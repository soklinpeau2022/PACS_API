param(
  [string]$BaseUrl = $env:UDAYA_PACS_API_BASE_URL,
  [string]$ClientId = $(if ($env:PACS_TEST_CLIENT_ID) { $env:PACS_TEST_CLIENT_ID } else { "pacs-web" }),
  [string]$Username = $env:PACS_TEST_USERNAME,
  [string]$Password = $env:PACS_TEST_PASSWORD,
  [string]$DicomServerCallbackSecret = $env:TEST_DICOM_SERVER_CALLBACK_SECRET,
  [string]$DicomServerBaseUrl = $env:TEST_DICOM_REST_BASE_URL,
  [string]$DicomServerUsername = $env:TEST_DICOM_SERVER_USERNAME,
  [string]$DicomServerPassword = $env:TEST_DICOM_SERVER_PASSWORD,
  [string]$InvalidDicomServerPassword = $(if ($env:TEST_DICOM_SERVER_INVALID_PASSWORD) { $env:TEST_DICOM_SERVER_INVALID_PASSWORD } else { "invalid_for_negative_test" }),
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
  throw "TEST_DICOM_REST_BASE_URL or -DicomServerBaseUrl is required for this live status test."
}
if ([string]::IsNullOrWhiteSpace($ViewerBaseUrl)) {
  throw "TEST_VIEWER_BASE_URL or -ViewerBaseUrl is required for this live status test."
}
if ([string]::IsNullOrWhiteSpace($DicomServerCallbackSecret)) {
  throw "TEST_DICOM_SERVER_CALLBACK_SECRET or -DicomServerCallbackSecret is required."
}

function Invoke-Api {
  param(
    [string]$Method,
    [string]$Path,
    [object]$Body = $null,
    [hashtable]$Headers = @{},
    [string]$ContentType = "application/json"
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
    $raw = $_.ErrorDetails.Message
    if (-not [string]::IsNullOrWhiteSpace($raw)) {
      try {
        return $raw | ConvertFrom-Json
      } catch {
        throw $_
      }
    }
    if ($_.Exception.Response) {
      $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      $raw = $reader.ReadToEnd()
      if (-not [string]::IsNullOrWhiteSpace($raw)) {
        try {
          return $raw | ConvertFrom-Json
        } catch {
          throw $_
        }
      }
    }
    throw $_
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

function Assert-ApiSuccess {
  param(
    [object]$Response,
    [string]$Message
  )
  if ($null -eq $Response -or -not $Response.success) {
    $errorMessage = if ($Response -and $Response.body -and $Response.body.message) {
      [string]$Response.body.message
    } elseif ($Response -and $Response.header -and $Response.header.errorText) {
      [string]$Response.header.errorText
    } else {
      "Unknown API failure"
    }
    throw "${Message}: $errorMessage"
  }
}

function Get-ResponseMessage {
  param([object]$Response)
  if ($null -eq $Response) {
    return ""
  }
  if ($Response.body -and $Response.body.message) {
    return [string]$Response.body.message
  }
  if ($Response.header -and $Response.header.errorText) {
    return [string]$Response.header.errorText
  }
  return ""
}

function Ensure-DicomServerServer {
  param(
    [long]$HospitalId,
    [long]$DicomServerId,
    [hashtable]$Headers,
    [string]$PasswordForServer = $DicomServerPassword
  )

  $dicomServerUri = [Uri]$DicomServerBaseUrl
  $response = Invoke-Api -Method "POST" -Path "/dicom-server/dicom-server-update" -Body @{
    id = $DicomServerId
    hospitalId = $HospitalId
    name = "DicomServer Split Server"
    ipAddress = $dicomServerUri.Host
    port = $dicomServerUri.Port
    dicomPort = $DicomServerDicomPort
    aeTitle = $DicomServerAeTitle
    baseUrl = $DicomServerBaseUrl.TrimEnd("/")
    dicomServerUiBaseUrl = $DicomServerBaseUrl.TrimEnd("/")
    dicomwebBaseUrl = "$($DicomServerBaseUrl.TrimEnd('/'))/dicom-web"
    viewerBaseUrl = $ViewerBaseUrl.TrimEnd("/")
    username = $DicomServerUsername
    password = $PasswordForServer
    isActive = 1
  } -Headers $Headers
  Assert-ApiSuccess $response "DICOM server update failed"
}

function Find-PatientId {
  param(
    [string]$PatientName,
    [hashtable]$Headers
  )
  $patientLookup = Get-DataList (Invoke-Api -Method "POST" -Path "/dropdown/dropdown-patient" -Body @{
    page = 1
    rowsPerPage = 20
    searchText = $PatientName
  } -Headers $Headers)

  $patient = $patientLookup | Where-Object { $_.label -eq $PatientName } | Select-Object -First 1
  if ($null -eq $patient) {
    $patient = $patientLookup | Select-Object -First 1
  }
  if ($null -eq $patient) {
    throw "Failed to locate patient '$PatientName'."
  }
  return [long]$patient.value
}

function Create-Patient {
  param(
    [string]$PatientName,
    [string]$PhoneNumber,
    [hashtable]$Headers
  )
  $null = Invoke-Api -Method "POST" -Path "/patient/patient-create" -Body @{
    patientName = $PatientName
    gender = "M"
    phoneNumber = $PhoneNumber
    dateOfBirth = "1999-01-01"
  } -Headers $Headers

  return Find-PatientId -PatientName $PatientName -Headers $Headers
}

function Get-WorklistHttpStatus {
  param([string]$WorklistId)
  if ([string]::IsNullOrWhiteSpace($WorklistId)) {
    return ""
  }
  try {
    if ([string]::IsNullOrWhiteSpace($DicomServerBaseUrl)) {
      throw "TEST_DICOM_REST_BASE_URL or -DicomServerBaseUrl is required for DicomServer verification."
    }
    $dicomServerHeaders = @{}
    if (-not [string]::IsNullOrWhiteSpace($DicomServerUsername) -and -not [string]::IsNullOrWhiteSpace($DicomServerPassword)) {
      $basicAuth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $DicomServerUsername, $DicomServerPassword)))
      $dicomServerHeaders.Authorization = "Basic $basicAuth"
    }
    $response = Invoke-WebRequest -Method "GET" -Uri "$($DicomServerBaseUrl.TrimEnd('/'))/worklists/$WorklistId" -Headers $dicomServerHeaders -TimeoutSec 15 -ErrorAction Stop
    return [string]$response.StatusCode
  } catch {
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      return [string][int]$_.Exception.Response.StatusCode
    }
    return ""
  }
}

