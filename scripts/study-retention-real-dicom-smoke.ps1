Param(
    [ValidateSet("test", "seed", "cleanup")]
    [string]$Action = "test",
    [int]$Count = 30,
    [int]$UploadChunkSize = 10,
    [int]$DeleteChunkSize = 25,
    [string]$ApiBaseUrl = "http://127.0.0.1:8080/pacsApi",
    [string]$Username = $(if ($env:PACS_USERNAME) { $env:PACS_USERNAME } else { "admin" }),
    [string]$Password = $(if ($env:PACS_PASSWORD) { $env:PACS_PASSWORD } else { "1" }),
    [string]$SourceHospitalCode = "H001",
    [string]$SmokeHospitalCode = "SMKREAL",
    [switch]$AllowNonLocalTarget
)

$ErrorActionPreference = "Stop"

if ($Count -lt 1) {
    throw "Count must be at least 1."
}
if ($UploadChunkSize -lt 1 -or $UploadChunkSize -gt 100) {
    throw "UploadChunkSize must be between 1 and 100."
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
    throw "Real DICOM retention smoke is local-only by default. Use -AllowNonLocalTarget only for an isolated test environment."
}

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$repoRoot = Resolve-Path (Join-Path $projectRoot "..")
$envDbPath = Join-Path $projectRoot ".env.db"
$workRoot = Join-Path $repoRoot ".codex-tmp\real-dicom-retention"

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

function Convert-PsqlJson {
    param([string]$Text)
    $jsonLine = @($Text -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_.StartsWith("{") -or $_.StartsWith("[") }) | Select-Object -Last 1
    if ([string]::IsNullOrWhiteSpace($jsonLine)) {
        throw "Expected JSON from psql, got: $Text"
    }
    return ($jsonLine | ConvertFrom-Json)
}

function Invoke-ApiPost {
    param([string]$Path, [hashtable]$Body, [string]$Token)
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers["Authorization"] = "Bearer $Token"
    }
    $json = ($Body | ConvertTo-Json -Depth 20 -Compress)
    return Invoke-RestMethod -Method Post -Uri ($ApiBaseUrl.TrimEnd("/") + $Path) -ContentType "application/json" -Headers $headers -Body $json -TimeoutSec 180
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

function Get-DicomHttpContext {
    $sourceCode = ConvertTo-SqlLiteral $SourceHospitalCode
    $smokeCode = ConvertTo-SqlLiteral $SmokeHospitalCode
    $sql = @"
WITH selected_server AS (
    SELECT ds.*
    FROM hospitals h
    INNER JOIN hospital_dicom_servers ds ON ds.hospital_id = h.id AND ds.is_active = 1
    WHERE h.code = $smokeCode
    ORDER BY ds.id
    LIMIT 1
),
fallback_server AS (
    SELECT ds.*
    FROM hospitals h
    INNER JOIN hospital_dicom_servers ds ON ds.hospital_id = h.id AND ds.is_active = 1
    WHERE h.code = $sourceCode
    ORDER BY ds.id
    LIMIT 1
),
server_row AS (
    SELECT * FROM selected_server
    UNION ALL
    SELECT * FROM fallback_server WHERE NOT EXISTS (SELECT 1 FROM selected_server)
    LIMIT 1
)
SELECT jsonb_build_object(
    'baseUrl', 'http://127.0.0.1:' || port,
    'username', username,
    'password', password
)::text
FROM server_row;
"@
    return (Convert-PsqlJson (Invoke-Psql $sql))
}

function Invoke-DicomStudyHttp {
    param([string]$Method, [string]$StudyId, $DicomContext)
    if ([string]::IsNullOrWhiteSpace($StudyId)) { return 0 }
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if (-not $curl) { $curl = Get-Command curl -ErrorAction SilentlyContinue }
    if (-not $curl) { throw "curl is required for DICOM server verification." }
    $url = ([string]$DicomContext.baseUrl).TrimEnd("/") + "/studies/" + $StudyId
    $args = @("-sS", "-o", "NUL", "-w", "%{http_code}", "-X", $Method)
    if (-not [string]::IsNullOrWhiteSpace([string]$DicomContext.username)) {
        $args += @("-u", ([string]$DicomContext.username + ":" + [string]$DicomContext.password))
    }
    $args += $url
    $output = & $curl.Source @args 2>$null
    if ($LASTEXITCODE -ne 0 -and $Method -ne "GET") {
        throw "DICOM server $Method failed for study $StudyId."
    }
    $status = (($output | Out-String).Trim())
    if ([string]::IsNullOrWhiteSpace($status)) { return 0 }
    return [int]$status
}

function Remove-ExistingRealDicomStudies {
    $idsText = Invoke-Psql @"
SELECT COALESCE(jsonb_agg(DISTINCT dicom_server_study_id), '[]'::jsonb)::text
FROM pacs_studies
WHERE (accession_number LIKE 'RTR-%' OR accession_number LIKE 'RET-REAL-%')
  AND dicom_server_study_id IS NOT NULL
  AND BTRIM(dicom_server_study_id) != '';
"@
    $ids = @(Convert-PsqlJson $idsText)
    if ($ids.Count -le 0) { return }
    $context = Get-DicomHttpContext
    foreach ($id in $ids) {
        $status = Invoke-DicomStudyHttp -Method "DELETE" -StudyId ([string]$id) -DicomContext $context
        if ($status -ne 200 -and $status -ne 202 -and $status -ne 204 -and $status -ne 404) {
            throw "DICOM cleanup delete returned HTTP $status for study $id."
        }
    }
}

