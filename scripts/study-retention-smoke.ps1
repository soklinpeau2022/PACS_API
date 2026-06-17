Param(
    [ValidateSet("test", "seed", "cleanup")]
    [string]$Action = "test",
    [int]$Count = 30,
    [int]$SeedChunkSize = 1000,
    [int]$DeleteChunkSize = 25,
    [string]$ApiBaseUrl = "http://127.0.0.1:8080/pacsApi",
    [string]$Username = $(if ($env:PACS_USERNAME) { $env:PACS_USERNAME } else { "admin" }),
    [string]$Password = $(if ($env:PACS_PASSWORD) { $env:PACS_PASSWORD } else { "1" }),
    [string]$SourceHospitalCode = "H001",
    [string]$SmokeHospitalCode = "SMKRET",
    [switch]$AllowNonLocalTarget
)

$ErrorActionPreference = "Stop"

if ($Count -lt 1) {
    throw "Count must be at least 1."
}
if ($SeedChunkSize -lt 1) {
    throw "SeedChunkSize must be at least 1."
}
if ($DeleteChunkSize -lt 1 -or $DeleteChunkSize -gt 100) {
    throw "DeleteChunkSize must be between 1 and 100."
}
try {
    $apiUri = [Uri]$ApiBaseUrl
} catch {
    throw "ApiBaseUrl must be a valid URL."
}
$apiHost = $apiUri.Host.ToLowerInvariant()
$localHosts = @("localhost", "127.0.0.1", "::1", "0.0.0.0")
if (-not $AllowNonLocalTarget -and -not ($localHosts -contains $apiHost)) {
    throw "Study retention smoke test is local-only by default. Use -AllowNonLocalTarget only for an isolated test environment."
}

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envDbPath = Join-Path $projectRoot ".env.db"

function Get-EnvFileValue {
    param([string]$FilePath, [string]$Key)
    if (-not (Test-Path $FilePath)) { return "" }
    $line = Select-String -Path $FilePath -Pattern "^\s*$Key\s*=\s*(.*)\s*$" | Select-Object -First 1
    if (-not $line) { return "" }
    return $line.Matches[0].Groups[1].Value.Trim()
}

function ConvertTo-SqlLiteral {
    param([string]$Value)
    if ($null -eq $Value) { return "NULL" }
    return "'" + $Value.Replace("'", "''") + "'"
}

$dbContainer = Get-EnvFileValue -FilePath $envDbPath -Key "PACS_DB_CONTAINER_NAME"
if ([string]::IsNullOrWhiteSpace($dbContainer)) { $dbContainer = "pacs-db" }
$dbName = Get-EnvFileValue -FilePath $envDbPath -Key "PACS_DB_NAME"
if ([string]::IsNullOrWhiteSpace($dbName)) { $dbName = "emr_pacs_db" }
$dbUser = Get-EnvFileValue -FilePath $envDbPath -Key "PACS_DB_USER"
if ([string]::IsNullOrWhiteSpace($dbUser)) { $dbUser = "pacs_app_local_rw" }
$dbPassword = Get-EnvFileValue -FilePath $envDbPath -Key "PACS_DB_PASSWORD"

function Invoke-Psql {
    param([string]$Sql)
    $output = $Sql | docker exec -i -e "PGPASSWORD=$dbPassword" $dbContainer psql -h 127.0.0.1 -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -t -A 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw (($output | Out-String).Trim())
    }
    return (($output | Out-String).Trim())
}

function Invoke-ApiPost {
    param([string]$Path, [hashtable]$Body, [string]$Token)
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers["Authorization"] = "Bearer $Token"
    }
    $json = ($Body | ConvertTo-Json -Depth 20 -Compress)
    return Invoke-RestMethod -Method Post -Uri ($ApiBaseUrl.TrimEnd("/") + $Path) -ContentType "application/json" -Headers $headers -Body $json -TimeoutSec 120
}

function Assert-ApiSuccess {
    param($Response, [string]$Operation)
    if (-not $Response -or -not $Response.header -or $Response.header.result -ne $true) {
        $message = ""
        if ($Response -and $Response.header) { $message = [string]$Response.header.errorText }
        if ([string]::IsNullOrWhiteSpace($message) -and $Response -and $Response.body) { $message = [string]$Response.body.message }
        if ([string]::IsNullOrWhiteSpace($message)) { $message = "Unknown API failure." }
        throw "$Operation failed: $message"
    }
}

function Get-ResponseRows {
    param($Response)
    if (-not $Response -or -not $Response.body -or $null -eq $Response.body.data) {
        return @()
    }
    return @($Response.body.data)
}

function Get-ResponseTotal {
    param($Response)
    if ($Response -and $Response.body -and $Response.body.pagination -and $null -ne $Response.body.pagination.total) {
        return [int]$Response.body.pagination.total
    }
    return (Get-ResponseRows $Response).Count
}

function Get-IntProperty {
    param($Object, [string]$Name)
    if (-not $Object) { return 0 }
    $property = $Object.PSObject.Properties[$Name]
    if (-not $property -or $null -eq $property.Value) { return 0 }
    return [int]$property.Value
}

function Convert-PsqlJson {
    param([string]$Text)
    $jsonLine = @($Text -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_.StartsWith("{") -or $_.StartsWith("[") }) | Select-Object -Last 1
    if ([string]::IsNullOrWhiteSpace($jsonLine)) {
        throw "Expected JSON from psql, got: $Text"
    }
    return ($jsonLine | ConvertFrom-Json)
}

function Get-AccessToken {
    $login = Invoke-ApiPost -Path "/auth/auth-login" -Body @{
        clientId = "pacs-web"
        username = $Username
        password = $Password
    } -Token $null
    Assert-ApiSuccess $login "Login"
    $rows = @(Get-ResponseRows $login)
    $token = if ($rows.Count -gt 0) { [string]$rows[0].accessToken } else { "" }
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login did not return an access token."
    }
    return $token
}

