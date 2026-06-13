param(
  [string]$BaseUrl = $env:UDAYA_PACS_API_BASE_URL,
  [string]$ClientId = $(if ($env:PACS_TEST_CLIENT_ID) { $env:PACS_TEST_CLIENT_ID } else { "pacs-web" }),
  [string]$Username = $env:PACS_TEST_USERNAME,
  [string]$Password = $env:PACS_TEST_PASSWORD,
  [string]$DicomServerBaseUrl = $env:TEST_DICOM_REST_BASE_URL,
  [string]$DicomServerUsername = $env:TEST_DICOM_SERVER_USERNAME,
  [string]$DicomServerPassword = $env:TEST_DICOM_SERVER_PASSWORD,
  [string]$DicomServerAeTitle = $(if ($env:TEST_DICOM_SERVER_AE_TITLE) { $env:TEST_DICOM_SERVER_AE_TITLE } else { "UDAYA_DICOM_SERVER" }),
  [int]$DicomServerDicomPort = $(if ($env:TEST_DICOM_SERVER_DICOM_PORT) { [int]$env:TEST_DICOM_SERVER_DICOM_PORT } else { 4242 }),
  [string]$ViewerBaseUrl = $env:TEST_VIEWER_BASE_URL
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  throw "UDAYA_PACS_API_BASE_URL or -BaseUrl is required, for example http://UDAYA_PACS_API_SERVER_IP:8080/pacsApi."
}
if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
  throw "PACS_TEST_USERNAME/PACS_TEST_PASSWORD or -Username/-Password are required."
}
if ([string]::IsNullOrWhiteSpace($DicomServerBaseUrl)) {
  throw "TEST_DICOM_REST_BASE_URL or -DicomServerBaseUrl is required for this live smoke test."
}
if ([string]::IsNullOrWhiteSpace($ViewerBaseUrl)) {
  throw "TEST_VIEWER_BASE_URL or -ViewerBaseUrl is required for this live smoke test."
}

function Invoke-Api {
  param(
    [string]$Method,
    [string]$Path,
    [object]$Body = $null,
    [hashtable]$Headers = @{},
    [string]$ContentType = "application/json",
    [switch]$AllowFailure
  )

  $url = "$BaseUrl$Path"
  $bodyJson = $null
  if ($null -ne $Body) {
    $bodyJson = if ($ContentType -eq "application/json") {
      if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
    } else {
      $Body
    }
  }

  try {
    if ($null -eq $Body) {
      return Invoke-RestMethod -Method $Method -Uri $url -Headers $Headers -TimeoutSec 30 -ErrorAction Stop
    }

    return Invoke-RestMethod -Method $Method -Uri $url -Headers $Headers -Body $bodyJson -ContentType $ContentType -TimeoutSec 30 -ErrorAction Stop
  } catch {
    $details = $_.ErrorDetails.Message
    if ([string]::IsNullOrWhiteSpace($details)) {
      $details = $_.Exception.Message
    }
    if ($AllowFailure -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
      try {
        return $_.ErrorDetails.Message | ConvertFrom-Json
      } catch {
        return [pscustomobject]@{
          success = $false
          body = @{ message = $details }
        }
      }
    }
    throw "$Method $Path failed. $details"
  }
}

function Get-FirstData {
  param([object]$Response)
  if ($null -eq $Response -or $null -eq $Response.body -or $null -eq $Response.body.data) {
    return $null
  }
  $data = $Response.body.data
  if ($data -is [System.Array]) {
    if ($data.Count -gt 0) { return $data[0] }
    return $null
  }
  return $data
}

function Get-DataList {
  param([object]$Response)
  if ($null -eq $Response -or $null -eq $Response.body -or $null -eq $Response.body.data) {
    return @()
  }
  $data = $Response.body.data
  if ($data -is [System.Array]) { return $data }
  if ($null -eq $data) { return @() }
  return @($data)
}

function Read-ApiErrorMessage {
  param([System.Management.Automation.ErrorRecord]$ErrorRecord)
  if ($null -eq $ErrorRecord -or $null -eq $ErrorRecord.Exception -or $null -eq $ErrorRecord.Exception.Response) {
    return ""
  }
  $reader = New-Object System.IO.StreamReader($ErrorRecord.Exception.Response.GetResponseStream())
  $raw = $reader.ReadToEnd()
  if ([string]::IsNullOrWhiteSpace($raw)) {
    return ""
  }
  try {
    $parsed = $raw | ConvertFrom-Json
    if ($parsed.body -and $parsed.body.message) { return [string]$parsed.body.message }
    if ($parsed.header -and $parsed.header.errorText) { return [string]$parsed.header.errorText }
  } catch {
    return $raw
  }
  return $raw
}

