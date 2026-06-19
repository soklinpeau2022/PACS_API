param(
    [int]$RowCount = 100000,
    [int]$HospitalCount = 2,
    [switch]$AllowSmallRowCount,
    [switch]$CleanupOnly,
    [switch]$SkipCleanup,
    [string]$BatchId = ("perf-" + (Get-Date -Format "yyyyMMddHHmmss")),
    [string]$ApiBaseUrl = "http://localhost:8080/pacsApi",
    [string]$DbContainer = $(if ($env:PACS_DB_CONTAINER) { $env:PACS_DB_CONTAINER } else { "udaya_pacs_api_postgres_1" }),
    [string]$DbUser = $(if ($env:PACS_DB_USER) { $env:PACS_DB_USER } else { "pacs_app_local_rw" }),
    [string]$DbName = $(if ($env:PACS_DB_NAME) { $env:PACS_DB_NAME } else { "emr_pacs_db" }),
    [string]$ClientId = "pacs-web",
    [string]$Username = "admin",
    [string]$Password = "1",
    [int]$ApiWarnMs = 1500
)

$ErrorActionPreference = "Stop"

if (-not $AllowSmallRowCount -and ($RowCount -lt 100000 -or $RowCount -gt 1000000)) {
    throw "RowCount must be between 100000 and 1000000. Use -AllowSmallRowCount only for local script debugging."
}

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ReportDir = Join-Path $ScriptRoot ".cache\large-data-performance"
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Psql {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [switch]$TuplesOnly
    )

    $args = @("exec", "-i", $DbContainer, "psql", "-U", $DbUser, "-d", $DbName, "-v", "ON_ERROR_STOP=1")
    if ($TuplesOnly) {
        $args += @("-t", "-A")
    }

    $Sql | docker @args
    if ($LASTEXITCODE -ne 0) {
        throw "psql command failed with exit code $LASTEXITCODE"
    }
}

function Invoke-PsqlCsv {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $trimmedSql = $Sql.Trim()
    while ($trimmedSql.EndsWith(";")) {
        $trimmedSql = $trimmedSql.Substring(0, $trimmedSql.Length - 1).TrimEnd()
    }
    $wrapped = "COPY ($trimmedSql) TO STDOUT WITH CSV HEADER;"
    Invoke-Psql -Sql $wrapped
}

function Cleanup-Batch {
    param([string]$TargetBatchId)

    $escapedBatch = $TargetBatchId.Replace("'", "''")
    Write-Step "Cleaning performance batch $TargetBatchId"
    Invoke-Psql -Sql @"
BEGIN;

CREATE TEMP TABLE perf_patients_to_delete AS
SELECT id
FROM patients
WHERE patient_uid LIKE 'PERF-$escapedBatch-P%';

CREATE TEMP TABLE perf_worklists_to_delete AS
SELECT w.id, w.study_id
FROM pacs_worklists w
JOIN perf_patients_to_delete p ON p.id = w.patient_id
WHERE w.visit_code LIKE 'PERF-$escapedBatch-Q%';

CREATE TEMP TABLE perf_studies_to_delete AS
SELECT id
FROM pacs_studies
WHERE accession_number LIKE 'PERF-$escapedBatch-ACC%';

CREATE TEMP TABLE perf_results_to_delete AS
SELECT r.id
FROM pacs_results r
JOIN perf_worklists_to_delete w ON w.id = r.worklist_id
UNION
SELECT r.id
FROM pacs_results r
JOIN perf_studies_to_delete s ON s.id = r.study_id;

DO `$`$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = CURRENT_USER
          AND rolsuper
    ) THEN
        RAISE EXCEPTION
            'Large-data fast cleanup requires a disposable local superuser role.';
    END IF;
END
`$`$;

-- Benchmark rows are fully identified above. Suppressing triggers avoids
-- hundreds of thousands of redundant FK checks across inherited partitions;
-- every dependent benchmark table and both caches are deleted explicitly.
SET LOCAL session_replication_role = replica;

DELETE FROM pacs_worklists_week_cache c
USING perf_worklists_to_delete w
WHERE c.id = w.id;

DELETE FROM pacs_studies_week_cache c
USING perf_studies_to_delete s
WHERE c.id = s.id;