$login = Invoke-Api -Method "POST" -Path "/auth/auth-login" -Body @{
  clientId = $ClientId
  username = $Username
  password = $Password
}
Assert-ApiSuccess $login "Login failed"

$accessToken = $login.body.data[0].accessToken
$auth = @{ Authorization = "Bearer $accessToken" }

$routingResponse = Invoke-Api -Method "POST" -Path "/dicom-routing/dicom-routing-list" -Body @{
  page = 1
  rowsPerPage = 20
  searchText = ""
} -Headers $auth
Assert-ApiSuccess $routingResponse "Unable to load DICOM routing"

$route = Get-FirstData $routingResponse
if ($null -ne $route -and $route.PSObject.Properties.Name -contains "routes" -and $route.routes) {
  $nestedRoutes = @($route.routes)
  if ($nestedRoutes.Count -gt 0) {
    $route = $nestedRoutes[0]
  }
}
if ($null -eq $route -or $null -eq $route.hospitalId -or $null -eq $route.modalityId -or $null -eq $route.dicomServerId) {
  throw "No active DICOM routing found."
}

$hospitalId = [long]$route.hospitalId
$modalityId = [long]$route.modalityId
$dicomServerId = [long]$route.dicomServerId
$selectedRouteId = [long]$route.id

Ensure-DicomServerServer -HospitalId $hospitalId -DicomServerId $dicomServerId -Headers $auth


$suffix = Get-Date -Format "HHmmss"
$scheduledDate = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")

# Worklist A: full main status path and blocked edits/cancel after progression
$patientAName = "Status Matrix Patient $suffix"
$patientAId = Create-Patient -PatientName $patientAName -PhoneNumber "0930$suffix" -Headers $auth
$assignA = Invoke-Api -Method "POST" -Path "/worklist/worklist-assign" -Body @{
  patientId = $patientAId
  modalityId = $modalityId
  studyDescription = "Matrix Main $suffix"
  scheduledDate = $scheduledDate
  scheduledTime = "08:30"
  notes = "matrix waiting"
} -Headers $auth
Assert-ApiSuccess $assignA "Worklist A assign failed"
$WorklistAId = [long](Get-FirstData $assignA).worklistId

$sendA = Invoke-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Body @{ worklistId = $WorklistAId; routeId = $selectedRouteId } -Headers $auth
Assert-ApiSuccess $sendA "Worklist A send failed"
$detailASent = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{ id = $WorklistAId } -Headers $auth
Assert-ApiSuccess $detailASent "Worklist A detail after send failed"
$visitCodeA = (Get-FirstData $detailASent).visitCode
$worklistIdA = (Get-FirstData $detailASent).dicomServerWorklistId