function Get-FirstRoutingRoute {
  param([object]$RoutingResponse)

  $rows = Get-DataList $RoutingResponse
  foreach ($row in $rows) {
    if ($row.PSObject.Properties.Name -contains "routes" -and $row.routes) {
      $nestedRoutes = @($row.routes)
      if ($nestedRoutes.Count -gt 0) {
        return $nestedRoutes[0]
      }
    }
    if ($row.PSObject.Properties.Name -contains "modalityId" -and $row.modalityId) {
      return $row
    }
  }

  return $null
}

$login = Invoke-Api -Method "POST" -Path "/auth/auth-login" -Body @{
  clientId = $ClientId
  username = $Username
  password = $Password
}

if (-not $login.success) {
  throw "Login failed."
}

$accessToken = $login.body.data[0].accessToken
$auth = @{ Authorization = "Bearer $accessToken" }

$routingResponse = Invoke-Api -Method "POST" -Path "/dicom-routing/dicom-routing-list" -Body @{
  page = 1
  rowsPerPage = 20
  searchText = ""
} -Headers $auth

 $route = Get-FirstRoutingRoute $routingResponse
if ($null -eq $route -or $null -eq $route.hospitalId -or $null -eq $route.modalityId) {
  throw "No DICOM routing found."
}

$hospitalId = [long]$route.hospitalId
$modalityId = [long]$route.modalityId

$null = Invoke-Api -Method "POST" -Path "/dicom-server/dicom-server-update" -Body @{
  id = [long]$route.dicomServerId
  hospitalId = $hospitalId
  name = if ($route.dicomServerName) { [string]$route.dicomServerName } else { "DicomServer Local" }
  ipAddress = ([Uri]$DicomServerBaseUrl).Host
  port = ([Uri]$DicomServerBaseUrl).Port
  dicomPort = $DicomServerDicomPort
  aeTitle = $DicomServerAeTitle
  baseUrl = $DicomServerBaseUrl.TrimEnd("/")
  dicomServerUiBaseUrl = $DicomServerBaseUrl.TrimEnd("/")
  dicomwebBaseUrl = "$($DicomServerBaseUrl.TrimEnd('/'))/dicom-web"
  viewerBaseUrl = $ViewerBaseUrl.TrimEnd("/")
  username = $DicomServerUsername
  password = $DicomServerPassword
  isActive = 1
} -Headers $auth


$routesResponse = Invoke-Api -Method "POST" -Path "/dicom-routing/dicom-routing-list" -Body @{
  hospitalId = $hospitalId
  modalityId = $modalityId
} -Headers $auth

$routeRows = Get-DataList $routesResponse
$routeServerIds = @()
$routeIds = @()
foreach ($routeRow in $routeRows) {
  if ($routeRow.PSObject.Properties.Name -contains "routes" -and $routeRow.routes) {
    foreach ($nested in $routeRow.routes) {
      if ($nested.dicomServerId) { $routeServerIds += [string]$nested.dicomServerId }
      if ($nested.id) { $routeIds += [string]$nested.id }
    }
  } elseif ($routeRow.dicomServerId) {
    $routeServerIds += [string]$routeRow.dicomServerId
    if ($routeRow.id) { $routeIds += [string]$routeRow.id }
  }
}
$routeServerIds = @($routeServerIds | Select-Object -Unique)
$routeIds = @($routeIds | Select-Object -Unique)
$dicomServerId = if ($routeServerIds.Count -gt 0) { [long]([string]$routeServerIds[0]) } else { $null }
$selectedRouteId = if ($routeIds.Count -gt 0) { [long]([string]$routeIds[0]) } else { $null }

$suffix = Get-Date -Format "HHmmss"
$studyDescription = "Smoke Worklist $suffix"
$scheduledDate = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$scheduledTime = "09:00"
$patientName = "Smoke Worklist Patient $suffix"

$null = Invoke-Api -Method "POST" -Path "/patient/patient-create" -Body @{
  patientName = $patientName
  gender = "M"
  phoneNumber = "0900$suffix"
  dateOfBirth = "1999-01-01"
} -Headers $auth

$patientLookup = Get-DataList (Invoke-Api -Method "POST" -Path "/dropdown/dropdown-patient" -Body @{
  page = 1
  rowsPerPage = 20
  searchText = $patientName
} -Headers $auth)