DELETE FROM pacs_result_versions v
USING perf_results_to_delete r
WHERE v.result_id = r.id;

DELETE FROM pacs_result_images img
USING perf_results_to_delete r
WHERE img.result_id = r.id;

DELETE FROM pacs_results r
USING perf_results_to_delete d
WHERE r.id = d.id;

DELETE FROM pacs_viewer_states v
USING perf_studies_to_delete s
WHERE v.study_id = s.id;

DELETE FROM pacs_realtime_notification_events e
USING perf_studies_to_delete s
WHERE e.study_id = s.id;

DELETE FROM study_retention_delete_requests r
USING perf_studies_to_delete s
WHERE r.study_id = s.id;

DELETE FROM pacs_worklist_histories h
USING perf_worklists_to_delete w
WHERE h.worklist_id = w.id;

DELETE FROM pacs_worklist_study_links l
USING perf_worklists_to_delete w
WHERE l.worklist_id = w.id;

DELETE FROM pacs_worklists w
USING perf_worklists_to_delete d
WHERE w.id = d.id;

DELETE FROM pacs_studies s
USING perf_studies_to_delete d
WHERE s.id = d.id;

DELETE FROM patients p
USING perf_patients_to_delete d
WHERE p.id = d.id;

DELETE FROM system_activities
WHERE endpoint LIKE '/perf/$escapedBatch/%'
   OR description LIKE 'Performance batch $escapedBatch%';

DO `$`$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pacs_worklists_week_cache c
        JOIN perf_worklists_to_delete w ON w.id = c.id
    ) OR EXISTS (
        SELECT 1
        FROM pacs_studies_week_cache c
        JOIN perf_studies_to_delete s ON s.id = c.id
    ) OR EXISTS (
        SELECT 1
        FROM pacs_worklists w
        JOIN perf_worklists_to_delete d ON d.id = w.id
    ) OR EXISTS (
        SELECT 1
        FROM pacs_studies s
        JOIN perf_studies_to_delete d ON d.id = s.id
    ) OR EXISTS (
        SELECT 1
        FROM patients p
        JOIN perf_patients_to_delete d ON d.id = p.id
    ) THEN
        RAISE EXCEPTION 'Large-data cleanup left tagged source/cache rows behind.';
    END IF;
END
`$`$;

SET LOCAL session_replication_role = origin;

COMMIT;

VACUUM (ANALYZE) patients;
VACUUM (ANALYZE) pacs_studies;
VACUUM (ANALYZE) pacs_worklists;
VACUUM (ANALYZE) pacs_worklist_study_links;
VACUUM (ANALYZE) pacs_results;
VACUUM (ANALYZE) system_activities;
"@
}

function Seed-Batch {
    param(
        [string]$TargetBatchId,
        [int]$TargetRowCount,
        [int]$TargetHospitalCount
    )

    $escapedBatch = $TargetBatchId.Replace("'", "''")
    Write-Step "Seeding $TargetRowCount normalized worklist rows for batch $TargetBatchId"
    Invoke-Psql -Sql @"
BEGIN;

CREATE TEMP TABLE perf_context AS
WITH route_context AS (
    SELECT DISTINCT ON (h.id)
           h.id AS hospital_id,
           m.id AS modality_id,
           COALESCE(NULLIF(m.abbr, ''), m.name, 'OT') AS modality_code,
           r.id AS dicom_route_id
    FROM hospitals h
    JOIN hospital_modality_server_routes r
      ON r.hospital_id = h.id
     AND r.is_active = 1
    JOIN modalities m
      ON m.id = r.modality_id
     AND m.is_active = 1
    WHERE h.is_active = 1
    ORDER BY h.id, r.id
)
SELECT row_number() OVER (ORDER BY hospital_id) AS rn,
       hospital_id,
       modality_id,
       modality_code,
       dicom_route_id
FROM route_context
ORDER BY hospital_id
LIMIT $TargetHospitalCount;

DO `$`$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM perf_context) THEN
        RAISE EXCEPTION 'No active hospital/modality route exists for performance smoke.';
    END IF;
END
`$`$;