$progressA = Invoke-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Body @{ worklistId = $WorklistAId; routeId = $selectedRouteId } -Headers $auth
Assert-ApiSuccess $progressA "Worklist A progress send failed"

$cancelWhileInProgress = Invoke-Api -Method "POST" -Path "/worklist/worklist-cancel" -Body @{ id = $WorklistAId; notes = "matrix cancel blocked" } -Headers $auth
$updateWhileInProgress = Invoke-Api -Method "POST" -Path "/worklist/worklist-update" -Body @{
  id = $WorklistAId
  modalityId = $modalityId
  studyDescription = "Should Block In Progress"
  scheduledDate = $scheduledDate
  scheduledTime = "08:45"
} -Headers $auth

$receivedA = Invoke-Api -Method "POST" -Path "/worklist/worklist-received-study" -Body @{
  visitCode = $visitCodeA
  studyInstanceUid = "1.2.840.113619.$suffix.$WorklistAId"
} -Headers @{ "X-DicomServer-Secret" = $DicomServerCallbackSecret }
Assert-ApiSuccess $receivedA "Worklist A received-study failed"
$detailAReceived = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{ id = $WorklistAId } -Headers $auth
Assert-ApiSuccess $detailAReceived "Worklist A detail after received failed"

$cancelWhileImageReceived = Invoke-Api -Method "POST" -Path "/worklist/worklist-cancel" -Body @{ id = $WorklistAId; notes = "matrix cancel image blocked" } -Headers $auth
$updateWhileImageReceived = Invoke-Api -Method "POST" -Path "/worklist/worklist-update" -Body @{
  id = $WorklistAId
  modalityId = $modalityId
  studyDescription = "Should Block Image Received"
  scheduledDate = $scheduledDate
  scheduledTime = "09:00"
} -Headers $auth

# Worklist B: cancel from IN_PROGRESS should delete DicomServer worklist
$patientBName = "Status Matrix Sent Cancel $suffix"
$patientBId = Create-Patient -PatientName $patientBName -PhoneNumber "0940$suffix" -Headers $auth
$assignB = Invoke-Api -Method "POST" -Path "/worklist/worklist-assign" -Body @{
  patientId = $patientBId
  modalityId = $modalityId
  studyDescription = "Matrix Sent Cancel $suffix"
  scheduledDate = $scheduledDate
  scheduledTime = "10:00"
} -Headers $auth
Assert-ApiSuccess $assignB "Worklist B assign failed"
$WorklistBId = [long](Get-FirstData $assignB).worklistId
$sendB = Invoke-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Body @{ worklistId = $WorklistBId; routeId = $selectedRouteId } -Headers $auth
Assert-ApiSuccess $sendB "Worklist B send failed"
$detailBSent = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{ id = $WorklistBId } -Headers $auth
Assert-ApiSuccess $detailBSent "Worklist B detail failed"
$worklistIdB = (Get-FirstData $detailBSent).dicomServerWorklistId
$cancelB = Invoke-Api -Method "POST" -Path "/worklist/worklist-cancel" -Body @{ id = $WorklistBId; notes = "matrix cancel sent" } -Headers $auth
Assert-ApiSuccess $cancelB "Worklist B cancel failed"
$detailBCancelled = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{ id = $WorklistBId } -Headers $auth
Assert-ApiSuccess $detailBCancelled "Worklist B cancelled detail failed"
$worklistBStatus = Get-WorklistHttpStatus -WorklistId $worklistIdB

# Worklist C: cancel from WAITING should stay EMR-only
$patientCName = "Status Matrix Waiting Cancel $suffix"
$patientCId = Create-Patient -PatientName $patientCName -PhoneNumber "0950$suffix" -Headers $auth
$assignC = Invoke-Api -Method "POST" -Path "/worklist/worklist-assign" -Body @{
  patientId = $patientCId
  modalityId = $modalityId
  studyDescription = "Matrix Waiting Cancel $suffix"
  scheduledDate = $scheduledDate
  scheduledTime = "10:30"
} -Headers $auth
Assert-ApiSuccess $assignC "Worklist C assign failed"
$WorklistCId = [long](Get-FirstData $assignC).worklistId
$cancelC = Invoke-Api -Method "POST" -Path "/worklist/worklist-cancel" -Body @{ id = $WorklistCId; notes = "matrix cancel waiting" } -Headers $auth
Assert-ApiSuccess $cancelC "Worklist C cancel failed"
$detailCCancelled = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{ id = $WorklistCId } -Headers $auth
Assert-ApiSuccess $detailCCancelled "Worklist C cancelled detail failed"