$patient = $patientLookup | Where-Object { $_.label -eq $patientName } | Select-Object -First 1
if ($null -eq $patient) {
  $patient = $patientLookup | Select-Object -First 1
}
if ($null -eq $patient) {
  throw "Failed to create or locate smoke patient."
}

$patientId = [long]$patient.value
$assign = Invoke-Api -Method "POST" -Path "/worklist/worklist-assign" -Body @{
  patientId = $patientId
  modalityId = $modalityId
  studyDescription = $studyDescription
  scheduledDate = $scheduledDate
  scheduledTime = $scheduledTime
  notes = "live smoke assign"
} -Headers $auth

$assignRow = Get-FirstData $assign
$worklistId = [long]$assignRow.worklistId

$detail1 = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{
  id = $worklistId
} -Headers $auth
$waitingUpdate = Invoke-Api -Method "POST" -Path "/worklist/worklist-update" -Body @{
  id = $worklistId
  modalityId = $modalityId
  studyDescription = "$studyDescription Draft"
  scheduledDate = $scheduledDate
  scheduledTime = "09:15"
  notes = "live smoke waiting update"
} -Headers $auth
$detailWaiting = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{
  id = $worklistId
} -Headers $auth
$send = Invoke-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Body @{
  worklistId = $worklistId
  routeId = $selectedRouteId
} -Headers $auth

if (-not $send.success) {
  throw "Send to PACS failed: $($send.body.message)"
}

$detailAfterSend = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{
  id = $worklistId
} -Headers $auth

$update = Invoke-Api -Method "POST" -Path "/worklist/worklist-update" -Body @{
  id = $worklistId
  modalityId = $modalityId
  studyDescription = "$studyDescription Updated"
  scheduledDate = (Get-Date).AddDays(2).ToString("yyyy-MM-dd")
  scheduledTime = "10:30"
} -Headers $auth -AllowFailure

$detailAfterUpdate = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{
  id = $worklistId
} -Headers $auth
$progress = Invoke-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Body @{
  worklistId = $worklistId
  routeId = $selectedRouteId
} -Headers $auth -AllowFailure
$detailAfterProgress = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{
  id = $worklistId
} -Headers $auth
$syncResult = Invoke-Api -Method "POST" -Path "/worklist/worklist-sync-result" -Body @{
  id = $worklistId
  notes = "live smoke sync result"
} -Headers $auth
$detailAfterSync = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{
  id = $worklistId
} -Headers $auth

$patientName2 = "Smoke Sent Cancel Patient $suffix"
$null = Invoke-Api -Method "POST" -Path "/patient/patient-create" -Body @{
  patientName = $patientName2
  gender = "M"
  phoneNumber = "0910$suffix"
  dateOfBirth = "1999-01-01"
} -Headers $auth

$patientLookup2 = Get-DataList (Invoke-Api -Method "POST" -Path "/dropdown/dropdown-patient" -Body @{
  page = 1
  rowsPerPage = 20
  searchText = $patientName2
} -Headers $auth)

$patient2 = $patientLookup2 | Where-Object { $_.label -eq $patientName2 } | Select-Object -First 1
if ($null -eq $patient2) {
  $patient2 = $patientLookup2 | Select-Object -First 1
}
if ($null -eq $patient2) {
  throw "Failed to create or locate cancel smoke patient."
}

$Worklist2 = Invoke-Api -Method "POST" -Path "/worklist/worklist-assign" -Body @{
  patientId = [long]$patient2.value
  modalityId = $modalityId
  studyDescription = "Smoke Waiting Cancel $suffix"
  scheduledDate = $scheduledDate
  scheduledTime = "11:00"
  notes = "live smoke waiting cancel"
} -Headers $auth

$Worklist2Id = [long](Get-FirstData $Worklist2).worklistId
$send2 = Invoke-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Body @{
  worklistId = $Worklist2Id
  routeId = $selectedRouteId
} -Headers $auth
$detailWorklist2Sent = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{
  id = $Worklist2Id
} -Headers $auth
$cancel2 = Invoke-Api -Method "POST" -Path "/worklist/worklist-cancel" -Body @{
  id = $Worklist2Id
  notes = "live smoke sent cancel"
} -Headers $auth -AllowFailure
$detail3 = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{
  id = $Worklist2Id
} -Headers $auth
$worklistId2 = (Get-FirstData $detailWorklist2Sent).dicomServerWorklistId
$dicomServerDeleteCheck = if ($worklistId2) {
  try {
    if ([string]::IsNullOrWhiteSpace($DicomServerBaseUrl)) {
      throw "TEST_DICOM_REST_BASE_URL or -DicomServerBaseUrl is required for DicomServer verification."
    }
    $dicomServerHeaders = @{}
    if (-not [string]::IsNullOrWhiteSpace($DicomServerUsername) -and -not [string]::IsNullOrWhiteSpace($DicomServerPassword)) {
      $basicAuth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $DicomServerUsername, $DicomServerPassword)))
      $dicomServerHeaders.Authorization = "Basic $basicAuth"
    }
    $response = Invoke-WebRequest -Method "GET" -Uri "$($DicomServerBaseUrl.TrimEnd('/'))/worklists/$worklistId2" -Headers $dicomServerHeaders -TimeoutSec 15 -ErrorAction Stop
    [string]$response.StatusCode
  } catch {
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      [string][int]$_.Exception.Response.StatusCode
    } else {
      ""
    }
  }
} else {
  ""
}