INSERT INTO patients (
    hospital_id,
    patient_uid,
    first_name,
    last_name,
    phone_number,
    gender,
    date_of_birth,
    is_active,
    created,
    modified
)
SELECT ctx.hospital_id,
       'PERF-$escapedBatch-P' || lpad(seq.n::text, 9, '0'),
       'Performance',
       'Patient ' || seq.n,
       '010' || lpad((seq.n % 10000000)::text, 7, '0'),
       CASE WHEN seq.n % 2 = 0 THEN 'M' ELSE 'F' END,
       DATE '1980-01-01' + ((seq.n % 14000)::int),
       1,
       now(),
       now()
FROM generate_series(1, $TargetRowCount) AS seq(n)
CROSS JOIN (SELECT count(*) AS total FROM perf_context) totals
JOIN perf_context ctx ON ctx.rn = ((seq.n - 1) % totals.total) + 1;

CREATE TEMP TABLE perf_numbered_patients AS
SELECT p.id,
       p.hospital_id,
       row_number() OVER (ORDER BY p.id) AS rn
FROM patients p
WHERE p.patient_uid LIKE 'PERF-$escapedBatch-P%';

INSERT INTO pacs_studies (
    hospital_id,
    patient_id,
    study_instance_uid,
    accession_number,
    modality,
    study_date,
    study_description,
    dicom_server_study_id,
    dicom_server_patient_id,
    dicom_server_series_id,
    status,
    is_active,
    created,
    modified
)
SELECT np.hospital_id,
       np.id,
       '1.2.826.0.1.3680043.10.777.$escapedBatch.' || np.rn,
       'PERF-$escapedBatch-ACC' || lpad(np.rn::text, 9, '0'),
       ctx.modality_code,
       CURRENT_DATE - ((np.rn % 365)::int),
       'Performance normalized study',
       'perf-study-$escapedBatch-' || np.rn,
       'perf-patient-$escapedBatch-' || np.rn,
       'perf-series-$escapedBatch-' || np.rn,
       CASE WHEN np.rn % 5 = 0 THEN 2 ELSE 1 END,
       1,
       now(),
       now()
FROM perf_numbered_patients np
JOIN perf_context ctx ON ctx.hospital_id = np.hospital_id;

CREATE TEMP TABLE perf_numbered_studies AS
SELECT s.id,
       s.patient_id,
       s.accession_number,
       row_number() OVER (ORDER BY s.id) AS rn
FROM pacs_studies s
WHERE s.accession_number LIKE 'PERF-$escapedBatch-ACC%';

INSERT INTO pacs_worklists (
    hospital_id,
    patient_id,
    visit_code,
    notes,
    status,
    modality_id,
    dicom_route_id,
    study_id,
    scheduled_date,
    scheduled_time,
    sent_at,
    received_at,
    image_received_at,
    created_by,
    modified_by,
    created,
    modified,
    created_at,
    modified_at
)
SELECT np.hospital_id,
       np.id,
       'PERF-$escapedBatch-Q' || lpad(np.rn::text, 9, '0'),
       'Performance workflow smoke history',
       CASE
           WHEN np.rn % 5 = 0 THEN 2
           WHEN np.rn % 4 = 0 THEN 3
           WHEN np.rn % 3 = 0 THEN 4
           ELSE 1
       END,
       ctx.modality_id,
       ctx.dicom_route_id,
       CASE WHEN np.rn % 5 = 0 THEN ns.id ELSE NULL END,
       CURRENT_DATE + ((np.rn % 14)::int),
       TIME '08:00' + ((np.rn % 480)::int * interval '1 minute'),
       CASE WHEN np.rn % 2 = 0 THEN now() ELSE NULL END,
       CASE WHEN np.rn % 5 = 0 THEN now() ELSE NULL END,
       CASE WHEN np.rn % 5 = 0 THEN now() ELSE NULL END,
       1,
       1,
       now(),
       now(),
       now(),
       now()
FROM perf_numbered_patients np
JOIN perf_context ctx ON ctx.hospital_id = np.hospital_id
LEFT JOIN perf_numbered_studies ns ON ns.rn = np.rn;

INSERT INTO pacs_worklist_study_links (
    hospital_id,
    worklist_id,
    study_id,
    is_primary,
    linked_at
)
SELECT w.hospital_id,
       w.id,
       w.study_id,
       1,
       now()