function Invoke-SmokeCleanup {
    $smokeCode = ConvertTo-SqlLiteral $SmokeHospitalCode
    $sql = @"
DO `$`$
DECLARE
    v_hospital_id BIGINT;
BEGIN
    SELECT id INTO v_hospital_id
    FROM hospitals
    WHERE code = $smokeCode
    LIMIT 1;

    DELETE FROM study_retention_delete_requests
    WHERE accession_number LIKE 'RET-SMOKE-%'
       OR study_instance_uid LIKE '1.2.826.0.1.3680043.10.7777.%'
       OR patient_mrn LIKE 'RET-SMOKE-%'
       OR hospital_id = v_hospital_id;

    DELETE FROM pacs_result_images img
    USING pacs_results pr
    WHERE img.result_id = pr.id
      AND (
          pr.result_text LIKE 'Retention smoke %'
          OR pr.study_id IN (
              SELECT id FROM pacs_studies
              WHERE accession_number LIKE 'RET-SMOKE-%'
                 OR study_instance_uid LIKE '1.2.826.0.1.3680043.10.7777.%'
                 OR hospital_id = v_hospital_id
          )
          OR pr.worklist_id IN (
              SELECT id FROM pacs_worklists
              WHERE visit_code LIKE 'VISIT-RET-SMOKE-%'
                 OR hospital_id = v_hospital_id
          )
      );

    DELETE FROM pacs_viewer_states
    WHERE accession_number LIKE 'RET-SMOKE-%'
       OR study_instance_uid LIKE '1.2.826.0.1.3680043.10.7777.%'
       OR metadata ->> 'retentionSmoke' = 'true'
       OR study_id IN (
           SELECT id FROM pacs_studies
           WHERE accession_number LIKE 'RET-SMOKE-%'
              OR study_instance_uid LIKE '1.2.826.0.1.3680043.10.7777.%'
              OR hospital_id = v_hospital_id
       )
       OR worklist_id IN (
           SELECT id FROM pacs_worklists
           WHERE visit_code LIKE 'VISIT-RET-SMOKE-%'
              OR hospital_id = v_hospital_id
       );

    DELETE FROM pacs_results
    WHERE result_text LIKE 'Retention smoke %'
       OR study_id IN (
           SELECT id FROM pacs_studies
           WHERE accession_number LIKE 'RET-SMOKE-%'
              OR study_instance_uid LIKE '1.2.826.0.1.3680043.10.7777.%'
              OR hospital_id = v_hospital_id
       )
       OR worklist_id IN (
           SELECT id FROM pacs_worklists
           WHERE visit_code LIKE 'VISIT-RET-SMOKE-%'
              OR hospital_id = v_hospital_id
       )
       OR patient_id IN (
           SELECT id FROM patients
           WHERE patient_uid LIKE 'RET-SMOKE-%'
              OR patient_hn LIKE 'RET-SMOKE-%'
              OR hospital_id = v_hospital_id
       );

    DELETE FROM pacs_worklist_histories
    WHERE worklist_id IN (
        SELECT id FROM pacs_worklists
        WHERE visit_code LIKE 'VISIT-RET-SMOKE-%'
           OR hospital_id = v_hospital_id
    )
       OR patient_id IN (
           SELECT id FROM patients
           WHERE patient_uid LIKE 'RET-SMOKE-%'
              OR patient_hn LIKE 'RET-SMOKE-%'
              OR hospital_id = v_hospital_id
       );

    DELETE FROM pacs_worklist_study_links
    WHERE study_id IN (
        SELECT id FROM pacs_studies
        WHERE accession_number LIKE 'RET-SMOKE-%'
           OR study_instance_uid LIKE '1.2.826.0.1.3680043.10.7777.%'
           OR hospital_id = v_hospital_id
    )
       OR worklist_id IN (
           SELECT id FROM pacs_worklists
           WHERE visit_code LIKE 'VISIT-RET-SMOKE-%'
              OR hospital_id = v_hospital_id
       );

    DELETE FROM pacs_worklists
    WHERE visit_code LIKE 'VISIT-RET-SMOKE-%'
       OR notes LIKE 'RETENTION_SMOKE%'
       OR hospital_id = v_hospital_id;

    DELETE FROM pacs_studies
    WHERE accession_number LIKE 'RET-SMOKE-%'
       OR study_instance_uid LIKE '1.2.826.0.1.3680043.10.7777.%'
       OR hospital_id = v_hospital_id;

    DELETE FROM patients
    WHERE patient_uid LIKE 'RET-SMOKE-%'
       OR patient_hn LIKE 'RET-SMOKE-%'
       OR hospital_id = v_hospital_id;

    DELETE FROM study_retention_policies
    WHERE notes LIKE 'RETENTION_SMOKE%'
       OR hospital_id = v_hospital_id;

    DELETE FROM hospital_modality_server_routes
    WHERE hospital_id = v_hospital_id;

    DELETE FROM hospital_dicom_servers
    WHERE hospital_id = v_hospital_id;

    DELETE FROM hospital_modalities hm
    USING modalities m
    WHERE hm.modality_id = m.id
      AND (m.abbr IN ('SMKEXP', 'SMKAUTO', 'SMKNEAR', 'SMKOPEN')
           OR m.name LIKE 'Retention Smoke %'
           OR hm.hospital_id = v_hospital_id);

    DELETE FROM modalities
    WHERE abbr IN ('SMKEXP', 'SMKAUTO', 'SMKNEAR', 'SMKOPEN')
       OR name LIKE 'Retention Smoke %';

    DELETE FROM hospitals
    WHERE id = v_hospital_id;
END
`$`$;
"@
    Invoke-Psql $sql | Out-Null
}