function Invoke-SmokeCleanup {
    Remove-ExistingRealDicomStudies
    $smokeCode = ConvertTo-SqlLiteral $SmokeHospitalCode
    $sql = @"
DO `$`$
DECLARE
    v_hospital_id BIGINT;
    v_viewer_state_count BIGINT;
BEGIN
    SELECT id INTO v_hospital_id
    FROM hospitals
    WHERE code = $smokeCode
    LIMIT 1;

    DELETE FROM study_retention_delete_requests
    WHERE accession_number LIKE 'RTR-%'
       OR accession_number LIKE 'RET-REAL-%'
       OR study_instance_uid LIKE '1.2.826.0.1.3680043.10.7779.%'
       OR patient_mrn LIKE 'RTR-%'
       OR patient_mrn LIKE 'RET-REAL-%'
       OR hospital_id = v_hospital_id;

    DELETE FROM pacs_result_images img
    USING pacs_results pr
    WHERE img.result_id = pr.id
      AND (
          pr.result_text LIKE 'Retention real DICOM %'
          OR pr.study_id IN (SELECT id FROM pacs_studies WHERE accession_number LIKE 'RTR-%' OR hospital_id = v_hospital_id)
          OR pr.worklist_id IN (SELECT id FROM pacs_worklists WHERE visit_code LIKE 'VISIT-RTR-%' OR hospital_id = v_hospital_id)
      );

    DELETE FROM pacs_viewer_states
    WHERE accession_number LIKE 'RTR-%'
       OR study_instance_uid LIKE '1.2.826.0.1.3680043.10.7779.%'
       OR metadata ->> 'retentionRealDicomSmoke' = 'true'
       OR study_id IN (SELECT id FROM pacs_studies WHERE accession_number LIKE 'RTR-%' OR hospital_id = v_hospital_id)
       OR worklist_id IN (SELECT id FROM pacs_worklists WHERE visit_code LIKE 'VISIT-RTR-%' OR hospital_id = v_hospital_id);

    DELETE FROM pacs_results
    WHERE result_text LIKE 'Retention real DICOM %'
       OR study_id IN (SELECT id FROM pacs_studies WHERE accession_number LIKE 'RTR-%' OR hospital_id = v_hospital_id)
       OR worklist_id IN (SELECT id FROM pacs_worklists WHERE visit_code LIKE 'VISIT-RTR-%' OR hospital_id = v_hospital_id)
       OR patient_id IN (SELECT id FROM patients WHERE patient_uid LIKE 'RTR-%' OR patient_hn LIKE 'RTR-%' OR hospital_id = v_hospital_id);

    DELETE FROM pacs_worklist_histories
    WHERE worklist_id IN (SELECT id FROM pacs_worklists WHERE visit_code LIKE 'VISIT-RTR-%' OR hospital_id = v_hospital_id)
       OR patient_id IN (SELECT id FROM patients WHERE patient_uid LIKE 'RTR-%' OR patient_hn LIKE 'RTR-%' OR hospital_id = v_hospital_id);

    DELETE FROM pacs_worklist_study_links
    WHERE study_id IN (SELECT id FROM pacs_studies WHERE accession_number LIKE 'RTR-%' OR hospital_id = v_hospital_id)
       OR worklist_id IN (SELECT id FROM pacs_worklists WHERE visit_code LIKE 'VISIT-RTR-%' OR hospital_id = v_hospital_id);

    DELETE FROM pacs_worklists
    WHERE visit_code LIKE 'VISIT-RTR-%'
       OR notes LIKE 'RETENTION_REAL_DICOM_SMOKE%'
       OR hospital_id = v_hospital_id;

    DELETE FROM pacs_studies
    WHERE accession_number LIKE 'RTR-%'
       OR study_instance_uid LIKE '1.2.826.0.1.3680043.10.7779.%'
       OR hospital_id = v_hospital_id;

    DELETE FROM patients
    WHERE patient_uid LIKE 'RTR-%'
       OR patient_hn LIKE 'RTR-%'
       OR hospital_id = v_hospital_id;

    DELETE FROM study_retention_policies
    WHERE notes LIKE 'RETENTION_REAL_DICOM_SMOKE%'
       OR hospital_id = v_hospital_id;

    DELETE FROM pacs_patient_sequences
    WHERE hospital_id = v_hospital_id;

    DELETE FROM hospital_modality_server_routes WHERE hospital_id = v_hospital_id;
    DELETE FROM hospital_dicom_servers WHERE hospital_id = v_hospital_id;
    DELETE FROM hospital_modalities hm
    USING modalities m
    WHERE hm.modality_id = m.id
      AND (m.abbr IN ('RLXEXP', 'RLXAUTO', 'RLXNEAR', 'RLXOPEN') OR hm.hospital_id = v_hospital_id);
    DELETE FROM modalities
    WHERE abbr IN ('RLXEXP', 'RLXAUTO', 'RLXNEAR', 'RLXOPEN')
       OR name LIKE 'Retention Real DICOM %';
    DELETE FROM hospitals WHERE id = v_hospital_id;
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
    ORDER BY ds.id
    LIMIT 1;
    IF v_source_server_id IS NULL THEN
        RAISE EXCEPTION 'No active DICOM server found for source hospital %.', $sourceCode;
    END IF;
END
`$`$;

WITH smoke_hospital AS (
    INSERT INTO hospitals (code, name, timezone, is_active, created_by, created, created_at)
    VALUES ($smokeCode, 'Retention Real DICOM Smoke Hospital', 'Asia/Phnom_Penh', 1, 1, NOW(), NOW())
    RETURNING id, public_id::text AS public_key
),
source_server AS (
    SELECT ds.*
    FROM hospitals h
    INNER JOIN hospital_dicom_servers ds ON ds.hospital_id = h.id AND ds.is_active = 1
    WHERE h.code = $sourceCode
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
        h.id, 'RETENTION_REAL_DICOM_SERVER', s.ip_address, s.port, 'RLXRET', s.username, s.password, 1,
        1, 1, NOW(), NOW(), s.viewer_base_url, s.dicom_port,
        s.ssl_enabled, s.authentication_enabled, s.authorization_enabled, s.dicomweb_path
    FROM smoke_hospital h
    CROSS JOIN source_server s
    RETURNING id, public_id::text AS public_key, username, password, port
),
smoke_modalities AS (
    INSERT INTO modalities (name, abbr, is_active, created_by, modified_by, created_at, modified_at)
    VALUES
        ('Retention Real DICOM Expired', 'RLXEXP', 1, 1, 1, NOW(), NOW()),
        ('Retention Real DICOM Auto Delete', 'RLXAUTO', 1, 1, 1, NOW(), NOW()),
        ('Retention Real DICOM Near Expiry', 'RLXNEAR', 1, 1, 1, NOW(), NOW()),
        ('Retention Real DICOM Open', 'RLXOPEN', 1, 1, 1, NOW(), NOW())
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
           CASE WHEN m.abbr IN ('RLXEXP', 'RLXAUTO') THEN 1 ELSE 30 END,
           CASE WHEN m.abbr IN ('RLXEXP', 'RLXAUTO') THEN 1 ELSE 30 END,
           'DAY',
           CASE WHEN m.abbr IN ('RLXEXP', 'RLXAUTO') THEN 0 ELSE 14 END,
           CASE WHEN m.abbr = 'RLXAUTO' THEN FALSE ELSE TRUE END,
           TRUE,
           CASE WHEN m.abbr = 'RLXAUTO' THEN TRUE ELSE FALSE END,
           'RETENTION_REAL_DICOM_SMOKE ' || m.abbr,
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
    'dicomServerKey', (SELECT public_key FROM smoke_server),
    'dicomHostBaseUrl', 'http://127.0.0.1:' || (SELECT port FROM smoke_server),
    'dicomUsername', (SELECT username FROM smoke_server),
    'dicomPassword', (SELECT password FROM smoke_server)
)::text;
"@
    return (Convert-PsqlJson (Invoke-Psql $sql))
}