FROM pacs_worklists w
JOIN patients p ON p.id = w.patient_id
WHERE p.patient_uid LIKE 'PERF-$escapedBatch-P%'
  AND w.study_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO pacs_results (
    hospital_id,
    modality_id,
    worklist_id,
    study_id,
    patient_id,
    result_text,
    status,
    completed,
    is_active,
    created_by,
    created_at,
    modified_at
)
SELECT w.hospital_id,
       w.modality_id,
       w.id,
       w.study_id,
       w.patient_id,
       'Performance normalized result',
       CASE WHEN rn.rn % 20 = 0 THEN 'FINAL' ELSE 'IMAGE_RECEIVED' END,
       (rn.rn % 20 = 0),
       1,
       1,
       now(),
       now()
FROM (
    SELECT w.*, row_number() OVER (ORDER BY w.id) AS rn
    FROM pacs_worklists w
    JOIN patients p ON p.id = w.patient_id
    WHERE p.patient_uid LIKE 'PERF-$escapedBatch-P%'
      AND w.study_id IS NOT NULL
) rn
JOIN pacs_worklists w ON w.id = rn.id
WHERE rn.rn % 2 = 0;

INSERT INTO system_activities (
    endpoint,
    module,
    module_id,
    action,
    description,
    browser,
    operating_system,
    ip,
    host_name,
    duration,
    created_by,
    created,
    status
)
SELECT '/perf/$escapedBatch/' || seq.n,
       'Performance',
       seq.n,
       'PERFORMANCE_SMOKE',
       'Performance batch $escapedBatch activity ' || seq.n,
       'Smoke',
       'Smoke',
       '127.0.0.1',
       'local',
       (seq.n % 1000),
       1,
       now(),
       1
FROM generate_series(1, LEAST($TargetRowCount, 50000)) AS seq(n);

ANALYZE patients;
ANALYZE pacs_studies;
ANALYZE pacs_worklists;
ANALYZE pacs_worklist_study_links;
ANALYZE pacs_results;
ANALYZE system_activities;

COMMIT;
"@
}

function Get-Counts {
    param([string]$TargetBatchId)
    $escapedBatch = $TargetBatchId.Replace("'", "''")
    $csv = Invoke-PsqlCsv -Sql @"
SELECT 'patients' AS table_name, count(*) AS row_count FROM patients WHERE patient_uid LIKE 'PERF-$escapedBatch-P%'
UNION ALL
SELECT 'studies', count(*) FROM pacs_studies WHERE accession_number LIKE 'PERF-$escapedBatch-ACC%'
UNION ALL
SELECT 'worklists', count(*) FROM pacs_worklists WHERE visit_code LIKE 'PERF-$escapedBatch-Q%'
UNION ALL
SELECT 'study_links', count(*)
FROM pacs_worklist_study_links l
JOIN pacs_worklists w ON w.id = l.worklist_id
WHERE w.visit_code LIKE 'PERF-$escapedBatch-Q%'
UNION ALL
SELECT 'results', count(*)
FROM pacs_results r
JOIN pacs_worklists w ON w.id = r.worklist_id
WHERE w.visit_code LIKE 'PERF-$escapedBatch-Q%'
UNION ALL
SELECT 'activities', count(*) FROM system_activities WHERE endpoint LIKE '/perf/$escapedBatch/%';
"@
    return $csv | ConvertFrom-Csv
}