function New-SmokeContext {
    $sourceCode = ConvertTo-SqlLiteral $SourceHospitalCode
    $smokeCode = ConvertTo-SqlLiteral $SmokeHospitalCode
    $sql = @"
DO `$`$
DECLARE
    v_source_server_id BIGINT;
BEGIN
    SELECT ds.id INTO v_source_server_id
    FROM hospitals h
    INNER JOIN hospital_dicom_servers ds ON ds.hospital_id = h.id AND ds.is_active = 1
    WHERE h.code = $sourceCode
      AND h.is_active = 1
    ORDER BY ds.id
    LIMIT 1;

    IF v_source_server_id IS NULL THEN
        RAISE EXCEPTION 'No active DICOM server found for source hospital %. Start local stack first.', $sourceCode;
    END IF;
END
`$`$;

WITH smoke_hospital AS (
    INSERT INTO hospitals (code, name, timezone, is_active, created_by, created, created_at)
    VALUES ($smokeCode, 'Retention Smoke Hospital', 'Asia/Phnom_Penh', 1, 1, NOW(), NOW())
    RETURNING id, public_id::text AS public_key
),
source_server AS (
    SELECT ds.*
    FROM hospitals h
    INNER JOIN hospital_dicom_servers ds ON ds.hospital_id = h.id AND ds.is_active = 1
    WHERE h.code = $sourceCode
      AND h.is_active = 1
    ORDER BY ds.id
    LIMIT 1
),
smoke_server AS (
    INSERT INTO hospital_dicom_servers (
        hospital_id, name, ip_address, port, ae_title, username, password, is_active,
        created_by, modified_by, created_at, modified_at, viewer_base_url, dicom_port,
        ssl_enabled, authentication_enabled, authorization_enabled, dicomweb_path
    )
    SELECT
        h.id, 'RETENTION_SMOKE_DICOM', s.ip_address, s.port, 'SMKRET', s.username, s.password, 1,
        1, 1, NOW(), NOW(), s.viewer_base_url, s.dicom_port,
        s.ssl_enabled, s.authentication_enabled, s.authorization_enabled, s.dicomweb_path
    FROM smoke_hospital h
    CROSS JOIN source_server s
    RETURNING id, public_id::text AS public_key
),
smoke_modalities AS (
    INSERT INTO modalities (name, abbr, is_active, created_by, modified_by, created_at, modified_at)
    VALUES
        ('Retention Smoke Expired', 'SMKEXP', 1, 1, 1, NOW(), NOW()),
        ('Retention Smoke Auto Delete', 'SMKAUTO', 1, 1, 1, NOW(), NOW()),
        ('Retention Smoke Near Expiry', 'SMKNEAR', 1, 1, 1, NOW(), NOW()),
        ('Retention Smoke Open', 'SMKOPEN', 1, 1, 1, NOW(), NOW())
    RETURNING id, abbr, public_id::text AS public_key
),
hospital_modality_rows AS (
    INSERT INTO hospital_modalities (hospital_id, modality_id, is_active, created_by, modified_by, created_at, modified_at)
    SELECT h.id, m.id, 1, 1, 1, NOW(), NOW()
    FROM smoke_hospital h
    CROSS JOIN smoke_modalities m
    RETURNING id
),
policy_rows AS (
    INSERT INTO study_retention_policies (
        hospital_id, dicom_server_id, modality_id, retention_days, retention_value, retention_unit,
        notify_before_days, require_approval, enabled, auto_delete, notes, is_active,
        created_by, modified_by, created_at, modified_at
    )
    SELECT h.id, s.id, m.id,
           CASE WHEN m.abbr IN ('SMKEXP', 'SMKAUTO') THEN 1 ELSE 30 END,
           CASE WHEN m.abbr IN ('SMKEXP', 'SMKAUTO') THEN 1 ELSE 30 END,
           'DAY',
           CASE WHEN m.abbr IN ('SMKEXP', 'SMKAUTO') THEN 0 ELSE 14 END,
           CASE WHEN m.abbr = 'SMKAUTO' THEN FALSE ELSE TRUE END,
           TRUE,
           CASE WHEN m.abbr = 'SMKAUTO' THEN TRUE ELSE FALSE END,
           'RETENTION_SMOKE ' || m.abbr,
           1,
           1, 1, NOW(), NOW()
    FROM smoke_hospital h
    CROSS JOIN smoke_server s
    INNER JOIN smoke_modalities m ON TRUE
    RETURNING id
)
SELECT jsonb_build_object(
    'hospitalId', (SELECT id FROM smoke_hospital),
    'hospitalKey', (SELECT public_key FROM smoke_hospital),
    'dicomServerId', (SELECT id FROM smoke_server),
    'dicomServerKey', (SELECT public_key FROM smoke_server)
)::text;
"@
    return (Convert-PsqlJson (Invoke-Psql $sql))
}