# Worklist D: force FAILED then fix and retry send-to-pacs
Ensure-DicomServerServer -HospitalId $hospitalId -DicomServerId $dicomServerId -Headers $auth -PasswordForServer $InvalidDicomServerPassword
$patientDName = "Status Matrix Failed Retry $suffix"
$patientDId = Create-Patient -PatientName $patientDName -PhoneNumber "0960$suffix" -Headers $auth
$assignD = Invoke-Api -Method "POST" -Path "/worklist/worklist-assign" -Body @{
  patientId = $patientDId
  modalityId = $modalityId
  studyDescription = "Matrix Failed Retry $suffix"
  scheduledDate = $scheduledDate
  scheduledTime = "11:00"
} -Headers $auth
Assert-ApiSuccess $assignD "Worklist D assign failed"
$WorklistDId = [long](Get-FirstData $assignD).worklistId
$sendD = Invoke-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Body @{ worklistId = $WorklistDId; routeId = $selectedRouteId } -Headers $auth
$detailDFailed = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{ id = $WorklistDId } -Headers $auth

$updateD = Invoke-Api -Method "POST" -Path "/worklist/worklist-update" -Body @{
  id = $WorklistDId
  modalityId = $modalityId
  studyDescription = "Matrix Failed Retry Updated $suffix"
  scheduledDate = $scheduledDate
  scheduledTime = "11:15"
  notes = "fix failed Worklist"
} -Headers $auth

Ensure-DicomServerServer -HospitalId $hospitalId -DicomServerId $dicomServerId -Headers $auth
$retrySendD = Invoke-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Body @{ worklistId = $WorklistDId; routeId = $selectedRouteId } -Headers $auth
Assert-ApiSuccess $retrySendD "Worklist D retry send failed"
$detailDRetried = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{ id = $WorklistDId } -Headers $auth
Assert-ApiSuccess $detailDRetried "Worklist D detail after retry failed"

[pscustomobject]@{
  login = "ok"
  WorklistA = [pscustomobject]@{
    worklistId = $WorklistAId
    waitToSend = (Get-FirstData $sendA).status
    inProgressStatus = (Get-FirstData $progressA).status
    cancelWhileInProgressSuccess = [bool]$cancelWhileInProgress.success
    cancelWhileInProgressMessage = Get-ResponseMessage $cancelWhileInProgress
    updateWhileInProgressSuccess = [bool]$updateWhileInProgress.success
    updateWhileInProgressMessage = Get-ResponseMessage $updateWhileInProgress
    receivedStatus = (Get-FirstData $receivedA).status
    detailAfterReceivedStatus = (Get-FirstData $detailAReceived).status
    cancelWhileImageReceivedSuccess = [bool]$cancelWhileImageReceived.success
    cancelWhileImageReceivedMessage = Get-ResponseMessage $cancelWhileImageReceived
    updateWhileImageReceivedSuccess = [bool]$updateWhileImageReceived.success
    updateWhileImageReceivedMessage = Get-ResponseMessage $updateWhileImageReceived
    finalStatus = (Get-FirstData $detailAReceived).status
    dicomServerWorklistId = $worklistIdA
  }
  WorklistB = [pscustomobject]@{
    worklistId = $WorklistBId
    sentStatus = (Get-FirstData $sendB).status
    cancelSuccess = [bool]$cancelB.success
    finalStatus = (Get-FirstData $detailBCancelled).status
    dicomServerWorklistHttpStatus = $worklistBStatus
  }
  WorklistC = [pscustomobject]@{
    worklistId = $WorklistCId
    cancelSuccess = [bool]$cancelC.success
    finalStatus = (Get-FirstData $detailCCancelled).status
  }
  WorklistD = [pscustomobject]@{
    worklistId = $WorklistDId
    failedSendSuccess = [bool]$sendD.success
    failedSendMessage = Get-ResponseMessage $sendD
    failedStatus = (Get-FirstData $detailDFailed).status
    failedErrorMessage = (Get-FirstData $detailDFailed).errorMessage
    failedUpdateSuccess = [bool]$updateD.success
    retrySendSuccess = [bool]$retrySendD.success
    retrySendStatus = (Get-FirstData $retrySendD).status
    retriedFinalStatus = (Get-FirstData $detailDRetried).status
  }
} | ConvertTo-Json -Depth 8