function New-CategoryPlan {
    $manualCount = [Math]::Floor($Count / 3)
    $autoCount = [Math]::Floor($Count / 3)
    $remaining = $Count - $manualCount - $autoCount
    $nearCount = [Math]::Floor($remaining / 2)
    $openCount = $remaining - $nearCount
    return @(
        [pscustomobject]@{ Category = "MAN"; Abbr = "RLXEXP"; Bucket = 1; Count = $manualCount },
        [pscustomobject]@{ Category = "AUTO"; Abbr = "RLXAUTO"; Bucket = 2; Count = $autoCount },
        [pscustomobject]@{ Category = "NEAR"; Abbr = "RLXNEAR"; Bucket = 3; Count = $nearCount },
        [pscustomobject]@{ Category = "OPEN"; Abbr = "RLXOPEN"; Bucket = 4; Count = $openCount }
    )
}

function Assert-PydicomAvailable {
    $check = & python -c "import importlib.util; raise SystemExit(0 if importlib.util.find_spec('pydicom') else 1)"
    if ($LASTEXITCODE -ne 0) {
        throw "Python package pydicom is required for real DICOM smoke generation."
    }
}

function New-RealDicomFiles {
    param($Plan)
    Assert-PydicomAvailable
    if (Test-Path $workRoot) {
        Remove-Item -LiteralPath $workRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $workRoot | Out-Null
    $dicomDir = Join-Path $workRoot "dicom"
    New-Item -ItemType Directory -Force -Path $dicomDir | Out-Null

    $spec = @()
    foreach ($group in $Plan) {
        for ($i = 1; $i -le $group.Count; $i++) {
            $accession = "RTR-$($group.Category)-{0:D6}" -f $i
            $patientId = "RTR-$($group.Category)-P{0:D6}" -f $i
            $uidRoot = "1.2.826.0.1.3680043.10.7779.$($group.Bucket).$i"
            $spec += [ordered]@{
                path = (Join-Path $dicomDir ($accession + ".dcm"))
                patient_id = $patientId
                patient_name = "Real$($group.Category)^Patient$i"
                patient_birth_date = "19700101"
                patient_sex = "O"
                study_instance_uid = $uidRoot
                series_instance_uid = "$uidRoot.1"
                sop_instance_uid = "$uidRoot.1.1"
                accession = $accession
                modality = $group.Abbr
                study_description = "Retention real DICOM $($group.Category) $i"
                institution = "Retention Real DICOM Smoke Hospital"
                study_date = "20260617"
                category = $group.Category
            }
        }
    }

    $specPath = Join-Path $workRoot "dicom-spec.json"
    $generatorPath = Join-Path $workRoot "generate_dicom.py"
    $spec | ConvertTo-Json -Depth 8 | Set-Content -Path $specPath -Encoding UTF8
    @'
import json
import sys
from pathlib import Path
from pydicom.dataset import FileDataset, FileMetaDataset
from pydicom.uid import ExplicitVRLittleEndian, SecondaryCaptureImageStorage, PYDICOM_IMPLEMENTATION_UID

spec_path = Path(sys.argv[1])
items = json.loads(spec_path.read_text(encoding="utf-8-sig"))
for item in items:
    path = Path(item["path"])
    path.parent.mkdir(parents=True, exist_ok=True)
    file_meta = FileMetaDataset()
    file_meta.MediaStorageSOPClassUID = SecondaryCaptureImageStorage
    file_meta.MediaStorageSOPInstanceUID = item["sop_instance_uid"]
    file_meta.TransferSyntaxUID = ExplicitVRLittleEndian
    file_meta.ImplementationClassUID = PYDICOM_IMPLEMENTATION_UID

    ds = FileDataset(str(path), {}, file_meta=file_meta, preamble=b"\0" * 128)
    ds.SpecificCharacterSet = "ISO_IR 192"
    ds.SOPClassUID = SecondaryCaptureImageStorage
    ds.SOPInstanceUID = item["sop_instance_uid"]
    ds.PatientID = item["patient_id"]
    ds.PatientName = item["patient_name"]
    ds.PatientBirthDate = item["patient_birth_date"]
    ds.PatientSex = item["patient_sex"]
    ds.StudyInstanceUID = item["study_instance_uid"]
    ds.SeriesInstanceUID = item["series_instance_uid"]
    ds.StudyID = item["accession"][-16:]
    ds.AccessionNumber = item["accession"]
    ds.Modality = item["modality"]
    ds.StudyDescription = item["study_description"]
    ds.InstitutionName = item["institution"]
    ds.StudyDate = item["study_date"]
    ds.StudyTime = "101010"
    ds.SeriesNumber = 1
    ds.InstanceNumber = 1
    ds.ImageType = ["ORIGINAL", "PRIMARY"]
    ds.Manufacturer = "UDAYA PACS Smoke"
    ds.SamplesPerPixel = 1
    ds.PhotometricInterpretation = "MONOCHROME2"
    ds.Rows = 2
    ds.Columns = 2
    ds.BitsAllocated = 8
    ds.BitsStored = 8
    ds.HighBit = 7
    ds.PixelRepresentation = 0
    ds.PixelData = bytes([0, 64, 128, 255])
    ds.save_as(str(path), write_like_original=False)
'@ | Set-Content -Path $generatorPath -Encoding UTF8

    & python $generatorPath $specPath
    if ($LASTEXITCODE -ne 0) {
        throw "DICOM generation failed."
    }
    return @($spec | ForEach-Object { [pscustomobject]$_ })
}

function Invoke-DicomUploadChunk {
    param([string]$Token, [string]$HospitalKey, [string]$DicomServerKey, [array]$Files)
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if (-not $curl) { $curl = Get-Command curl -ErrorAction SilentlyContinue }
    if (-not $curl) { throw "curl is required for multipart upload." }

    $args = @(
        "-fsS",
        "--max-time", "240",
        "-H", "Authorization: Bearer $Token",
        "-F", "hospitalKey=$HospitalKey",
        "-F", "dicomServerKey=$DicomServerKey"
    )
    foreach ($file in $Files) {
        $args += @("-F", "files=@$($file.path);type=application/dicom")
    }
    $args += ($ApiBaseUrl.TrimEnd("/") + "/dicom-uploads")

    $output = & $curl.Source @args 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "DICOM upload curl failed: $($output | Out-String)"
    }
    $response = (($output | Out-String).Trim() | ConvertFrom-Json)
    Assert-ApiSuccess $response "DICOM upload"
    $rows = @(Get-ResponseRows $response)
    if ($rows.Count -le 0) {
        throw "DICOM upload response did not contain a body row."
    }
    $row = $rows[0]
    $accepted = Get-IntProperty -Object $row -Name "acceptedFiles"
    $failed = Get-IntProperty -Object $row -Name "failedFiles"
    if ($accepted -ne $Files.Count -or $failed -ne 0) {
        $errors = ""
        if ($row.errors) { $errors = ($row.errors | Out-String).Trim() }
        throw "DICOM upload accepted=$accepted failed=$failed expected=$($Files.Count). $errors"
    }
}