function Add-SmokeStudies {
    param(
        [string]$Category,
        [string]$Abbr,
        [int]$UidBucket,
        [int]$Start,
        [int]$End
    )
    if ($End -lt $Start) { return }
    $categoryLiteral = ConvertTo-SqlLiteral $Category
    $abbrLiteral = ConvertTo-SqlLiteral $Abbr
    $sql = @"
DO `$`$
DECLARE
    v_hospital_id BIGINT;
    v_server_id BIGINT;
    v_modality_id BIGINT;
    v_base_at TIMESTAMPTZ;
    v_related_count BIGINT;
BEGIN
    SELECT h.id INTO v_hospital_id
    FROM hospitals h
    WHERE h.code = $(ConvertTo-SqlLiteral $SmokeHospitalCode)
    LIMIT 1;

    SELECT id INTO v_server_id
    FROM hospital_dicom_servers
    WHERE hospital_id = v_hospital_id
      AND is_active = 1
    ORDER BY id
    LIMIT 1;

    SELECT id INTO v_modality_id
    FROM modalities
    WHERE abbr = $abbrLiteral
      AND is_active = 1
    LIMIT 1;

    IF v_hospital_id IS NULL OR v_server_id IS NULL OR v_modality_id IS NULL THEN
        RAISE EXCEPTION 'Smoke context is incomplete for category %.', $categoryLiteral;
    END IF;

    v_base_at := CASE $categoryLiteral
        WHEN 'MAN' THEN NOW() - INTERVAL '2 days'
        WHEN 'AUTO' THEN NOW() - INTERVAL '2 days'
        WHEN 'NEAR' THEN NOW() - INTERVAL '20 days'
        ELSE NOW()
    END;

    WITH rows AS (
        SELECT generate_series($Start, $End) AS n
    ),
    patient_source AS (
        SELECT
            n,
            FORMAT('RET-SMOKE-%s-P%06s', $categoryLiteral, n) AS patient_uid,
            FORMAT('Smoke %s', $categoryLiteral) AS first_name,
            FORMAT('Patient %06s', n) AS last_name
        FROM rows
    ),
    inserted_patients AS (
        INSERT INTO patients (
            hospital_id, patient_uid, patient_hn, first_name, last_name, gender,
            date_of_birth, is_active, created, modified
        )
        SELECT
            v_hospital_id, patient_uid, patient_uid, first_name, last_name, 'U',
            DATE '1970-01-01' + (n % 12000), 1, NOW(), NOW()
        FROM patient_source
        ON CONFLICT (hospital_id, patient_uid)
        DO UPDATE SET
            patient_hn = EXCLUDED.patient_hn,
            first_name = EXCLUDED.first_name,
            last_name = EXCLUDED.last_name,
            is_active = 1,
            modified = NOW()
        RETURNING id, patient_uid
    )
    INSERT INTO pacs_studies (
        hospital_id, patient_id, study_instance_uid, accession_number, reference_visit_code,
        modality, modality_id, study_date, study_description, status, is_active,
        created, modified, dicom_server_study_id, dicom_server_patient_id,
        dicom_server_series_id, received_at, image_received_at, dicom_server_id,
        source_type, uploaded_by, instance_count, institution_name
    )
    SELECT
        v_hospital_id,
        p.id,
        FORMAT('1.2.826.0.1.3680043.10.7777.%s.%s', $UidBucket, s.n),
        FORMAT('RET-SMOKE-%s-%06s', $categoryLiteral, s.n),
        FORMAT('VISIT-RET-SMOKE-%s-%06s', $categoryLiteral, s.n),
        $abbrLiteral,
        v_modality_id,
        CAST(v_base_at AS DATE),
        FORMAT('Retention smoke %s study %06s', $categoryLiteral, s.n),
        1,
        1,
        NOW(),
        NOW(),
        FORMAT('ret-smoke-%s-%06s', LOWER($categoryLiteral), s.n),
        FORMAT('ret-smoke-patient-%s-%06s', LOWER($categoryLiteral), s.n),
        FORMAT('ret-smoke-series-%s-%06s', LOWER($categoryLiteral), s.n),
        v_base_at,
        v_base_at,
        v_server_id,
        'UPLOAD',
        1,
        1,
        'Retention Smoke Hospital'
    FROM patient_source s
    INNER JOIN inserted_patients p ON p.patient_uid = s.patient_uid
    ON CONFLICT (hospital_id, study_instance_uid)
    DO UPDATE SET
        accession_number = EXCLUDED.accession_number,
        reference_visit_code = EXCLUDED.reference_visit_code,
        modality = EXCLUDED.modality,
        modality_id = EXCLUDED.modality_id,
        study_date = EXCLUDED.study_date,
        study_description = EXCLUDED.study_description,
        status = EXCLUDED.status,
        is_active = 1,
        modified = NOW(),
        dicom_server_study_id = EXCLUDED.dicom_server_study_id,
        dicom_server_patient_id = EXCLUDED.dicom_server_patient_id,
        dicom_server_series_id = EXCLUDED.dicom_server_series_id,
        received_at = EXCLUDED.received_at,
        image_received_at = EXCLUDED.image_received_at,
        dicom_server_id = EXCLUDED.dicom_server_id,
        source_type = EXCLUDED.source_type,
        uploaded_by = EXCLUDED.uploaded_by,
        instance_count = EXCLUDED.instance_count,
        institution_name = EXCLUDED.institution_name;

    WITH rows AS (
        SELECT generate_series($Start, $End) AS n
    ),
    study_rows AS (
        SELECT
            s.id AS study_id,
            s.hospital_id,
            s.patient_id,
            s.modality_id,
            s.study_instance_uid,
            s.accession_number,
            s.reference_visit_code,
            s.dicom_server_study_id,
            s.study_description,
            s.study_date,
            s.received_at,
            s.image_received_at,
            p.patient_hn,
            NULLIF(TRIM(CONCAT(COALESCE(p.first_name, ''), ' ', COALESCE(p.last_name, ''))), '') AS patient_name
        FROM rows r
        INNER JOIN pacs_studies s
            ON s.hospital_id = v_hospital_id
           AND s.study_instance_uid = FORMAT('1.2.826.0.1.3680043.10.7777.%s.%s', $UidBucket, r.n)
        INNER JOIN patients p ON p.id = s.patient_id
    ),
    inserted_worklists AS (
        INSERT INTO pacs_worklists (
            hospital_id,
            patient_id,
            status,
            notes,
            created,
            modified,
            modality_id,
            visit_code,
            created_by,
            modified_by,
            created_at,
            modified_at,
            dicom_server_worklist_id,
            dicom_server_worklist_path,
            sent_at,
            received_at,
            study_description,
            scheduled_date,
            scheduled_time,
            image_received_at,
            study_id
        )
        SELECT
            sr.hospital_id,
            sr.patient_id,
            4,
            FORMAT('RETENTION_SMOKE %s worklist', $categoryLiteral),
            NOW(),
            NOW(),
            sr.modality_id,
            sr.reference_visit_code,
            1,
            1,
            NOW(),
            NOW(),
            FORMAT('wl-ret-smoke-%s-%s', LOWER($categoryLiteral), sr.study_id),
            FORMAT('/retention-smoke/%s/%s', LOWER($categoryLiteral), sr.accession_number),
            sr.received_at,
            sr.received_at,
            sr.study_description,
            sr.study_date,
            CAST(sr.received_at AS TIME),
            sr.image_received_at,
            sr.study_id
        FROM study_rows sr
        ON CONFLICT (hospital_id, visit_code) WHERE visit_code IS NOT NULL DO UPDATE
        SET
            hospital_id = EXCLUDED.hospital_id,
            patient_id = EXCLUDED.patient_id,
            status = EXCLUDED.status,
            notes = EXCLUDED.notes,
            modality_id = EXCLUDED.modality_id,
            modified = NOW(),
            modified_by = EXCLUDED.modified_by,
            modified_at = NOW(),
            dicom_server_worklist_id = EXCLUDED.dicom_server_worklist_id,
            dicom_server_worklist_path = EXCLUDED.dicom_server_worklist_path,
            sent_at = EXCLUDED.sent_at,
            received_at = EXCLUDED.received_at,
            study_description = EXCLUDED.study_description,
            scheduled_date = EXCLUDED.scheduled_date,
            scheduled_time = EXCLUDED.scheduled_time,
            image_received_at = EXCLUDED.image_received_at,
            study_id = EXCLUDED.study_id
        RETURNING id AS worklist_id, hospital_id, patient_id, modality_id, study_id
    ),
    inserted_links AS (
        INSERT INTO pacs_worklist_study_links (
            hospital_id,
            worklist_id,
            study_id,
            is_primary,
            linked_at,
            created_by
        )
        SELECT hospital_id, worklist_id, study_id, 1, NOW(), 1
        FROM inserted_worklists
        ON CONFLICT (hospital_id, worklist_id, study_id) DO UPDATE
        SET
            is_primary = 1,
            linked_at = EXCLUDED.linked_at
        RETURNING 1
    ),
    inserted_histories AS (
        INSERT INTO pacs_worklist_histories (
            hospital_id,
            worklist_id,
            patient_id,
            from_status,
            to_status,
            action,
            reason,
            created,
            created_by
        )
        SELECT
            hospital_id,
            worklist_id,
            patient_id,
            2,
            4,
            'RETENTION_SMOKE_IMAGE_RECEIVED',
            FORMAT('Retention smoke %s seeded linked worklist/study cleanup row', $categoryLiteral),
            NOW(),
            1
        FROM inserted_worklists
        RETURNING 1
    ),
    inserted_results AS (
        INSERT INTO pacs_results (
            hospital_id,
            modality_id,
            study_id,
            worklist_id,
            patient_id,
            result_date,
            result_text,
            status,
            completed,
            is_active,
            created_by,
            created_at,
            modified_at
        )
        SELECT
            sr.hospital_id,
            sr.modality_id,
            sr.study_id,
            iw.worklist_id,
            sr.patient_id,
            sr.study_date,
            FORMAT('Retention smoke %s result for %s', $categoryLiteral, sr.accession_number),
            'IMAGE_RECEIVED',
            FALSE,
            1,
            1,
            NOW(),
            NOW()
        FROM study_rows sr
        INNER JOIN inserted_worklists iw ON iw.study_id = sr.study_id
        ON CONFLICT (hospital_id, modality_id, study_id) WHERE is_active = 1 AND study_id IS NOT NULL DO UPDATE
        SET
            worklist_id = EXCLUDED.worklist_id,
            patient_id = EXCLUDED.patient_id,
            result_text = EXCLUDED.result_text,
            status = EXCLUDED.status,
            completed = EXCLUDED.completed,
            modified_at = NOW()
        RETURNING id, hospital_id, modality_id, study_id, worklist_id, patient_id
    ),
    inserted_result_images AS (
        INSERT INTO pacs_result_images (
            result_id,
            image_path,
            original_file_name,
            file_type,
            file_size,
            sort_order,
            is_active,
            created_at
        )
        SELECT
            ir.id,
            FORMAT('/retention-smoke/%s/%s.dcm', LOWER($categoryLiteral), sr.accession_number),
            FORMAT('%s.dcm', sr.accession_number),
            'application/dicom',
            1024 + (sr.study_id % 2048),
            0,
            1,
            NOW()
        FROM inserted_results ir
        INNER JOIN study_rows sr ON sr.study_id = ir.study_id
        WHERE NOT EXISTS (
            SELECT 1
            FROM pacs_result_images existing
            WHERE existing.result_id = ir.id
              AND existing.image_path = FORMAT('/retention-smoke/%s/%s.dcm', LOWER($categoryLiteral), sr.accession_number)
        )
        RETURNING 1
    ),
    inserted_viewer_states AS (
        INSERT INTO pacs_viewer_states (
            hospital_id,
            modality_id,
            study_id,
            worklist_id,
            patient_id,
            study_instance_uid,
            accession_number,
            patient_code,
            state_type,
            schema_version,
            viewer_state,
            measurements,
            annotations,
            segmentations,
            additional_findings,
            metadata,
            version,
            created_by,
            modified_by,
            is_active,
            created_at,
            modified_at
        )
        SELECT
            sr.hospital_id,
            sr.modality_id,
            sr.study_id,
            iw.worklist_id,
            sr.patient_id,
            sr.study_instance_uid,
            sr.accession_number,
            sr.patient_hn,
            'OHIF_VIEWER_STATE',
            1,
            jsonb_build_object('retentionSmoke', true, 'category', $categoryLiteral, 'study', sr.accession_number),
            '[]'::jsonb,
            '[]'::jsonb,
            '[]'::jsonb,
            '[]'::jsonb,
            jsonb_build_object('retentionSmoke', 'true', 'retentionSmokeCategory', $categoryLiteral),
            1,
            1,
            1,
            1,
            NOW(),
            NOW()
        FROM study_rows sr
        INNER JOIN inserted_worklists iw ON iw.study_id = sr.study_id
        ON CONFLICT (hospital_id, worklist_id, state_type) WHERE is_active = 1 AND worklist_id IS NOT NULL DO UPDATE
        SET
            study_id = EXCLUDED.study_id,
            patient_id = EXCLUDED.patient_id,
            study_instance_uid = EXCLUDED.study_instance_uid,
            accession_number = EXCLUDED.accession_number,
            patient_code = EXCLUDED.patient_code,
            viewer_state = EXCLUDED.viewer_state,
            metadata = EXCLUDED.metadata,
            modified_by = EXCLUDED.modified_by,
            modified_at = NOW()
        RETURNING 1
    )
    SELECT
        (SELECT COUNT(1) FROM inserted_links)
        + (SELECT COUNT(1) FROM inserted_histories)
        + (SELECT COUNT(1) FROM inserted_results)
        + (SELECT COUNT(1) FROM inserted_result_images)
        + (SELECT COUNT(1) FROM inserted_viewer_states)
    INTO v_related_count;
END
`$`$;
"@
    Invoke-Psql $sql | Out-Null
}

