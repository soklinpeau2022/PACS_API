param(
    [string]$ApiBaseUrl = $env:SIM_API_BASE_URL,
    [string]$ViewerBaseUrl = $env:SIM_VIEWER_BASE_URL,
    [string]$DicomServerRestBaseUrl = $env:SIM_DICOM_REST_BASE_URL,
    [string]$DicomServerUsername = $env:SIM_DICOM_SERVER_USERNAME,
    [string]$DicomServerPassword = $env:SIM_DICOM_SERVER_PASSWORD,
    [string]$ModalityRegistrationHost = $env:SIM_MODALITY_REGISTRATION_HOST,
    [int]$ModalityRegistrationPort = $(if ($env:SIM_MODALITY_REGISTRATION_PORT) { [int]$env:SIM_MODALITY_REGISTRATION_PORT } else { 104 }),
    [string]$ModalityRegistrationKey = $(if ($env:SIM_MODALITY_REGISTRATION_KEY) { $env:SIM_MODALITY_REGISTRATION_KEY } else { "modalitysim" }),
    [string]$ClientId = $(if ($env:SIM_CLIENT_ID) { $env:SIM_CLIENT_ID } else { "pacs-web" }),
    [string]$Username = $env:SIM_USERNAME,
    [string]$Password = $env:SIM_PASSWORD,
    [string]$AccessToken = $env:SIM_ACCESS_TOKEN,
    [string]$DicomHost = $env:SIM_DICOM_HOST,
    [int]$DicomPort = $(if ($env:SIM_DICOM_PORT) { [int]$env:SIM_DICOM_PORT } else { 0 }),
    [string]$CalledAe = $(if ($env:SIM_CALLED_AE) { $env:SIM_CALLED_AE } else { "UDAYA" }),
    [string]$CallingAe = $(if ($env:SIM_CALLING_AE) { $env:SIM_CALLING_AE } else { "UDAYA" }),
    [int]$HospitalId = $(if ($env:SIM_HOSPITAL_ID) { [int]$env:SIM_HOSPITAL_ID } else { 0 }),
    [string]$HospitalName = $(if ($env:SIM_HOSPITAL_NAME) { $env:SIM_HOSPITAL_NAME } else { "" }),
    [int]$RouteId = $(if ($env:SIM_ROUTE_ID) { [int]$env:SIM_ROUTE_ID } else { 0 }),
    [string]$ModalityCode = $(if ($env:SIM_MODALITY_CODE) { $env:SIM_MODALITY_CODE } else { "CT" }),
    [string]$ScheduledDate = $env:SIM_SCHEDULED_DATE,
    [string]$ScheduledTime = $(if ($env:SIM_SCHEDULED_TIME) { $env:SIM_SCHEDULED_TIME } else { "10:00" }),
    [int]$Instances = $(if ($env:SIM_INSTANCES) { [int]$env:SIM_INSTANCES } else { 3 }),
    [string]$SourceDicomUrl = $env:SIM_SOURCE_DICOM_URL,
    [string]$SourceDicomFile = $env:SIM_SOURCE_DICOM_FILE,
    [switch]$SkipModalityRegistration
)

$ErrorActionPreference = "Stop"

$missing = @()
if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) { $missing += "SIM_API_BASE_URL or -ApiBaseUrl" }
if ([string]::IsNullOrWhiteSpace($DicomServerUsername)) { $missing += "SIM_DICOM_SERVER_USERNAME or -DicomServerUsername" }
if ([string]::IsNullOrWhiteSpace($DicomServerPassword)) { $missing += "SIM_DICOM_SERVER_PASSWORD or -DicomServerPassword" }
if ([string]::IsNullOrWhiteSpace($ModalityRegistrationHost)) { $missing += "SIM_MODALITY_REGISTRATION_HOST or -ModalityRegistrationHost" }
if ([string]::IsNullOrWhiteSpace($AccessToken)) {
    if ([string]::IsNullOrWhiteSpace($Username)) { $missing += "SIM_USERNAME or -Username" }
    if ([string]::IsNullOrWhiteSpace($Password)) { $missing += "SIM_PASSWORD or -Password" }
}
if ($missing.Count -gt 0) {
    throw "Missing required split-server config: $($missing -join ', ')."
}
$toolRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$venvPython = Join-Path $toolRoot ".venv\Scripts\python.exe"

if (-not (Test-Path $venvPython)) {
    throw "Virtual environment not found. Run '.\.venv\Scripts\pip install -r requirements.txt' first."
}

$args = @(
    (Join-Path $toolRoot "modality_simulator.py"),
    "--api-base-url", $ApiBaseUrl,
    "--viewer-base-url", $ViewerBaseUrl,
    "--dicom_server_username", $DicomServerUsername,
    "--dicom_server_password", $DicomServerPassword,
    "--modality-registration-host", $ModalityRegistrationHost,
    "--modality-registration-port", $ModalityRegistrationPort,
    "--modality-registration-key", $ModalityRegistrationKey,
    "--client-id", $ClientId,
    "--called-ae", $CalledAe,
    "--calling-ae", $CallingAe,
    "--hospital-id", $HospitalId,
    "--route-id", $RouteId,
    "--modality-code", $ModalityCode,
    "--scheduled-time", $ScheduledTime,
    "--instances", $Instances
)

if (-not [string]::IsNullOrWhiteSpace($HospitalName)) {
    $args += @("--hospital-name", $HospitalName)
}

if (-not [string]::IsNullOrWhiteSpace($ViewerBaseUrl)) {
    $args += @("--viewer-base-url", $ViewerBaseUrl)
}

if (-not [string]::IsNullOrWhiteSpace($ScheduledDate)) {
    $args += @("--scheduled-date", $ScheduledDate)
}

if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
    $args += @("--access-token", $AccessToken)
}

if (-not [string]::IsNullOrWhiteSpace($Username)) {
    $args += @("--username", $Username)
}

if (-not [string]::IsNullOrWhiteSpace($Password)) {
    $args += @("--password", $Password)
}

if (-not [string]::IsNullOrWhiteSpace($DicomServerRestBaseUrl)) {
    $args += @("--dicom_server_rest-base-url", $DicomServerRestBaseUrl)
}

if (-not [string]::IsNullOrWhiteSpace($DicomHost)) {
    $args += @("--dicom-host", $DicomHost)
}

if ($DicomPort -gt 0) {
    $args += @("--dicom-port", $DicomPort)
}

if (-not [string]::IsNullOrWhiteSpace($SourceDicomUrl)) {
    $args += @("--source-dicom-url", $SourceDicomUrl)
}

if (-not [string]::IsNullOrWhiteSpace($SourceDicomFile)) {
    $args += @("--source-dicom-file", $SourceDicomFile)
}

if ($SkipModalityRegistration) {
    $args += @("--skip-modality-registration")
}

& $venvPython @args
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
