param(
    [string]$DicomServerBaseUrl = $env:TEST_DICOM_REST_BASE_URL,
    [string]$DicomServerUsername = $env:TEST_DICOM_SERVER_USERNAME,
    [string]$DicomServerPassword = $env:TEST_DICOM_SERVER_PASSWORD,
    [string]$DicomServerUiBaseUrl = $env:TEST_DICOM_UI_BASE_URL,
    [string]$ViewerBaseUrl = $env:TEST_VIEWER_BASE_URL,
    [string]$DicomServerHost = $env:TEST_DICOM_SERVER_HOST,
    [string]$DicomServerApiBaseUrl = $env:TEST_DICOM_SERVER_API_BASE_URL,
    [string]$PacsApiCallbackBaseUrl = $env:TEST_UDAYA_PACS_API_CALLBACK_BASE_URL,
    [int]$DicomwebPort = $(if ($env:TEST_DICOMWEB_PORT) { [int]$env:TEST_DICOMWEB_PORT } else { 8042 }),
    [int]$DicomPort = $(if ($env:TEST_DICOM_PORT) { [int]$env:TEST_DICOM_PORT } else { 4242 }),
    [string]$MachineAeTitle = $(if ($env:TEST_MACHINE_AE_TITLE) { $env:TEST_MACHINE_AE_TITLE } else { "UDAYA" }),
    [string]$SeedDate = $(if ($env:TEST_SEED_DATE) { $env:TEST_SEED_DATE } else { (Get-Date).ToString("yyyy-MM-dd") }),
    [string]$PostgresContainer = $(if ($env:PACS_DB_CONTAINER) { $env:PACS_DB_CONTAINER } else { "pacs-db" }),
    [string]$DatabaseName = $(if ($env:PACS_DB_NAME) { $env:PACS_DB_NAME } else { "emr_pacs_db" }),
    [string]$DatabaseUser = $(if ($env:PACS_DB_USER) { $env:PACS_DB_USER } else { "pacs_app_local_rw" }),
    [string]$WorkingDirectory = "",
    [long]$HospitalId = 1,
    [long]$DicomServerId = 4,
    [long]$AdminUserId = 1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($DicomServerBaseUrl)) {
    throw "TEST_DICOM_REST_BASE_URL or -DicomServerBaseUrl is required for viewer seed reset."
}
if ([string]::IsNullOrWhiteSpace($DicomServerUsername) -or [string]::IsNullOrWhiteSpace($DicomServerPassword)) {
    throw "TEST_DICOM_SERVER_USERNAME and TEST_DICOM_SERVER_PASSWORD, or -DicomServerUsername and -DicomServerPassword, are required."
}
if ([string]::IsNullOrWhiteSpace($DicomServerUiBaseUrl)) {
    throw "TEST_DICOM_UI_BASE_URL or -DicomServerUiBaseUrl is required for viewer seed reset."
}
if ([string]::IsNullOrWhiteSpace($ViewerBaseUrl)) {
    throw "TEST_VIEWER_BASE_URL or -ViewerBaseUrl is required for viewer seed reset."
}
if ([string]::IsNullOrWhiteSpace($DicomServerHost)) {
    throw "TEST_DICOM_SERVER_HOST or -DicomServerHost is required for viewer seed reset."
}

if (-not $WorkingDirectory) {
    $WorkingDirectory = Join-Path $PSScriptRoot ".cache\\Worklist-study-viewer-seed"
}

$sampleBasePath = Join-Path $PSScriptRoot ".cache\\viewer-testdata\\dcm"
$generatedPath = Join-Path $WorkingDirectory "generated"
$summaryPath = Join-Path $WorkingDirectory "seed-summary.json"
$seedSpecsPath = Join-Path $WorkingDirectory "seed-specs.json"
$generatedMetaPath = Join-Path $WorkingDirectory "generated-meta.json"

function Get-BasicAuthHeader([string]$username, [string]$password) {
    $token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${username}:${password}"))
    return @{ Authorization = "Basic $token" }
}

function Invoke-DicomServerRequest {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [switch]$AllowNotFound
    )

    $headers = Get-BasicAuthHeader -username $DicomServerUsername -password $DicomServerPassword
    $uri = $DicomServerBaseUrl.TrimEnd("/") + $Path

    try {
        if ($null -eq $Body) {
            return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -TimeoutSec 60 -ErrorAction Stop
        }

        $payload = if ($Body -is [string]) {
            $Body
        } else {
            $Body | ConvertTo-Json -Depth 20 -Compress
        }

        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body $payload -ContentType "application/json" -TimeoutSec 60 -ErrorAction Stop
    } catch {
        if ($AllowNotFound -and $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404) {
            return $null
        }
        throw
    }
}