function Seed-SmokeData {
    $manualCount = [Math]::Floor($Count / 3)
    $autoCount = [Math]::Floor($Count / 3)
    $remaining = $Count - $manualCount - $autoCount
    $nearCount = [Math]::Floor($remaining / 2)
    $openCount = $remaining - $nearCount

    $context = New-SmokeContext
    $groups = @(
        @{ Category = "MAN"; Abbr = "SMKEXP"; Bucket = 1; Count = $manualCount },
        @{ Category = "AUTO"; Abbr = "SMKAUTO"; Bucket = 2; Count = $autoCount },
        @{ Category = "NEAR"; Abbr = "SMKNEAR"; Bucket = 3; Count = $nearCount },
        @{ Category = "OPEN"; Abbr = "SMKOPEN"; Bucket = 4; Count = $openCount }
    )
    foreach ($group in $groups) {
        if ($group.Count -le 0) { continue }
        for ($start = 1; $start -le $group.Count; $start += $SeedChunkSize) {
            $end = [Math]::Min($group.Count, $start + $SeedChunkSize - 1)
            Add-SmokeStudies -Category $group.Category -Abbr $group.Abbr -UidBucket $group.Bucket -Start $start -End $end
            Write-Host ("Seeded {0} {1}-{2}/{3}" -f $group.Category, $start, $end, $group.Count)
        }
    }
    return [pscustomobject]@{
        Context = $context
        ManualExpired = $manualCount
        AutoDeleteReady = $autoCount
        NearExpiry = $nearCount
        Open = $openCount
    }
}