function Get-AuthHeader {
    $body = @{
        username = $Username
        password = $Password
        clientId = $ClientId
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/auth-login" -ContentType "application/json" -Body $body
    $token = $response.body.data[0].accessToken
    if (-not $token) {
        $token = $response.data.token
    }
    if (-not $token) {
        $token = $response.token
    }
    if (-not $token) {
        throw "Login response did not include a token."
    }
    return @{ Authorization = "Bearer $token" }
}

function Measure-ApiCall {
    param(
        [string]$Name,
        [string]$Method = "POST",
        [string]$Path,
        [hashtable]$Headers,
        [object]$Body = @{},
        [int]$WarnMs = $ApiWarnMs
    )

    $jsonBody = $Body | ConvertTo-Json -Depth 8
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $statusCode = $null
    $ok = $true
    $errorMessage = $null
    try {
        $response = Invoke-WebRequest -Method $Method -Uri "$ApiBaseUrl$Path" -Headers $Headers -ContentType "application/json" -Body $jsonBody -UseBasicParsing
        $statusCode = [int]$response.StatusCode
    } catch {
        $ok = $false
        $errorMessage = $_.Exception.Message
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
    } finally {
        $sw.Stop()
    }

    [pscustomobject]@{
        name = $Name
        path = $Path
        statusCode = $statusCode
        elapsedMs = [math]::Round($sw.Elapsed.TotalMilliseconds, 2)
        ok = $ok
        warning = ($sw.Elapsed.TotalMilliseconds -gt $WarnMs)
        error = $errorMessage
    }
}

function Get-SampleIds {
    param([string]$TargetBatchId)
    $escapedBatch = $TargetBatchId.Replace("'", "''")
    $csv = Invoke-PsqlCsv -Sql @"
SELECT
    (SELECT id FROM patients WHERE patient_uid LIKE 'PERF-$escapedBatch-P%' ORDER BY id LIMIT 1) AS patient_id,
    (SELECT id FROM pacs_worklists WHERE visit_code LIKE 'PERF-$escapedBatch-Q%' ORDER BY id LIMIT 1) AS worklist_id,
    (SELECT id FROM pacs_studies WHERE accession_number LIKE 'PERF-$escapedBatch-ACC%' ORDER BY id LIMIT 1) AS study_id,
    (SELECT public_id::text FROM patients WHERE patient_uid LIKE 'PERF-$escapedBatch-P%' ORDER BY id LIMIT 1) AS patient_key,
    (SELECT public_id::text FROM pacs_worklists WHERE visit_code LIKE 'PERF-$escapedBatch-Q%' ORDER BY id LIMIT 1) AS worklist_key,
    (SELECT public_id::text FROM pacs_studies WHERE accession_number LIKE 'PERF-$escapedBatch-ACC%' ORDER BY id LIMIT 1) AS study_key,
    (SELECT hospital_id FROM pacs_worklists WHERE visit_code LIKE 'PERF-$escapedBatch-Q%' ORDER BY id LIMIT 1) AS hospital_id,
    (
        SELECT h.public_id::text
        FROM pacs_worklists w
        INNER JOIN hospitals h ON h.id = w.hospital_id
        WHERE w.visit_code LIKE 'PERF-$escapedBatch-Q%'
        ORDER BY w.id
        LIMIT 1
    ) AS hospital_key,
    (SELECT min(visit_code) FROM pacs_worklists WHERE visit_code LIKE 'PERF-$escapedBatch-Q%') AS visit_code,
    (SELECT min(accession_number) FROM pacs_studies WHERE accession_number LIKE 'PERF-$escapedBatch-ACC%') AS accession_number,
    (SELECT min(patient_uid) FROM patients WHERE patient_uid LIKE 'PERF-$escapedBatch-P%') AS patient_uid,
    (
        SELECT w.hospital_id
        FROM pacs_worklists w
        WHERE w.visit_code LIKE 'PERF-$escapedBatch-Q%'
          AND w.dicom_route_id IS NOT NULL
          AND w.status = 1
        ORDER BY w.id DESC
        LIMIT 1
    ) AS explain_worklist_hospital_id,
    (
        SELECT w.dicom_route_id
        FROM pacs_worklists w
        WHERE w.visit_code LIKE 'PERF-$escapedBatch-Q%'
          AND w.dicom_route_id IS NOT NULL
          AND w.status = 1
        ORDER BY w.id DESC
        LIMIT 1
    ) AS explain_dicom_route_id,
    (
        SELECT w.visit_code
        FROM pacs_worklists w
        WHERE w.visit_code LIKE 'PERF-$escapedBatch-Q%'
          AND w.dicom_route_id IS NOT NULL
          AND w.status = 1
        ORDER BY w.id DESC
        LIMIT 1
    ) AS explain_visit_code,
    (
        SELECT p.hospital_id
        FROM patients p
        WHERE p.patient_uid LIKE 'PERF-$escapedBatch-P%'
        ORDER BY p.id DESC
        LIMIT 1
    ) AS explain_patient_hospital_id,
    (
        SELECT p.patient_uid
        FROM patients p
        WHERE p.patient_uid LIKE 'PERF-$escapedBatch-P%'
        ORDER BY p.id DESC
        LIMIT 1
    ) AS explain_patient_uid,
    (
        SELECT st.hospital_id
        FROM pacs_studies st
        WHERE st.accession_number LIKE 'PERF-$escapedBatch-ACC%'
        ORDER BY st.id DESC
        LIMIT 1
    ) AS explain_study_hospital_id,
    (
        SELECT st.accession_number
        FROM pacs_studies st
        WHERE st.accession_number LIKE 'PERF-$escapedBatch-ACC%'
        ORDER BY st.id DESC
        LIMIT 1
    ) AS explain_accession_number;
"@
    return ($csv | ConvertFrom-Csv | Select-Object -First 1)
}

function Measure-ApiSet {
    param([string]$TargetBatchId)

    Write-Step "Measuring API list/find/search calls"
    $headers = Get-AuthHeader
    $ids = Get-SampleIds -TargetBatchId $TargetBatchId

    $calls = @(
        @{
            Name = "worklist-list"
            Path = "/worklist/worklist-list"
            Body = @{ page = 1; rowsPerPage = 20; orderBy = "id"; orderDirection = "DESC" }
        },
        @{
            Name = "worklist-list-visit-search"
            Path = "/worklist/worklist-list"
            Body = @{ page = 1; rowsPerPage = 20; searchValue = $ids.visit_code; orderBy = "id"; orderDirection = "DESC" }
        },
        @{
            Name = "worklist-find"
            Path = "/worklist/worklist-find"
            Body = @{ publicKey = $ids.worklist_key; hospitalKey = $ids.hospital_key }
        },
        @{
            Name = "study-list"
            Path = "/study/study-list"
            Body = @{ page = 1; rowsPerPage = 20; orderBy = "id"; orderDirection = "DESC" }
        },
        @{
            Name = "study-list-accession-search"
            Path = "/study/study-list"
            Body = @{ page = 1; rowsPerPage = 20; searchValue = $ids.accession_number; orderBy = "id"; orderDirection = "DESC" }
        },
        @{
            Name = "patient-list"
            Path = "/patient/patient-list"
            Body = @{ page = 1; rowsPerPage = 20; orderBy = "id"; orderDirection = "DESC" }
        },
        @{
            Name = "patient-list-code-search"
            Path = "/patient/patient-list"
            Body = @{ page = 1; rowsPerPage = 20; searchValue = $ids.patient_uid; orderBy = "id"; orderDirection = "DESC" }
        },
        @{
            Name = "system-activity-list"
            Path = "/system-activity/system-activity-list"
            Body = @{ page = 1; rowsPerPage = 20; orderBy = "id"; orderDirection = "DESC" }
        }
    )

    $results = foreach ($call in $calls) {
        Measure-ApiCall -Name $call.Name -Path $call.Path -Headers $headers -Body $call.Body
    }

    return @{
        sample = $ids
        calls = $results
    }
}

function Measure-ExplainQuery {
    param(
        [string]$Name,
        [string]$Sql
    )

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $ok = $true
    $errorMessage = $null
    $plan = $null
    try {
        $plan = Invoke-Psql -Sql ("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + $Sql) -TuplesOnly
    } catch {
        $ok = $false
        $errorMessage = $_.Exception.Message
    } finally {
        $sw.Stop()
    }

    [pscustomobject]@{
        name = $Name
        elapsedMs = [math]::Round($sw.Elapsed.TotalMilliseconds, 2)
        ok = $ok
        error = $errorMessage
        plan = if ($plan) { ($plan -join "`n") } else { $null }
    }
}

function Measure-ExplainSet {
    param([string]$TargetBatchId)

    Write-Step "Measuring database query plans"
    $ids = Get-SampleIds -TargetBatchId $TargetBatchId
    $visitCode = ([string]$ids.explain_visit_code).Replace("'", "''")
    $patientUid = ([string]$ids.explain_patient_uid).Replace("'", "''")
    $accessionNumber = ([string]$ids.explain_accession_number).Replace("'", "''")
    $worklistHospitalId = [long]$ids.explain_worklist_hospital_id
    $patientHospitalId = [long]$ids.explain_patient_hospital_id
    $studyHospitalId = [long]$ids.explain_study_hospital_id
    $dicomRouteId = [long]$ids.explain_dicom_route_id

    return @(
        Measure-ExplainQuery -Name "worklist_route_status" -Sql @"
SELECT w.id, w.visit_code, w.patient_id
FROM pacs_worklists w
WHERE w.hospital_id = $worklistHospitalId
  AND w.dicom_route_id = $dicomRouteId
  AND w.status = 1
ORDER BY w.id DESC
LIMIT 50
"@
        Measure-ExplainQuery -Name "worklist_visit_code" -Sql @"
SELECT w.id
FROM pacs_worklists w
WHERE w.hospital_id = $worklistHospitalId
  AND w.visit_code IS NOT NULL
  AND BTRIM(w.visit_code) <> ''
  AND LOWER(w.visit_code) = LOWER('$visitCode')
LIMIT 1
"@
        Measure-ExplainQuery -Name "patient_uid" -Sql @"
SELECT p.id
FROM patients p
WHERE p.hospital_id = $patientHospitalId
  AND LOWER(p.patient_uid) = LOWER('$patientUid')
LIMIT 1
"@
        Measure-ExplainQuery -Name "study_accession" -Sql @"
SELECT st.id
FROM pacs_studies st
WHERE st.hospital_id = $studyHospitalId
  AND st.accession_number = '$accessionNumber'
LIMIT 1
"@
    )
}

$startedAt = Get-Date
$reportPath = Join-Path $ReportDir ("performance-$BatchId.json")

if ($CleanupOnly) {
    Cleanup-Batch -TargetBatchId $BatchId
    $counts = Get-Counts -TargetBatchId $BatchId
    $counts | Format-Table -AutoSize
    return
}

$finalCleanupDone = $false
try {
    Cleanup-Batch -TargetBatchId $BatchId
    Seed-Batch -TargetBatchId $BatchId -TargetRowCount $RowCount -TargetHospitalCount $HospitalCount
    $countsAfterSeed = Get-Counts -TargetBatchId $BatchId
    $apiMeasurements = Measure-ApiSet -TargetBatchId $BatchId
    $explainMeasurements = Measure-ExplainSet -TargetBatchId $BatchId

    if (-not $SkipCleanup) {
        Cleanup-Batch -TargetBatchId $BatchId
        $finalCleanupDone = $true
    }

    $countsAfterCleanup = Get-Counts -TargetBatchId $BatchId
    $endedAt = Get-Date
    $report = [ordered]@{
        batchId = $BatchId
        rowCount = $RowCount
        hospitalCount = $HospitalCount
        startedAt = $startedAt.ToString("o")
        endedAt = $endedAt.ToString("o")
        durationSeconds = [math]::Round(($endedAt - $startedAt).TotalSeconds, 2)
        cleanupDone = $finalCleanupDone
        countsAfterSeed = $countsAfterSeed
        countsAfterCleanup = $countsAfterCleanup
        api = $apiMeasurements
        explain = $explainMeasurements
    }

    $report | ConvertTo-Json -Depth 20 | Set-Content -Path $reportPath -Encoding UTF8
    Write-Step "Report written to $reportPath"

    $failedCalls = @($apiMeasurements.calls | Where-Object { -not $_.ok })
    $slowCalls = @($apiMeasurements.calls | Where-Object { $_.warning })
    if ($slowCalls.Count -gt 0) {
        Write-Host "API performance warnings:" -ForegroundColor Yellow
        $slowCalls | Format-Table name, statusCode, elapsedMs, warning, ok -AutoSize
    }

    $apiMeasurements.calls | Format-Table name, statusCode, elapsedMs, warning, ok -AutoSize
    $countsAfterCleanup | Format-Table -AutoSize
    if ($failedCalls.Count -gt 0) {
        throw "$($failedCalls.Count) API performance smoke call(s) failed."
    }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    if (-not $SkipCleanup) {
        try {
            Cleanup-Batch -TargetBatchId $BatchId
        } catch {
            Write-Host "Cleanup failed after error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    throw
}