function Invoke-RealDicomUploads {
    param([string]$Token, $Context, [array]$Files)
    $uploaded = 0
    for ($start = 0; $start -lt $Files.Count; $start += $UploadChunkSize) {
        $chunk = @($Files[$start..([Math]::Min($Files.Count - 1, $start + $UploadChunkSize - 1))])
        Invoke-DicomUploadChunk -Token $Token -HospitalKey ([string]$Context.hospitalKey) -DicomServerKey ([string]$Context.dicomServerKey) -Files $chunk
        $uploaded += $chunk.Count
        Write-Host ("Uploaded real DICOM files {0}/{1}" -f $uploaded, $Files.Count)
    }
}

function Update-UploadedStudiesForRetention {
    $sql = @"
DO `$`$
DECLARE
    v_hospital_id BIGINT;
    v_viewer_state_count BIGINT;
BEGIN
    SELECT id INTO v_hospital_id FROM hospitals WHERE code = $(ConvertTo-SqlLiteral $SmokeHospitalCode) LIMIT 1;
    IF v_hospital_id IS NULL THEN
        RAISE EXCEPTION 'Real DICOM smoke hospital is missing.';
    END IF;

    UPDATE pacs_studies
    SET
        received_at = CASE
            WHEN accession_number LIKE 'RTR-MAN-%' THEN NOW() - INTERVAL '2 days'
            WHEN accession_number LIKE 'RTR-AUTO-%' THEN NOW() - INTERVAL '2 days'
            WHEN accession_number LIKE 'RTR-NEAR-%' THEN NOW() - INTERVAL '20 days'
            ELSE NOW()
        END,
        image_received_at = CASE
            WHEN accession_number LIKE 'RTR-MAN-%' THEN NOW() - INTERVAL '2 days'
            WHEN accession_number LIKE 'RTR-AUTO-%' THEN NOW() - INTERVAL '2 days'
            WHEN accession_number LIKE 'RTR-NEAR-%' THEN NOW() - INTERVAL '20 days'
            ELSE NOW()
        END,
        study_date = CAST(CASE
            WHEN accession_number LIKE 'RTR-MAN-%' THEN NOW() - INTERVAL '2 days'
            WHEN accession_number LIKE 'RTR-AUTO-%' THEN NOW() - INTERVAL '2 days'
            WHEN accession_number LIKE 'RTR-NEAR-%' THEN NOW() - INTERVAL '20 days'
            ELSE NOW()
        END AS DATE),
        modified = NOW()
    WHERE hospital_id = v_hospital_id
      AND accession_number LIKE 'RTR-%';

    WITH study_rows AS (
        SELECT
            s.*,
            p.patient_hn,
            NULLIF(TRIM(CONCAT(COALESCE(p.first_name, ''), ' ', COALESCE(p.last_name, ''))), '') AS patient_name
        FROM pacs_studies s
        INNER JOIN patients p ON p.id = s.patient_id
        WHERE s.hospital_id = v_hospital_id
          AND s.accession_number LIKE 'RTR-%'
    ),
    inserted_worklists AS (
        INSERT INTO pacs_worklists (
            hospital_id, patient_id, status, notes, created, modified, modality_id, visit_code,
            created_by, modified_by, created_at, modified_at, dicom_server_worklist_id,
            dicom_server_worklist_path, sent_at, received_at, study_description, scheduled_date,
            scheduled_time, image_received_at, study_id
        )
        SELECT
            sr.hospital_id,
            sr.patient_id,
            2,
            'RETENTION_REAL_DICOM_SMOKE received worklist',
            NOW(),
            NOW(),
            sr.modality_id,
            'VISIT-' || sr.accession_number,
            1,
            1,
            NOW(),
            NOW(),
            'wl-' || LOWER(sr.accession_number),
            '/retention-real-dicom/' || LOWER(sr.accession_number),
            sr.received_at,
            sr.received_at,
            sr.study_description,
            sr.study_date,
            CAST(sr.received_at AS TIME),
            sr.image_received_at,
            sr.id
        FROM study_rows sr
        ON CONFLICT (hospital_id, visit_code) WHERE visit_code IS NOT NULL DO UPDATE
        SET
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
        INSERT INTO pacs_worklist_study_links (hospital_id, worklist_id, study_id, is_primary, linked_at, created_by)
        SELECT hospital_id, worklist_id, study_id, 1, NOW(), 1
        FROM inserted_worklists
        ON CONFLICT (hospital_id, worklist_id, study_id) DO UPDATE
        SET is_primary = 1, linked_at = EXCLUDED.linked_at
        RETURNING 1
    ),
    inserted_histories AS (
        INSERT INTO pacs_worklist_histories (hospital_id, worklist_id, patient_id, from_status, to_status, action, reason, created, created_by)
        SELECT hospital_id, worklist_id, patient_id, 1, 2, 'RETENTION_REAL_DICOM_IMAGE_RECEIVED', 'Real DICOM retention smoke linked worklist/study cleanup row', NOW(), 1
        FROM inserted_worklists
        RETURNING 1
    ),
    inserted_results AS (
        INSERT INTO pacs_results (
            hospital_id, modality_id, study_id, worklist_id, patient_id, result_date,
            result_text, status, completed, is_active, created_by, created_at, modified_at
        )
        SELECT
            sr.hospital_id,
            sr.modality_id,
            sr.id,
            iw.worklist_id,
            sr.patient_id,
            sr.study_date,
            'Retention real DICOM result for ' || sr.accession_number,
            'IMAGE_RECEIVED',
            FALSE,
            1,
            1,
            NOW(),
            NOW()
        FROM study_rows sr
        INNER JOIN inserted_worklists iw ON iw.study_id = sr.id
        ON CONFLICT (hospital_id, modality_id, study_id) WHERE is_active = 1 AND study_id IS NOT NULL DO UPDATE
        SET worklist_id = EXCLUDED.worklist_id,
            patient_id = EXCLUDED.patient_id,
            result_text = EXCLUDED.result_text,
            status = EXCLUDED.status,
            completed = EXCLUDED.completed,
            modified_at = NOW()
        RETURNING id, study_id
    ),
    inserted_result_images AS (
        INSERT INTO pacs_result_images (result_id, image_path, original_file_name, file_type, file_size, sort_order, is_active, created_at)
        SELECT
            ir.id,
            '/retention-real-dicom/' || LOWER(sr.accession_number) || '.dcm',
            sr.accession_number || '.dcm',
            'application/dicom',
            2048,
            0,
            1,
            NOW()
        FROM inserted_results ir
        INNER JOIN study_rows sr ON sr.id = ir.study_id
        WHERE NOT EXISTS (
            SELECT 1 FROM pacs_result_images existing
            WHERE existing.result_id = ir.id
              AND existing.image_path = '/retention-real-dicom/' || LOWER(sr.accession_number) || '.dcm'
        )
        RETURNING 1
    ),
    inserted_viewer_states AS (
        INSERT INTO pacs_viewer_states (
            hospital_id, modality_id, study_id, worklist_id, patient_id, study_instance_uid,
            accession_number, patient_code, state_type, schema_version, viewer_state,
            measurements, annotations, segmentations, additional_findings, metadata,
            version, created_by, modified_by, is_active, created_at, modified_at
        )
        SELECT
            sr.hospital_id,
            sr.modality_id,
            sr.id,
            iw.worklist_id,
            sr.patient_id,
            sr.study_instance_uid,
            sr.accession_number,
            sr.patient_hn,
            'OHIF_VIEWER_STATE',
            1,
            jsonb_build_object('retentionRealDicomSmoke', true, 'study', sr.accession_number),
            '[]'::jsonb,
            '[]'::jsonb,
            '[]'::jsonb,
            '[]'::jsonb,
            jsonb_build_object('retentionRealDicomSmoke', 'true', 'accession', sr.accession_number),
            1,
            1,
            1,
            1,
            NOW(),
            NOW()
        FROM study_rows sr
        INNER JOIN inserted_worklists iw ON iw.study_id = sr.id
        ON CONFLICT (hospital_id, worklist_id, state_type) WHERE is_active = 1 AND worklist_id IS NOT NULL DO UPDATE
        SET study_id = EXCLUDED.study_id,
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
    SELECT COUNT(*) INTO v_viewer_state_count FROM inserted_viewer_states;
END
`$`$;
"@
    Invoke-Psql $sql | Out-Null
}

function Get-PlanCount {
    param([array]$Plan, [string]$Category)
    $row = $Plan | Where-Object { $_.Category -eq $Category } | Select-Object -First 1
    if ($null -eq $row) {
        return 0
    }
    return [int]$row.Count
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
        $review = Get-Review -Token $Token -HospitalKey $HospitalKey -Status "EXPIRED_WAITING_APPROVAL" -SearchText "RTR-MAN" -RowsPerPage 100
        if ($review.Rows.Count -le 0) { break }
        $keys = @($review.Rows | ForEach-Object { $_.studyPublicKey } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
        if ($keys.Count -le 0) {
            throw "Expired real DICOM rows were returned without public keys."
        }
        $response = Invoke-ApiPost -Path "/study-retention/bulk-delete" -Token $Token -Body @{
            studyPublicKeys = $keys
            chunkSize = $DeleteChunkSize
            note = "Real DICOM retention smoke bulk delete"
        }
        Assert-ApiSuccess $response "Bulk delete"
        $row = @(Get-ResponseRows $response)[0]
        $deleted = Get-IntProperty -Object $row -Name "deleted"
        $failed = Get-IntProperty -Object $row -Name "failed"
        $skipped = Get-IntProperty -Object $row -Name "skipped"
        $totalDeleted += $deleted
        $totalFailed += $failed
        $totalSkipped += $skipped
        Write-Host ("Bulk delete batch {0}: deleted={1}, failed={2}, skipped={3}" -f $loops, $deleted, $failed, $skipped)
        if ($failed -gt 0) { throw "Bulk delete failed for $failed real DICOM rows." }
        if ($deleted -eq 0 -and $review.Rows.Count -gt 0) { throw "Bulk delete made no progress." }
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
        $before = Get-Review -Token $Token -HospitalKey $HospitalKey -Status "AUTO_DELETE_READY" -SearchText "RTR-AUTO" -RowsPerPage 1
        if ($before.Total -le 0) { break }
        $response = Invoke-ApiPost -Path "/study-retention/auto-delete-run" -Token $Token -Body @{
            hospitalKey = $HospitalKey
            maxItems = 500
            chunkSize = $DeleteChunkSize
        }
        Assert-ApiSuccess $response "Auto-delete run"
        $row = @(Get-ResponseRows $response)[0]
        $deleted = Get-IntProperty -Object $row -Name "deleted"
        $failed = Get-IntProperty -Object $row -Name "failed"
        $skipped = Get-IntProperty -Object $row -Name "skipped"
        $totalDeleted += $deleted
        $totalFailed += $failed
        $totalSkipped += $skipped
        Write-Host ("Auto-delete batch {0}: deleted={1}, failed={2}, skipped={3}" -f $loops, $deleted, $failed, $skipped)
        if ($failed -gt 0) { throw "Auto-delete failed for $failed real DICOM rows." }
        if ($deleted -eq 0 -and $before.Total -gt 0) { throw "Auto-delete made no progress." }
    }
    return [pscustomobject]@{ Deleted = $totalDeleted; Failed = $totalFailed; Skipped = $totalSkipped }
}

function Get-RealDbCounts {
    $sql = @"
WITH categories(code, label) AS (
    VALUES ('MAN', 'manual'), ('AUTO', 'auto'), ('NEAR', 'near'), ('OPEN', 'open')
),
counts AS (
    SELECT c.label || 'Studies' AS metric, COUNT(s.id)::BIGINT AS value
    FROM categories c LEFT JOIN pacs_studies s ON s.accession_number LIKE 'RTR-' || c.code || '-%'
    GROUP BY c.label
    UNION ALL
    SELECT c.label || 'DicomInstances', COALESCE(SUM(COALESCE(s.instance_count, 0)), 0)::BIGINT
    FROM categories c LEFT JOIN pacs_studies s ON s.accession_number LIKE 'RTR-' || c.code || '-%'
    GROUP BY c.label
    UNION ALL
    SELECT c.label || 'Worklists', COUNT(w.id)::BIGINT
    FROM categories c LEFT JOIN pacs_worklists w ON w.visit_code LIKE 'VISIT-RTR-' || c.code || '-%'
    GROUP BY c.label
    UNION ALL
    SELECT c.label || 'Links', COUNT(link.id)::BIGINT
    FROM categories c
    LEFT JOIN pacs_worklists w ON w.visit_code LIKE 'VISIT-RTR-' || c.code || '-%'
    LEFT JOIN pacs_worklist_study_links link ON link.worklist_id = w.id
    GROUP BY c.label
    UNION ALL
    SELECT c.label || 'Histories', COUNT(history.id)::BIGINT
    FROM categories c
    LEFT JOIN pacs_worklists w ON w.visit_code LIKE 'VISIT-RTR-' || c.code || '-%'
    LEFT JOIN pacs_worklist_histories history ON history.worklist_id = w.id
    GROUP BY c.label
    UNION ALL
    SELECT c.label || 'Results', COUNT(pr.id)::BIGINT
    FROM categories c LEFT JOIN pacs_results pr ON pr.result_text LIKE 'Retention real DICOM result for RTR-' || c.code || '-%'
    GROUP BY c.label
    UNION ALL
    SELECT c.label || 'ResultImages', COUNT(img.id)::BIGINT
    FROM categories c
    LEFT JOIN pacs_results pr ON pr.result_text LIKE 'Retention real DICOM result for RTR-' || c.code || '-%'
    LEFT JOIN pacs_result_images img ON img.result_id = pr.id
    GROUP BY c.label
    UNION ALL
    SELECT c.label || 'ViewerStates', COUNT(vs.id)::BIGINT
    FROM categories c LEFT JOIN pacs_viewer_states vs ON vs.accession_number LIKE 'RTR-' || c.code || '-%'
    GROUP BY c.label
    UNION ALL
    SELECT 'deletedRequests', COUNT(1)::BIGINT FROM study_retention_delete_requests WHERE accession_number LIKE 'RTR-%' AND status = 'DELETED'
    UNION ALL
    SELECT 'failedRequests', COUNT(1)::BIGINT FROM study_retention_delete_requests WHERE accession_number LIKE 'RTR-%' AND status = 'DELETE_FAILED'
)
SELECT jsonb_object_agg(metric, value)::text FROM counts;
"@
    return (Convert-PsqlJson (Invoke-Psql $sql))
}

function Get-RealDicomStudyIds {
    param([string]$Category)
    $categoryLiteral = ConvertTo-SqlLiteral $Category
    $sql = @"
SELECT COALESCE(jsonb_agg(dicom_server_study_id ORDER BY accession_number), '[]'::jsonb)::text
FROM pacs_studies
WHERE accession_number LIKE 'RTR-' || $categoryLiteral || '-%'
  AND dicom_server_study_id IS NOT NULL
  AND BTRIM(dicom_server_study_id) != '';
"@
    return @(Convert-PsqlJson (Invoke-Psql $sql))
}

function Assert-DicomServerState {
    param([array]$StudyIds, $DicomContext, [bool]$ShouldExist, [string]$Label)
    foreach ($id in $StudyIds) {
        $status = Invoke-DicomStudyHttp -Method "GET" -StudyId ([string]$id) -DicomContext $DicomContext
        if ($ShouldExist -and $status -ne 200) {
            throw "Expected DICOM server study $id ($Label) to exist, got HTTP $status."
        }
        if (-not $ShouldExist -and $status -ne 404) {
            throw "Expected DICOM server study $id ($Label) to be deleted, got HTTP $status."
        }
    }
}

if ($Action -eq "cleanup") {
    Invoke-SmokeCleanup
    Write-Host "Real DICOM retention smoke cleanup completed."
    return
}

Write-Host "Cleaning previous real DICOM retention smoke rows and DICOM server objects..."
Invoke-SmokeCleanup

Write-Host "Preparing real DICOM retention smoke context..."
$context = New-SmokeContext
$dicomContext = [pscustomobject]@{
    baseUrl = [string]$context.dicomHostBaseUrl
    username = [string]$context.dicomUsername
    password = [string]$context.dicomPassword
}

Write-Host "Generating $Count real DICOM files..."
$plan = New-CategoryPlan
$files = New-RealDicomFiles -Plan $plan

Write-Host "Logging in and uploading real DICOM files through PACS API..."
$token = Get-AccessToken
Invoke-RealDicomUploads -Token $token -Context $context -Files $files

Write-Host "Backdating uploaded studies and attaching received worklist/result/viewer metadata..."
Update-UploadedStudiesForRetention

$expectedManual = Get-PlanCount -Plan $plan -Category "MAN"
$expectedAuto = Get-PlanCount -Plan $plan -Category "AUTO"
$expectedNear = Get-PlanCount -Plan $plan -Category "NEAR"
$expectedOpen = Get-PlanCount -Plan $plan -Category "OPEN"

$manualIds = @(Get-RealDicomStudyIds -Category "MAN")
$autoIds = @(Get-RealDicomStudyIds -Category "AUTO")
$nearIds = @(Get-RealDicomStudyIds -Category "NEAR")
$openIds = @(Get-RealDicomStudyIds -Category "OPEN")
Assert-DicomServerState -StudyIds (@($manualIds) + @($autoIds) + @($nearIds) + @($openIds)) -DicomContext $dicomContext -ShouldExist $true -Label "before retention delete"

if ($Action -eq "seed") {
    [pscustomobject]@{
        action = "seed"
        count = $Count
        hospitalKey = [string]$context.hospitalKey
        expected = @{
            expiredWaitingApproval = $expectedManual
            autoDeleteReady = $expectedAuto
            nearExpiry = $expectedNear
            open = $expectedOpen
        }
        dicomServer = @{
            uploadedStudies = ($manualIds.Count + $autoIds.Count + $nearIds.Count + $openIds.Count)
        }
        dbCounts = Get-RealDbCounts
    } | ConvertTo-Json -Depth 8
    return
}

$hospitalKey = [string]$context.hospitalKey
$manualReview = Get-Review -Token $token -HospitalKey $hospitalKey -Status "EXPIRED_WAITING_APPROVAL" -SearchText "RTR-MAN"
$autoReview = Get-Review -Token $token -HospitalKey $hospitalKey -Status "AUTO_DELETE_READY" -SearchText "RTR-AUTO"
$nearReview = Get-Review -Token $token -HospitalKey $hospitalKey -Status "NEAR_EXPIRY" -SearchText "RTR-NEAR"

if ($manualReview.Total -ne $expectedManual) {
    throw "Expected $expectedManual real expired rows, found $($manualReview.Total)."
}
if ($autoReview.Total -ne $expectedAuto) {
    throw "Expected $expectedAuto real auto-delete rows, found $($autoReview.Total)."
}
if ($nearReview.Total -ne $expectedNear) {
    throw "Expected $expectedNear real near-expiry rows, found $($nearReview.Total)."
}

$bulk = Invoke-BulkDeleteLoop -Token $token -HospitalKey $hospitalKey
$auto = Invoke-AutoDeleteLoop -Token $token -HospitalKey $hospitalKey
$afterManual = Get-Review -Token $token -HospitalKey $hospitalKey -Status "EXPIRED_WAITING_APPROVAL" -SearchText "RTR-MAN" -RowsPerPage 1
$afterAuto = Get-Review -Token $token -HospitalKey $hospitalKey -Status "AUTO_DELETE_READY" -SearchText "RTR-AUTO" -RowsPerPage 1
$afterNear = Get-Review -Token $token -HospitalKey $hospitalKey -Status "NEAR_EXPIRY" -SearchText "RTR-NEAR" -RowsPerPage 1
$dbCounts = Get-RealDbCounts

if ($bulk.Deleted -ne $expectedManual) {
    throw "Expected to bulk delete $expectedManual real DICOM rows, deleted $($bulk.Deleted)."
}
if ($auto.Deleted -ne $expectedAuto) {
    throw "Expected to auto delete $expectedAuto real DICOM rows, deleted $($auto.Deleted)."
}
if ($afterManual.Total -ne 0 -or $afterAuto.Total -ne 0) {
    throw "Expired or auto-delete real DICOM rows remain after deletion."
}
if ($afterNear.Total -ne $expectedNear) {
    throw "Near-expiry real DICOM rows changed unexpectedly. Expected $expectedNear, found $($afterNear.Total)."
}
if ([int]$dbCounts.failedRequests -ne 0) {
    throw "Real DICOM smoke deletion left failed retention delete requests."
}

$deletedRelatedKeys = @(
    "manualStudies", "manualDicomInstances", "manualWorklists", "manualLinks", "manualHistories", "manualResults", "manualResultImages", "manualViewerStates",
    "autoStudies", "autoDicomInstances", "autoWorklists", "autoLinks", "autoHistories", "autoResults", "autoResultImages", "autoViewerStates"
)
foreach ($key in $deletedRelatedKeys) {
    $value = Get-IntProperty -Object $dbCounts -Name $key
    if ($value -ne 0) {
        throw "Real DICOM cleanup left $key=$value after delete."
    }
}

$retainedExpectations = @{
    nearStudies = $expectedNear
    nearDicomInstances = $expectedNear
    nearWorklists = $expectedNear
    nearLinks = $expectedNear
    nearHistories = $expectedNear
    nearResults = $expectedNear
    nearResultImages = $expectedNear
    nearViewerStates = $expectedNear
    openStudies = $expectedOpen
    openDicomInstances = $expectedOpen
    openWorklists = $expectedOpen
    openLinks = $expectedOpen
    openHistories = $expectedOpen
    openResults = $expectedOpen
    openResultImages = $expectedOpen
    openViewerStates = $expectedOpen
}
foreach ($entry in $retainedExpectations.GetEnumerator()) {
    $actual = Get-IntProperty -Object $dbCounts -Name $entry.Key
    if ($actual -ne $entry.Value) {
        throw "Real DICOM retained-data check failed for $($entry.Key). Expected $($entry.Value), found $actual."
    }
}

Assert-DicomServerState -StudyIds (@($manualIds) + @($autoIds)) -DicomContext $dicomContext -ShouldExist $false -Label "deleted expired/auto"
Assert-DicomServerState -StudyIds (@($nearIds) + @($openIds)) -DicomContext $dicomContext -ShouldExist $true -Label "retained near/open"

[pscustomobject]@{
    action = "test"
    count = $Count
    uploadChunkSize = $UploadChunkSize
    deleteChunkSize = $DeleteChunkSize
    hospitalKey = $hospitalKey
    expected = @{
        expiredWaitingApproval = $expectedManual
        autoDeleteReady = $expectedAuto
        nearExpiry = $expectedNear
        open = $expectedOpen
    }
    upload = @{
        dicomFiles = $files.Count
        dicomServerStudiesBeforeDelete = ($manualIds.Count + $autoIds.Count + $nearIds.Count + $openIds.Count)
    }
    bulkDelete = $bulk
    autoDelete = $auto
    remaining = @{
        expiredWaitingApproval = $afterManual.Total
        autoDeleteReady = $afterAuto.Total
        nearExpiry = $afterNear.Total
        openDbRows = [int]$dbCounts.openStudies
        nearDicomServerStudies = $nearIds.Count
        openDicomServerStudies = $openIds.Count
    }
    audit = @{
        deletedRequests = [int]$dbCounts.deletedRequests
        failedRequests = [int]$dbCounts.failedRequests
    }
} | ConvertTo-Json -Depth 8