function Get-Review {
    param([string]$Token, [string]$HospitalKey, [string]$Status, [string]$SearchText, [int]$RowsPerPage = 100)
    $body = @{
        page = 1
        rowsPerPage = $RowsPerPage
        hospitalKey = $HospitalKey
        searchText = $SearchText
    }
    if (-not [string]::IsNullOrWhiteSpace($Status)) {
        $body.status = $Status
    }
    $response = Invoke-ApiPost -Path "/study-retention/review-list" -Body $body -Token $Token
    Assert-ApiSuccess $response "Retention review list ($Status/$SearchText)"
    return [pscustomobject]@{
        Rows = @(Get-ResponseRows $response)
        Total = Get-ResponseTotal $response
    }
}

function Invoke-BulkDeleteLoop {
    param([string]$Token, [string]$HospitalKey)
    $totalDeleted = 0
    $totalFailed = 0
    $totalSkipped = 0
    $loops = 0
    $maxLoops = [Math]::Max(200, [Math]::Ceiling($Count / 100) + 20)
    while ($true) {
        $loops++
        if ($loops -gt $maxLoops) {
            throw "Bulk delete loop exceeded $maxLoops batches."
        }
        $review = Get-Review -Token $Token -HospitalKey $HospitalKey -Status "EXPIRED_WAITING_APPROVAL" -SearchText "RET-SMOKE-MAN" -RowsPerPage 100
        if ($review.Rows.Count -le 0) {
            break
        }
        $keys = @($review.Rows | ForEach-Object { $_.studyPublicKey } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
        if ($keys.Count -le 0) {
            throw "Expired smoke rows were returned without public keys."
        }
        $response = Invoke-ApiPost -Path "/study-retention/bulk-delete" -Token $Token -Body @{
            studyPublicKeys = $keys
            chunkSize = $DeleteChunkSize
            note = "Retention smoke bulk delete"
        }
        Assert-ApiSuccess $response "Bulk delete"
        $responseRows = @(Get-ResponseRows $response)
        $row = $responseRows[0]
        $deleted = Get-IntProperty -Object $row -Name "deleted"
        $failed = Get-IntProperty -Object $row -Name "failed"
        $skipped = Get-IntProperty -Object $row -Name "skipped"
        $totalDeleted += $deleted
        $totalFailed += $failed
        $totalSkipped += $skipped
        Write-Host ("Bulk delete batch {0}: deleted={1}, failed={2}, skipped={3}" -f $loops, $deleted, $failed, $skipped)
        if ($failed -gt 0) {
            throw "Bulk delete failed for $failed smoke rows."
        }
        if ($deleted -eq 0 -and $review.Rows.Count -gt 0) {
            throw "Bulk delete made no progress while expired smoke rows remained."
        }
    }
    return [pscustomobject]@{ Deleted = $totalDeleted; Failed = $totalFailed; Skipped = $totalSkipped }
}

function Invoke-AutoDeleteLoop {
    param([string]$Token, [string]$HospitalKey)
    $totalDeleted = 0
    $totalFailed = 0
    $totalSkipped = 0
    $loops = 0
    $maxLoops = [Math]::Max(200, [Math]::Ceiling($Count / 500) + 20)
    while ($true) {
        $loops++
        if ($loops -gt $maxLoops) {
            throw "Auto-delete loop exceeded $maxLoops batches."
        }
        $before = Get-Review -Token $Token -HospitalKey $HospitalKey -Status "AUTO_DELETE_READY" -SearchText "RET-SMOKE-AUTO" -RowsPerPage 1
        if ($before.Total -le 0) {
            break
        }
        $response = Invoke-ApiPost -Path "/study-retention/auto-delete-run" -Token $Token -Body @{
            hospitalKey = $HospitalKey
            maxItems = 500
            chunkSize = $DeleteChunkSize
        }
        Assert-ApiSuccess $response "Auto-delete run"
        $responseRows = @(Get-ResponseRows $response)
        $row = $responseRows[0]
        $deleted = Get-IntProperty -Object $row -Name "deleted"
        $failed = Get-IntProperty -Object $row -Name "failed"
        $skipped = Get-IntProperty -Object $row -Name "skipped"
        $totalDeleted += $deleted
        $totalFailed += $failed
        $totalSkipped += $skipped
        Write-Host ("Auto-delete batch {0}: deleted={1}, failed={2}, skipped={3}" -f $loops, $deleted, $failed, $skipped)
        if ($failed -gt 0) {
            throw "Auto-delete failed for $failed smoke rows."
        }
        if ($deleted -eq 0 -and $before.Total -gt 0) {
            throw "Auto-delete made no progress while smoke auto-delete rows remained."
        }
    }
    return [pscustomobject]@{ Deleted = $totalDeleted; Failed = $totalFailed; Skipped = $totalSkipped }
}

function Get-SmokeDbCounts {
    $sql = @"
WITH categories(code, label) AS (
    VALUES
        ('MAN', 'manual'),
        ('AUTO', 'auto'),
        ('NEAR', 'near'),
        ('OPEN', 'open')
),
counts AS (
    SELECT c.label || 'Studies' AS metric, COUNT(s.id)::BIGINT AS value
    FROM categories c
    LEFT JOIN pacs_studies s ON s.accession_number LIKE 'RET-SMOKE-' || c.code || '-%'
    GROUP BY c.label

    UNION ALL
    SELECT c.label || 'DicomInstances' AS metric, COALESCE(SUM(COALESCE(s.instance_count, 0)), 0)::BIGINT AS value
    FROM categories c
    LEFT JOIN pacs_studies s ON s.accession_number LIKE 'RET-SMOKE-' || c.code || '-%'
    GROUP BY c.label

    UNION ALL
    SELECT c.label || 'Worklists' AS metric, COUNT(w.id)::BIGINT AS value
    FROM categories c
    LEFT JOIN pacs_worklists w ON w.visit_code LIKE 'VISIT-RET-SMOKE-' || c.code || '-%'
    GROUP BY c.label

    UNION ALL
    SELECT c.label || 'Links' AS metric, COUNT(link.id)::BIGINT AS value
    FROM categories c
    LEFT JOIN pacs_worklists w ON w.visit_code LIKE 'VISIT-RET-SMOKE-' || c.code || '-%'
    LEFT JOIN pacs_worklist_study_links link ON link.worklist_id = w.id
    GROUP BY c.label

    UNION ALL
    SELECT c.label || 'Histories' AS metric, COUNT(history.id)::BIGINT AS value
    FROM categories c
    LEFT JOIN pacs_worklists w ON w.visit_code LIKE 'VISIT-RET-SMOKE-' || c.code || '-%'
    LEFT JOIN pacs_worklist_histories history ON history.worklist_id = w.id
    GROUP BY c.label

    UNION ALL
    SELECT c.label || 'Results' AS metric, COUNT(pr.id)::BIGINT AS value
    FROM categories c
    LEFT JOIN pacs_results pr ON pr.result_text LIKE 'Retention smoke ' || c.code || ' result%'
    GROUP BY c.label

    UNION ALL
    SELECT c.label || 'ResultImages' AS metric, COUNT(img.id)::BIGINT AS value
    FROM categories c
    LEFT JOIN pacs_results pr ON pr.result_text LIKE 'Retention smoke ' || c.code || ' result%'
    LEFT JOIN pacs_result_images img ON img.result_id = pr.id
    GROUP BY c.label

    UNION ALL
    SELECT c.label || 'ViewerStates' AS metric, COUNT(vs.id)::BIGINT AS value
    FROM categories c
    LEFT JOIN pacs_viewer_states vs ON vs.metadata ->> 'retentionSmokeCategory' = c.code
    GROUP BY c.label

    UNION ALL
    SELECT 'deletedRequests' AS metric, COUNT(1)::BIGINT AS value
    FROM study_retention_delete_requests
    WHERE accession_number LIKE 'RET-SMOKE-%'
      AND status = 'DELETED'

    UNION ALL
    SELECT 'failedRequests' AS metric, COUNT(1)::BIGINT AS value
    FROM study_retention_delete_requests
    WHERE accession_number LIKE 'RET-SMOKE-%'
      AND status = 'DELETE_FAILED'
)
SELECT jsonb_object_agg(metric, value)::text
FROM counts;
"@
    return (Convert-PsqlJson (Invoke-Psql $sql))
}

if ($Action -eq "cleanup") {
    Invoke-SmokeCleanup
    Write-Host "Retention smoke cleanup completed."
    return
}

Write-Host "Cleaning previous retention smoke rows..."
Invoke-SmokeCleanup

Write-Host "Seeding retention smoke data ($Count studies, seed chunk $SeedChunkSize)..."
$seed = Seed-SmokeData

if ($Action -eq "seed") {
    $counts = Get-SmokeDbCounts
    [pscustomobject]@{
        action = "seed"
        count = $Count
        hospitalKey = $seed.Context.hospitalKey
        expected = @{
            expiredWaitingApproval = $seed.ManualExpired
            autoDeleteReady = $seed.AutoDeleteReady
            nearExpiry = $seed.NearExpiry
            open = $seed.Open
        }
        dbCounts = $counts
    } | ConvertTo-Json -Depth 8
    return
}

Write-Host "Logging in for live API smoke..."
$token = Get-AccessToken
$hospitalKey = [string]$seed.Context.hospitalKey

$manualReview = Get-Review -Token $token -HospitalKey $hospitalKey -Status "EXPIRED_WAITING_APPROVAL" -SearchText "RET-SMOKE-MAN"
$autoReview = Get-Review -Token $token -HospitalKey $hospitalKey -Status "AUTO_DELETE_READY" -SearchText "RET-SMOKE-AUTO"
$nearReview = Get-Review -Token $token -HospitalKey $hospitalKey -Status "NEAR_EXPIRY" -SearchText "RET-SMOKE-NEAR"

if ($manualReview.Total -ne $seed.ManualExpired) {
    throw "Expected $($seed.ManualExpired) expired smoke rows, found $($manualReview.Total)."
}
if ($autoReview.Total -ne $seed.AutoDeleteReady) {
    throw "Expected $($seed.AutoDeleteReady) auto-delete smoke rows, found $($autoReview.Total)."
}
if ($nearReview.Total -ne $seed.NearExpiry) {
    throw "Expected $($seed.NearExpiry) near-expiry smoke rows, found $($nearReview.Total)."
}

$bulk = Invoke-BulkDeleteLoop -Token $token -HospitalKey $hospitalKey
$auto = Invoke-AutoDeleteLoop -Token $token -HospitalKey $hospitalKey
$afterManual = Get-Review -Token $token -HospitalKey $hospitalKey -Status "EXPIRED_WAITING_APPROVAL" -SearchText "RET-SMOKE-MAN" -RowsPerPage 1
$afterAuto = Get-Review -Token $token -HospitalKey $hospitalKey -Status "AUTO_DELETE_READY" -SearchText "RET-SMOKE-AUTO" -RowsPerPage 1
$afterNear = Get-Review -Token $token -HospitalKey $hospitalKey -Status "NEAR_EXPIRY" -SearchText "RET-SMOKE-NEAR" -RowsPerPage 1
$dbCounts = Get-SmokeDbCounts

if ($bulk.Deleted -ne $seed.ManualExpired) {
    throw "Expected to bulk delete $($seed.ManualExpired), deleted $($bulk.Deleted)."
}
if ($auto.Deleted -ne $seed.AutoDeleteReady) {
    throw "Expected to auto delete $($seed.AutoDeleteReady), deleted $($auto.Deleted)."
}
if ($afterManual.Total -ne 0 -or $afterAuto.Total -ne 0) {
    throw "Expired or auto-delete smoke rows remain after deletion."
}
if ($afterNear.Total -ne $seed.NearExpiry) {
    throw "Near-expiry rows changed unexpectedly. Expected $($seed.NearExpiry), found $($afterNear.Total)."
}
if ([int]$dbCounts.failedRequests -ne 0) {
    throw "Smoke deletion left failed retention delete requests."
}

$deletedRelatedKeys = @(
    "manualStudies", "manualDicomInstances", "manualWorklists", "manualLinks", "manualHistories", "manualResults", "manualResultImages", "manualViewerStates",
    "autoStudies", "autoDicomInstances", "autoWorklists", "autoLinks", "autoHistories", "autoResults", "autoResultImages", "autoViewerStates"
)
foreach ($key in $deletedRelatedKeys) {
    if ((Get-IntProperty -Object $dbCounts -Name $key) -ne 0) {
        throw "Smoke cleanup left $key=$((Get-IntProperty -Object $dbCounts -Name $key)) after delete."
    }
}

$retainedExpectations = @{
    nearStudies = $seed.NearExpiry
    nearDicomInstances = $seed.NearExpiry
    nearWorklists = $seed.NearExpiry
    nearLinks = $seed.NearExpiry
    nearHistories = $seed.NearExpiry
    nearResults = $seed.NearExpiry
    nearResultImages = $seed.NearExpiry
    nearViewerStates = $seed.NearExpiry
    openStudies = $seed.Open
    openDicomInstances = $seed.Open
    openWorklists = $seed.Open
    openLinks = $seed.Open
    openHistories = $seed.Open
    openResults = $seed.Open
    openResultImages = $seed.Open
    openViewerStates = $seed.Open
}
foreach ($entry in $retainedExpectations.GetEnumerator()) {
    $actual = Get-IntProperty -Object $dbCounts -Name $entry.Key
    if ($actual -ne $entry.Value) {
        throw "Smoke retained-data check failed for $($entry.Key). Expected $($entry.Value), found $actual."
    }
}

[pscustomobject]@{
    action = "test"
    count = $Count
    seedChunkSize = $SeedChunkSize
    deleteChunkSize = $DeleteChunkSize
    hospitalKey = $hospitalKey
    expected = @{
        expiredWaitingApproval = $seed.ManualExpired
        autoDeleteReady = $seed.AutoDeleteReady
        nearExpiry = $seed.NearExpiry
        open = $seed.Open
    }
    bulkDelete = $bulk
    autoDelete = $auto
    remaining = @{
        expiredWaitingApproval = $afterManual.Total
        autoDeleteReady = $afterAuto.Total
        nearExpiry = $afterNear.Total
        openDbRows = [int]$dbCounts.openStudies
        nearRelatedRows = @{
            studies = [int]$dbCounts.nearStudies
            dicomInstances = [int]$dbCounts.nearDicomInstances
            worklists = [int]$dbCounts.nearWorklists
            links = [int]$dbCounts.nearLinks
            histories = [int]$dbCounts.nearHistories
            results = [int]$dbCounts.nearResults
            resultImages = [int]$dbCounts.nearResultImages
            viewerStates = [int]$dbCounts.nearViewerStates
        }
        openRelatedRows = @{
            studies = [int]$dbCounts.openStudies
            dicomInstances = [int]$dbCounts.openDicomInstances
            worklists = [int]$dbCounts.openWorklists
            links = [int]$dbCounts.openLinks
            histories = [int]$dbCounts.openHistories
            results = [int]$dbCounts.openResults
            resultImages = [int]$dbCounts.openResultImages
            viewerStates = [int]$dbCounts.openViewerStates
        }
    }
    audit = @{
        deletedRequests = [int]$dbCounts.deletedRequests
        failedRequests = [int]$dbCounts.failedRequests
    }
} | ConvertTo-Json -Depth 8