$patientName3 = "Smoke Waiting Cancel Patient $suffix"
$null = Invoke-Api -Method "POST" -Path "/patient/patient-create" -Body @{
  patientName = $patientName3
  gender = "M"
  phoneNumber = "0920$suffix"
  dateOfBirth = "1999-01-01"
} -Headers $auth

$patientLookup3 = Get-DataList (Invoke-Api -Method "POST" -Path "/dropdown/dropdown-patient" -Body @{
  page = 1
  rowsPerPage = 20
  searchText = $patientName3
} -Headers $auth)

$patient3 = $patientLookup3 | Where-Object { $_.label -eq $patientName3 } | Select-Object -First 1
if ($null -eq $patient3) {
  $patient3 = $patientLookup3 | Select-Object -First 1
}
if ($null -eq $patient3) {
  throw "Failed to create or locate waiting cancel smoke patient."
}

$Worklist3 = Invoke-Api -Method "POST" -Path "/worklist/worklist-assign" -Body @{
  patientId = [long]$patient3.value
  modalityId = $modalityId
  studyDescription = "Smoke Waiting Cancel $suffix"
  scheduledDate = $scheduledDate
  scheduledTime = "11:30"
  notes = "live smoke waiting cancel"
} -Headers $auth

$Worklist3Id = [long](Get-FirstData $Worklist3).worklistId
$cancel3 = Invoke-Api -Method "POST" -Path "/worklist/worklist-cancel" -Body @{
  id = $Worklist3Id
  notes = "live smoke waiting cancel"
} -Headers $auth
$detail4 = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{
  id = $Worklist3Id
} -Headers $auth

[pscustomobject]@{
  login = "ok"
  worklistId = $worklistId
  hospitalId = $hospitalId
  patientId = $patientId
  modalityId = $modalityId
  dicomServerId = $dicomServerId
  selectedRouteId = $selectedRouteId
  assignStatus = $assign.body.data[0].status
  detailBeforeSend = (Get-FirstData $detail1).status
  waitingUpdateStatus = (Get-FirstData $waitingUpdate).status
  waitingUpdatedDescription = (Get-FirstData $detailWaiting).studyDescription
  sendStatus = (Get-FirstData $send).status
  syncedAfterSendStatus = (Get-FirstData $detailAfterSend).status
  syncedWorklistId = (Get-FirstData $detailAfterSend).dicomServerWorklistId
  updateAfterSendAllowed = [bool]$update.success
  updateAfterSendMessage = if ($update.success) { "" } else { $update.header.errorText }
  updatedDescription = (Get-FirstData $detailAfterUpdate).studyDescription
  updatedSchedule = (Get-FirstData $detailAfterUpdate).scheduledDate
  secondSendAllowed = [bool]$progress.success
  secondSendMessage = if ($progress.success) { "" } else { $progress.header.errorText }
  detailAfterProgressStatus = (Get-FirstData $detailAfterProgress).status
  syncResultMessage = $syncResult.body.message
  detailAfterSyncStatus = (Get-FirstData $detailAfterSync).status
  cancelDicomServerHttpStatus = [string]$dicomServerDeleteCheck
  waitingCancelworklistId = $Worklist2Id
  sentCancelSendStatus = (Get-FirstData $send2).status
  sentCancelBeforeFinalStatus = (Get-FirstData $detailWorklist2Sent).status
  waitingCancelMessage = $cancel2.body.message
  waitingCancelFinalStatus = (Get-FirstData $detail3).status
  localWaitingCancelworklistId = $Worklist3Id
  localWaitingCancelMessage = $cancel3.body.message
  localWaitingCancelFinalStatus = (Get-FirstData $detail4).status
} | ConvertTo-Json -Depth 8