function Upload-DicomServerDicomFile([string]$filePath) {
    $uri = $DicomServerBaseUrl.TrimEnd("/") + "/instances"
    $result = & curl.exe `
        --silent `
        --show-error `
        --fail `
        --user "${DicomServerUsername}:${DicomServerPassword}" `
        --header "Content-Type: application/dicom" `
        --data-binary "@$filePath" `
        $uri

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to upload DICOM file '$filePath' to DicomServer."
    }

    return $result
}

function Invoke-Psql {
    param(
        [string]$Sql,
        [switch]$AsTable
    )

    $args = @(
        "exec",
        "-i",
        $PostgresContainer,
        "psql",
        "-v",
        "ON_ERROR_STOP=1",
        "-U",
        $DatabaseUser,
        "-d",
        $DatabaseName
    )

    if (-not $AsTable) {
        $args += @("-At", "-F", "|")
    }

    $Sql | & docker @args
}

function ConvertFrom-PsqlRows {
    param(
        [string[]]$Rows,
        [string[]]$Columns
    )

    $items = @()
    foreach ($row in $Rows) {
        if ([string]::IsNullOrWhiteSpace($row)) {
            continue
        }
        $parts = [string]$row -split "\|", $Columns.Count
        $item = [ordered]@{}
        for ($index = 0; $index -lt $Columns.Count; $index += 1) {
            $item[$Columns[$index]] = if ($index -lt $parts.Count) { $parts[$index] } else { "" }
        }
        $items += [pscustomobject]$item
    }
    return $items
}

function Escape-SqlText([string]$Value) {
    return $Value.Replace("'", "''")
}

function Convert-ToDicomDate([string]$DateValue) {
    return ([datetime]::Parse($DateValue)).ToString("yyyyMMdd")
}

function Convert-ToDicomTime([string]$TimeValue) {
    return ([datetime]::Parse("2000-01-01T$TimeValue")).ToString("HHmmss")
}

function Get-StatusCode([string]$status) {
    switch ($status.Trim().ToUpperInvariant()) {
        "WAITING" { return 1 }
        "IN_PROGRESS" { return 2 }
        "CANCELLED" { return 3 }
        "FAILED" { return 4 }
        default { throw "Unsupported Worklist status '$status'." }
    }
}

function Get-StudyStatusCode([string]$status) {
    switch ($status.Trim().ToUpperInvariant()) {
        "IMAGE_RECEIVED" { return 1 }
        "COMPLETED" { return 2 }
        default { throw "Unsupported study status '$status'." }
    }
}

function Reset-DicomServerData {
    Write-Host "Clearing DicomServer studies and worklists..."

    $worklists = Invoke-DicomServerRequest -Method Get -Path "/worklists" -AllowNotFound
    if ($worklists) {
        foreach ($worklistId in @($worklists)) {
            if (-not [string]::IsNullOrWhiteSpace([string]$worklistId)) {
                Invoke-DicomServerRequest -Method Delete -Path "/worklists/$worklistId" -AllowNotFound | Out-Null
            }
        }
    }

    $studyIds = Invoke-DicomServerRequest -Method Get -Path "/studies"
    foreach ($studyId in @($studyIds)) {
        if (-not [string]::IsNullOrWhiteSpace([string]$studyId)) {
            Invoke-DicomServerRequest -Method Delete -Path "/studies/$studyId" -AllowNotFound | Out-Null
        }
    }
}

function Reset-PacsTables {
    Write-Host "Clearing PACS Worklist/study tables..."
    $sql = @"
TRUNCATE TABLE
    pacs_result_images,
    pacs_results,
    pacs_worklist_histories,
    pacs_worklist_study_links,
    pacs_worklists,
    pacs_studies,
    pacs_visit_sequences
RESTART IDENTITY CASCADE;
"@
    Invoke-Psql -Sql $sql | Out-Null
}

function Ensure-ViewerRoutes {
    Write-Host "Ensuring active KSFH physical machines have DicomServer routes..."
    $sql = @"
INSERT INTO hospital_modality_server_routes (
    routing_config_id,
    hospital_id,
    modality_id,
    dicom_server_id,
    machine_id,
    is_active,
    created_by,
    modified_by,
    created_at,
    modified_at
)
SELECT
    config.id,
    machine.hospital_id,
    machine.modality_id,
    $DicomServerId,
    machine.id,
    1,
    $AdminUserId,
    $AdminUserId,
    NOW(),
    NOW()
FROM hospital_dicom_machines machine
JOIN hospital_dicom_routing_configs config
  ON config.hospital_id = machine.hospital_id
 AND config.is_active = 1
WHERE machine.hospital_id = $HospitalId
  AND machine.is_active = 1
  AND NOT EXISTS (
      SELECT 1
      FROM hospital_modality_server_routes route
      WHERE route.hospital_id = machine.hospital_id
        AND route.machine_id = machine.id
        AND route.is_active = 1
  );
"@
    Invoke-Psql -Sql $sql | Out-Null
}

function Ensure-DicomServerViewerRuntimeConfig {
    Write-Host "Ensuring DICOM server public viewer URLs are populated..."

    $escapedDicomServerUiBaseUrl = Escape-SqlText($DicomServerUiBaseUrl.Trim())
    $escapedViewerBaseUrl = Escape-SqlText($ViewerBaseUrl.Trim())
    $escapedDicomServerHost = Escape-SqlText($DicomServerHost.Trim())
    $apiBaseUrl = if ([string]::IsNullOrWhiteSpace($DicomServerApiBaseUrl)) {
        $DicomServerBaseUrl.TrimEnd("/")
    } else {
        $DicomServerApiBaseUrl.TrimEnd("/")
    }
    $publicDicomwebBaseUrl = $DicomServerUiBaseUrl.TrimEnd("/") + "/dicom-web"
    $escapedApiBaseUrl = Escape-SqlText($apiBaseUrl)
    $escapedPublicDicomwebBaseUrl = Escape-SqlText($publicDicomwebBaseUrl)
    $callbackBaseUrl = if ([string]::IsNullOrWhiteSpace($PacsApiCallbackBaseUrl)) { "" } else { $PacsApiCallbackBaseUrl.TrimEnd("/") }
    $escapedPacsApiCallbackBaseUrl = Escape-SqlText($callbackBaseUrl)

    $sql = @"
UPDATE hospital_dicom_servers
SET dicom_server_ui_base_url = NULLIF('$escapedDicomServerUiBaseUrl', ''),
    viewer_base_url = NULLIF('$escapedViewerBaseUrl', ''),
    base_url = NULLIF('$escapedApiBaseUrl', ''),
    dicomweb_base_url = NULLIF('$escapedPublicDicomwebBaseUrl', ''),
    pacs_api_callback_base_url = COALESCE(NULLIF('$escapedPacsApiCallbackBaseUrl', ''), pacs_api_callback_base_url),
    ip_address = '$escapedDicomServerHost',
    port = $DicomwebPort,
    dicom_port = $DicomPort,
    modified_by = $AdminUserId,
    modified_at = NOW()
WHERE hospital_id = $HospitalId
  AND id = $DicomServerId;
"@

    Invoke-Psql -Sql $sql | Out-Null
}

function Resolve-DicomServerStudyByStudyUid([string]$studyInstanceUid) {
    for ($attempt = 0; $attempt -lt 12; $attempt += 1) {
        $studyIds = Invoke-DicomServerRequest -Method Get -Path "/studies"
        foreach ($studyId in @($studyIds)) {
            $study = Invoke-DicomServerRequest -Method Get -Path "/studies/$studyId"
            $mainStudyUid = [string]($study.MainDicomTags.StudyInstanceUID)
            if ($mainStudyUid -eq $studyInstanceUid) {
                return $study
            }
        }
        Start-Sleep -Seconds 1
    }
    throw "Unable to resolve DicomServer study for StudyInstanceUID '$studyInstanceUid'."
}

function Ensure-SeedPatients([object[]]$Specs) {
    Write-Host "Ensuring seed patients exist..."
    $seedPatients = @()
    foreach ($spec in $Specs) {
        $patientUid = Escape-SqlText([string]$spec.patientUid)
        $patientName = Escape-SqlText([string]$spec.patientName)
        $gender = Escape-SqlText([string]$spec.patientSex)
        $dob = Escape-SqlText([string]$spec.patientBirthDate)
        $phone = Escape-SqlText([string]$spec.phoneNumber)

        $sql = @"
INSERT INTO patients (
    hospital_id,
    patient_uid,
    name,
    gender,
    date_of_birth,
    is_active,
    created,
    modified,
    phone_number
)
VALUES (
    $HospitalId,
    '$patientUid',
    '$patientName',
    '$gender',
    '$dob',
    1,
    NOW(),
    NOW(),
    '$phone'
)
ON CONFLICT (hospital_id, patient_uid) DO UPDATE
SET name = EXCLUDED.name,
    gender = EXCLUDED.gender,
    date_of_birth = EXCLUDED.date_of_birth,
    is_active = 1,
    modified = NOW(),
    phone_number = EXCLUDED.phone_number
RETURNING id;
"@
        $patientId = [long]((Invoke-Psql -Sql $sql | Select-Object -First 1).Trim())
        $seedPatients += [pscustomobject]@{
            key = $spec.key
            patientId = $patientId
            patientUid = $spec.patientUid
            patientName = $spec.patientName
        }
    }
    return $seedPatients
}

function Write-SeedSpecs {
    New-Item -ItemType Directory -Force -Path $WorkingDirectory | Out-Null

    $specs = @(
        [pscustomobject]@{
            key = "basic-mr"
            patientUid = "SEED-VIEW-MR-001"
            patientName = "Viewer Basic MR"
            patientSex = "F"
            patientBirthDate = "1992-03-14"
            phoneNumber = "0100000001"
            modalityId = 6
            modalityCode = "MR"
            modalityLabel = "MR"
            studyDescription = "Viewer Basic MR"
            accessionNumber = "VIEW-MR-0001"
            visitCode = "SEED-MR-0001"
            WorklistStatus = "IN_PROGRESS"
            studyStatus = "IMAGE_RECEIVED"
            scheduledDate = $SeedDate
            scheduledTime = "08:30:00"
            notes = "Seeded local MR Worklist study"
            sources = @(
                [pscustomobject]@{
                    folder = "Dummy"
                    limit = 18
                    excludeNames = @("000000-doc.dcm")
                }
            )
        }
        [pscustomobject]@{
            key = "segmentation-ct"
            patientUid = "SEED-VIEW-CT-001"
            patientName = "Viewer Segmentation CT"
            patientSex = "M"
            patientBirthDate = "1988-07-09"
            phoneNumber = "0100000002"
            modalityId = 2
            modalityCode = "CT"
            modalityLabel = "CT"
            studyDescription = "Viewer Segmentation CT"
            accessionNumber = "VIEW-CT-0002"
            visitCode = "SEED-CT-0002"
            WorklistStatus = "IN_PROGRESS"
            studyStatus = "IMAGE_RECEIVED"
            scheduledDate = $SeedDate
            scheduledTime = "09:15:00"
            notes = "Seeded local CT segmentation Worklist study"
            sources = @(
                [pscustomobject]@{
                    folder = "scoord3d-and-scoord"
                    limit = 24
                }
            )
        }
        [pscustomobject]@{
            key = "us-annotation"
            patientUid = "SEED-VIEW-US-001"
            patientName = "Viewer Ultrasound"
            patientSex = "F"
            patientBirthDate = "1995-11-02"
            phoneNumber = "0100000003"
            modalityId = 9
            modalityCode = "US"
            modalityLabel = "US"
            studyDescription = "Viewer US Annotation"
            accessionNumber = "VIEW-US-0003"
            visitCode = "SEED-US-0003"
            WorklistStatus = "IN_PROGRESS"
            studyStatus = "IMAGE_RECEIVED"
            scheduledDate = $SeedDate
            scheduledTime = "10:00:00"
            notes = "Seeded local US annotation Worklist study"
            sources = @(
                [pscustomobject]@{
                    folder = "Dummy"
                    limit = 12
                    excludeNames = @("000000-doc.dcm")
                    modalityOverride = "US"
                    seriesDescription = "US Pleura Seed"
                }
            )
        }
        [pscustomobject]@{
            key = "tmtv-petct"
            patientUid = "SEED-VIEW-TMTV-001"
            patientName = "Viewer TMTV PET CT"
            patientSex = "M"
            patientBirthDate = "1986-01-21"
            phoneNumber = "0100000004"
            modalityId = 8
            modalityCode = "PT"
            modalityLabel = "CT/PT"
            studyDescription = "Viewer TMTV PET CT"
            accessionNumber = "VIEW-TMTV-0004"
            visitCode = "SEED-TMTV-0004"
            WorklistStatus = "IN_PROGRESS"
            studyStatus = "IMAGE_RECEIVED"
            scheduledDate = $SeedDate
            scheduledTime = "11:00:00"
            notes = "Seeded local PET CT TMTV Worklist study"
            sources = @(
                [pscustomobject]@{
                    folder = "scoord3d-and-scoord"
                    limit = 24
                }
                [pscustomobject]@{
                    folder = "acrin"
                    limit = 24
                }
            )
        }
        [pscustomobject]@{
            key = "microscopy-sm"
            patientUid = "SEED-VIEW-SM-001"
            patientName = "Viewer Microscopy"
            patientSex = "F"
            patientBirthDate = "1979-04-17"
            phoneNumber = "0100000005"
            modalityId = 7
            modalityCode = "OT"
            modalityLabel = "SM"
            studyDescription = "Viewer Microscopy"
            accessionNumber = "VIEW-SM-0005"
            visitCode = "SEED-SM-0005"
            WorklistStatus = "IN_PROGRESS"
            studyStatus = "IMAGE_RECEIVED"
            scheduledDate = $SeedDate
            scheduledTime = "13:00:00"
            notes = "Seeded local microscopy Worklist study"
            sources = @(
                [pscustomobject]@{
                    folder = "sm"
                }
            )
        }
        [pscustomobject]@{
            key = "preclinical-4d"
            patientUid = "M1"
            patientName = "Viewer Preclinical 4D"
            patientSex = "M"
            patientBirthDate = "1984-09-08"
            phoneNumber = "0100000006"
            modalityId = 8
            modalityCode = "PT"
            modalityLabel = "CT/PT"
            studyDescription = "Dynamic 4D PET CT"
            accessionNumber = "VIEW-4D-0006"
            visitCode = "SEED-4D-0006"
            WorklistStatus = "IN_PROGRESS"
            studyStatus = "IMAGE_RECEIVED"
            scheduledDate = $SeedDate
            scheduledTime = "14:15:00"
            notes = "Seeded local preclinical 4D Worklist study"
            sources = @(
                [pscustomobject]@{
                    folder = "scoord3d-and-scoord"
                    limit = 20
                }
                [pscustomobject]@{
                    folder = "acrin"
                    limit = 20
                    dynamicTimePoints = 3
                    frameReferenceTimeStepMs = 60000
                    acquisitionTimeStepSeconds = 60
                    seriesDescription = "PET Dynamic 4D"
                }
            )
        }
    )

    $specs | ConvertTo-Json -Depth 10 | Set-Content -Path $seedSpecsPath -Encoding UTF8
    return $specs
}

function Build-GeneratedStudies {
    Write-Host "Generating transformed local DICOM seed studies..."
    New-Item -ItemType Directory -Force -Path $generatedPath | Out-Null

    $python = @'
import copy
import json
import sys
from pathlib import Path
import shutil
from datetime import datetime, timedelta

import pydicom
from pydicom.uid import generate_uid

spec_path = Path(sys.argv[1])
sample_base = Path(sys.argv[2])
generated_base = Path(sys.argv[3])
output_meta = Path(sys.argv[4])

specs = json.loads(spec_path.read_text(encoding="utf-8-sig"))
generated_base.mkdir(parents=True, exist_ok=True)

def as_float_list(value):
    try:
        return [float(item) for item in value]
    except Exception:
        return None

def as_int(value, fallback=0):
    try:
        return int(value)
    except Exception:
        return fallback

def load_source_metadata(path: Path):
    dataset = pydicom.dcmread(str(path), stop_before_pixels=True, force=True)
    image_position = as_float_list(getattr(dataset, "ImagePositionPatient", None))
    return {
        "path": path,
        "seriesUid": str(getattr(dataset, "SeriesInstanceUID", "")),
        "instanceNumber": as_int(getattr(dataset, "InstanceNumber", 0), 0),
        "positionZ": image_position[2] if image_position and len(image_position) >= 3 else None,
        "name": path.name,
    }

def sort_source_files(paths):
    metadata = [load_source_metadata(path) for path in paths]

    def sort_key(item):
        instance_number = item["instanceNumber"] if item["instanceNumber"] else 10**9
        position_z = item["positionZ"] if item["positionZ"] is not None else 10**9
        return (item["seriesUid"], instance_number, position_z, item["name"])

    return [item["path"] for item in sorted(metadata, key=sort_key)]

def normalize_volume_geometry(dataset, frame_of_reference_uid: str, slice_index: int):
    # Seed data can be sub-sampled from public studies. Normalize geometry so
    # the local viewer demo does not show false slice-spacing warnings.
    dataset.FrameOfReferenceUID = frame_of_reference_uid
    dataset.ImageOrientationPatient = [1, 0, 0, 0, 1, 0]
    dataset.ImagePositionPatient = [0, 0, float(slice_index)]
    dataset.SliceLocation = float(slice_index)
    dataset.SliceThickness = 1.0
    dataset.SpacingBetweenSlices = 1.0
    if not getattr(dataset, "PixelSpacing", None):
        dataset.PixelSpacing = [1.0, 1.0]

def dicom_date(value: str) -> str:
    return value.replace("-", "")

def dicom_time(value: str) -> str:
    return value.replace(":", "")

def shift_time(value: str, seconds: int) -> str:
    base = datetime.strptime(value, "%H:%M:%S")
    shifted = base + timedelta(seconds=seconds)
    return shifted.strftime("%H%M%S")

result = []

for spec in specs:
    key = spec["key"]
    out_dir = generated_base / key
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    study_uid = generate_uid()
    series_uid_map = {}
    file_count = 0
    modality_set = set()

    for source_index, source in enumerate(spec["sources"]):
        source_dir = sample_base / source["folder"]
        if not source_dir.exists():
            raise FileNotFoundError(f"Sample source folder was not found: {source_dir}")

        exclude_names = set(source.get("excludeNames", []))
        files = [
            path for path in sorted(source_dir.rglob("*"))
            if path.is_file()
            and path.name not in exclude_names
            and path.suffix.lower() not in (".txt", ".md", ".json", ".py")
        ]
        files = sort_source_files(files)

        limit = source.get("limit")
        if isinstance(limit, int) and limit > 0:
            files = files[:limit]

        dynamic_time_points = int(source.get("dynamicTimePoints", 1) or 1)
        frame_reference_step_ms = int(source.get("frameReferenceTimeStepMs", 60000) or 60000)
        acquisition_time_step_seconds = int(source.get("acquisitionTimeStepSeconds", 60) or 60)
        source_frame_of_reference_uid = generate_uid()
        source_series_slice_index = {}
        source_series_total = {}

        for path in files:
            dataset = pydicom.dcmread(str(path), stop_before_pixels=True, force=True)
            original_series_uid = str(getattr(dataset, "SeriesInstanceUID", f"SERIES-{source_index}"))
            series_key = f"{source_index}:{original_series_uid}"
            source_series_total[series_key] = source_series_total.get(series_key, 0) + 1

        for path in files:
            try:
                dataset = pydicom.dcmread(str(path), force=True)
            except Exception as exc:
                raise RuntimeError(f"Unable to read DICOM file '{path}': {exc}") from exc

            original_series_uid = str(getattr(dataset, "SeriesInstanceUID", f"SERIES-{source_index}"))
            series_key = f"{source_index}:{original_series_uid}"
            new_series_uid = series_uid_map.setdefault(series_key, generate_uid())
            slice_index = source_series_slice_index.get(series_key, 0)
            source_series_slice_index[series_key] = slice_index + 1
            series_total = source_series_total.get(series_key, len(files))

            for time_index in range(dynamic_time_points):
                next_dataset = copy.deepcopy(dataset)

                next_dataset.StudyInstanceUID = study_uid
                next_dataset.SeriesInstanceUID = new_series_uid
                next_dataset.SOPInstanceUID = generate_uid()
                if hasattr(next_dataset, "file_meta") and next_dataset.file_meta is not None:
                    next_dataset.file_meta.MediaStorageSOPInstanceUID = next_dataset.SOPInstanceUID

                next_dataset.PatientID = spec["patientUid"]
                next_dataset.PatientName = spec["patientName"]
                next_dataset.PatientSex = spec["patientSex"]
                next_dataset.PatientBirthDate = dicom_date(spec["patientBirthDate"])
                next_dataset.StudyDate = dicom_date(spec["scheduledDate"])
                next_dataset.StudyTime = dicom_time(spec["scheduledTime"])
                next_dataset.AccessionNumber = spec["accessionNumber"]
                next_dataset.StudyDescription = spec["studyDescription"]
                next_dataset.RequestedProcedureDescription = spec["studyDescription"]
                next_dataset.RequestedProcedureID = spec["accessionNumber"]
                next_dataset.StudyID = spec["accessionNumber"]
                next_dataset.PatientComments = "Seeded local viewer test data"

                modality_override = source.get("modalityOverride")
                if modality_override:
                    next_dataset.Modality = modality_override

                series_description = source.get("seriesDescription")
                if series_description:
                    next_dataset.SeriesDescription = series_description

                normalized_modality = str(getattr(next_dataset, "Modality", "")).strip().upper()
                if normalized_modality in ("CT", "MR", "PT", "NM"):
                    normalize_volume_geometry(
                        next_dataset,
                        source_frame_of_reference_uid,
                        slice_index,
                    )
                    next_dataset.InstanceNumber = (time_index * series_total) + slice_index + 1

                if dynamic_time_points > 1:
                    next_dataset.NumberOfTimeSlices = dynamic_time_points
                    next_dataset.TemporalPositionIdentifier = time_index + 1
                    next_dataset.FrameReferenceTime = str(time_index * frame_reference_step_ms)
                    next_dataset.AcquisitionTime = shift_time(
                        spec["scheduledTime"],
                        time_index * acquisition_time_step_seconds,
                    )
                    next_dataset.TriggerTime = time_index * frame_reference_step_ms

                modality_set.add(str(getattr(next_dataset, "Modality", "")).strip().upper())
                target = out_dir / f"{key}-{file_count:04d}.dcm"
                next_dataset.save_as(str(target), write_like_original=False)
                file_count += 1

    result.append({
        "key": key,
        "outputDir": str(out_dir),
        "studyInstanceUid": study_uid,
        "accessionNumber": spec["accessionNumber"],
        "studyDescription": spec["studyDescription"],
        "patientUid": spec["patientUid"],
        "patientName": spec["patientName"],
        "patientSex": spec["patientSex"],
        "patientBirthDate": spec["patientBirthDate"],
        "scheduledDate": spec["scheduledDate"],
        "scheduledTime": spec["scheduledTime"],
        "WorklistStatus": spec["WorklistStatus"],
        "studyStatus": spec["studyStatus"],
        "visitCode": spec["visitCode"],
        "modalityId": spec["modalityId"],
        "modalityCode": spec["modalityCode"],
        "modalityLabel": spec["modalityLabel"],
        "notes": spec["notes"],
        "modalities": sorted(value for value in modality_set if value),
        "fileCount": file_count,
    })

output_meta.write_text(json.dumps(result, indent=2), encoding="utf-8")
'@

    $python | python - $seedSpecsPath $sampleBasePath $generatedPath $generatedMetaPath
    return Get-Content -Path $generatedMetaPath -Raw | ConvertFrom-Json
}

function Import-DicomServerStudies([object[]]$GeneratedStudies) {
    Write-Host "Uploading seeded studies to DicomServer..."
    $importedStudies = @()
    foreach ($study in $GeneratedStudies) {
        Write-Host (" - Uploading {0} ({1} files)" -f $study.key, $study.fileCount)
        Get-ChildItem -Path $study.outputDir -Filter *.dcm | ForEach-Object {
            Upload-DicomServerDicomFile -filePath $_.FullName | Out-Null
        }

        $dicomServerStudy = Resolve-DicomServerStudyByStudyUid -studyInstanceUid $study.studyInstanceUid
        $importedStudies += [pscustomobject]@{
            key = $study.key
            outputDir = $study.outputDir
            studyInstanceUid = $study.studyInstanceUid
            accessionNumber = $study.accessionNumber
            studyDescription = $study.studyDescription
            patientUid = $study.patientUid
            patientName = $study.patientName
            patientSex = $study.patientSex
            patientBirthDate = $study.patientBirthDate
            scheduledDate = $study.scheduledDate
            scheduledTime = $study.scheduledTime
            WorklistStatus = $study.WorklistStatus
            studyStatus = $study.studyStatus
            visitCode = $study.visitCode
            modalityId = $study.modalityId
            modalityCode = $study.modalityCode
            modalityLabel = $study.modalityLabel
            notes = $study.notes
            modalities = $study.modalities
            dicomServerStudyId = [string]$dicomServerStudy.ID
            dicomServerPatientId = [string]$dicomServerStudy.ParentPatient
            dicomServerSeriesId = if ($dicomServerStudy.Series.Count -gt 0) { [string]$dicomServerStudy.Series[0] } else { "" }
            viewerUrl = "$($DicomServerBaseUrl.TrimEnd('/'))/app/explorer.html#study?uuid=$($dicomServerStudy.ID)"
            fileCount = $study.fileCount
        }
    }
    return $importedStudies
}

function New-SeedWorklistAndStudyRows {
    param(
        [object[]]$ImportedStudies,
        [object[]]$SeedPatients
    )

    Write-Host "Inserting PACS study and Worklist rows..."
    $summary = @()

    $patientByKey = @{}
    foreach ($patient in $SeedPatients) {
        $patientByKey[$patient.key] = $patient
    }

    foreach ($study in $ImportedStudies) {
        $patient = $patientByKey[[string]$study.key]
        if ($null -eq $patient) {
            throw "Seed patient was not found for key '$($study.key)'."
        }

        $WorklistStatusCode = Get-StatusCode -status ([string]$study.WorklistStatus)
        $studyStatusCode = Get-StudyStatusCode -status ([string]$study.studyStatus)
        $accessionNumber = Escape-SqlText([string]$study.accessionNumber)
        $studyDescription = Escape-SqlText([string]$study.studyDescription)
        $studyInstanceUid = Escape-SqlText([string]$study.studyInstanceUid)
        $dicomServerStudyId = Escape-SqlText([string]$study.dicomServerStudyId)
        $dicomServerPatientId = Escape-SqlText([string]$study.dicomServerPatientId)
        $dicomServerSeriesId = Escape-SqlText([string]$study.dicomServerSeriesId)
        $visitCode = Escape-SqlText([string]$study.visitCode)
        $notes = Escape-SqlText([string]$study.notes)
        $modalityLabel = Escape-SqlText([string]$study.modalityLabel)
        $scheduledDate = Escape-SqlText([string]$study.scheduledDate)
        $scheduledTime = Escape-SqlText([string]$study.scheduledTime)

        $createdAt = [datetime]"2026-05-24T08:00:00"
        switch ([string]$study.key) {
            "segmentation-ct" { $createdAt = [datetime]"2026-05-24T08:25:00" }
            "us-annotation" { $createdAt = [datetime]"2026-05-24T08:45:00" }
            "tmtv-petct" { $createdAt = [datetime]"2026-05-24T09:10:00" }
            "microscopy-sm" { $createdAt = [datetime]"2026-05-24T09:35:00" }
            "preclinical-4d" { $createdAt = [datetime]"2026-05-24T10:00:00" }
        }

        $sentAt = $createdAt.AddMinutes(15)
        $startedAt = $sentAt.AddMinutes(10)
        $imageReceivedAt = $startedAt.AddMinutes(15)
        $studyReceivedAt = if ([string]$study.WorklistStatus -eq "IN_PROGRESS") { $imageReceivedAt } else { $sentAt }

        $insertStudySql = @"
INSERT INTO pacs_studies (
    hospital_id,
    patient_id,
    study_instance_uid,
    accession_number,
    modality,
    study_date,
    study_description,
    dicom_server_id,
    status,
    is_active,
    created,
    modified,
    dicom_server_study_id,
    dicom_server_patient_id,
    dicom_server_series_id,
    received_at
)
VALUES (
    $HospitalId,
    $($patient.patientId),
    '$studyInstanceUid',
    '$accessionNumber',
    '$modalityLabel',
    '$scheduledDate',
    '$studyDescription',
    $DicomServerId,
    $studyStatusCode,
    1,
    '$($createdAt.ToString("yyyy-MM-dd HH:mm:sszzz"))',
    NOW(),
    '$dicomServerStudyId',
    '$dicomServerPatientId',
    '$dicomServerSeriesId',
    '$($studyReceivedAt.ToString("yyyy-MM-dd HH:mm:sszzz"))'
)
RETURNING id;
"@
        $studyId = [long]((Invoke-Psql -Sql $insertStudySql | Select-Object -First 1).Trim())

        $insertWorklistSql = @"
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
    sent_at,
    received_at,
    study_description,
    scheduled_date,
    scheduled_time,
    started_at,
    image_received_at,
    study_id,
    dicom_route_id
)
VALUES (
    $HospitalId,
    $($patient.patientId),
    $WorklistStatusCode,
    '$notes',
    '$($createdAt.ToString("yyyy-MM-dd HH:mm:sszzz"))',
    NOW(),
    $($study.modalityId),
    '$visitCode',
    $AdminUserId,
    $AdminUserId,
    '$($createdAt.ToString("yyyy-MM-dd HH:mm:sszzz"))',
    NOW(),
    $(if ($WorklistStatusCode -eq 2) { "'$($sentAt.ToString("yyyy-MM-dd HH:mm:sszzz"))'" } else { "NULL" }),
    $(if ($WorklistStatusCode -eq 2) { "'$($imageReceivedAt.ToString("yyyy-MM-dd HH:mm:sszzz"))'" } else { "NULL" }),
    '$studyDescription',
    '$scheduledDate',
    '$scheduledTime',
    $(if ($WorklistStatusCode -eq 2) { "'$($startedAt.ToString("yyyy-MM-dd HH:mm:sszzz"))'" } else { "NULL" }),
    $(if ($WorklistStatusCode -eq 2) { "'$($imageReceivedAt.ToString("yyyy-MM-dd HH:mm:sszzz"))'" } else { "NULL" }),
    $studyId,
    (
        SELECT route.id
        FROM hospital_modality_server_routes route
        WHERE route.hospital_id = $HospitalId
          AND route.modality_id = $($study.modalityId)
          AND route.is_active = 1
        ORDER BY route.id
        LIMIT 1
    )
)
RETURNING id;
"@
        $worklistId = [long]((Invoke-Psql -Sql $insertWorklistSql | Select-Object -First 1).Trim())

        $linkSql = @"
INSERT INTO pacs_worklist_study_links (
    hospital_id,
    worklist_id,
    study_id,
    is_primary,
    linked_at,
    created_by
)
VALUES (
    $HospitalId,
    $worklistId,
    $studyId,
    1,
    NOW(),
    $AdminUserId
);
"@
        Invoke-Psql -Sql $linkSql | Out-Null

        $historyRows = @()
        if ($WorklistStatusCode -eq 2) {
            $historyRows += [pscustomobject]@{ from = 1; to = 2; action = "Send To DicomServer"; created = $sentAt; reason = "Seeded Worklist in-progress transition" }
        } elseif ($WorklistStatusCode -eq 3) {
            $historyRows += [pscustomobject]@{ from = 1; to = 3; action = "Cancel"; created = $createdAt.AddMinutes(5); reason = "Seeded Worklist cancel transition" }
        } elseif ($WorklistStatusCode -eq 4) {
            $historyRows += [pscustomobject]@{ from = 1; to = 4; action = "Fail"; created = $createdAt.AddMinutes(5); reason = "Seeded Worklist failure transition" }
        }

        foreach ($history in $historyRows) {
            $historyReason = Escape-SqlText([string]$history.reason)
            $historyAction = Escape-SqlText([string]$history.action)
            $historySql = @"
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
VALUES (
    $HospitalId,
    $worklistId,
    $($patient.patientId),
    $($history.from),
    $($history.to),
    '$historyAction',
    '$historyReason',
    '$($history.created.ToString("yyyy-MM-dd HH:mm:sszzz"))',
    $AdminUserId
);
"@
            Invoke-Psql -Sql $historySql | Out-Null
        }

        $summary += [pscustomobject]@{
            key = $study.key
            worklistId = $worklistId
            studyId = $studyId
            patientId = $patient.patientId
            patientUid = $patient.patientUid
            patientName = $patient.patientName
            modality = $study.modalityLabel
            accessionNumber = $study.accessionNumber
            visitCode = $study.visitCode
            WorklistStatus = $study.WorklistStatus
            studyStatus = $study.studyStatus
            studyDescription = $study.studyDescription
            studyInstanceUid = $study.studyInstanceUid
            dicomServerStudyId = $study.dicomServerStudyId
            dicomServerSeriesId = $study.dicomServerSeriesId
            viewerUrl = $study.viewerUrl
            expectedViewerMode = switch ([string]$study.key) {
                "basic-mr" { "basic" }
                "segmentation-ct" { "segmentation" }
                "us-annotation" { "usAnnotation" }
                "tmtv-petct" { "tmtv" }
                "microscopy-sm" { "microscopy" }
                "preclinical-4d" { "preclinical-4d" }
                default { "basic" }
            }
        }
    }

    return $summary
}

Write-Host "Preparing local Worklist/Study viewer seed..."

if (-not (Test-Path $sampleBasePath)) {
    throw "Expected viewer test data was not found at $sampleBasePath. Restore the local viewer test-data cache before running this reset."
}

$null = Invoke-DicomServerRequest -Method Get -Path "/system"
New-Item -ItemType Directory -Force -Path $WorkingDirectory | Out-Null

$specs = Write-SeedSpecs
Reset-DicomServerData
Reset-PacsTables
Ensure-DicomServerViewerRuntimeConfig
Ensure-ViewerRoutes
$seedPatients = Ensure-SeedPatients -Specs $specs
$generatedStudies = Build-GeneratedStudies
$importedStudies = Import-DicomServerStudies -GeneratedStudies $generatedStudies
$summary = New-SeedWorklistAndStudyRows -ImportedStudies $importedStudies -SeedPatients $seedPatients

$summary | ConvertTo-Json -Depth 10 | Set-Content -Path $summaryPath -Encoding UTF8

Write-Host ""
Write-Host "Seed completed."
$summary | Format-Table key, worklistId, studyId, modality, WorklistStatus, accessionNumber, expectedViewerMode -AutoSize
Write-Host ""
Write-Host "Summary saved to $summaryPath"
