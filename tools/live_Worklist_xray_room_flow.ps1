param(
  [string]$BaseUrl = $(if ($env:UDAYA_PACS_API_BASE_URL) { $env:UDAYA_PACS_API_BASE_URL } else { "http://localhost:8080/pacsApi" }),
  [string]$AdminClientId = $(if ($env:PACS_TEST_CLIENT_ID) { $env:PACS_TEST_CLIENT_ID } else { "pacs-web" }),
  [string]$AdminUsername = $env:PACS_TEST_USERNAME,
  [string]$AdminPassword = $env:PACS_TEST_PASSWORD,
  [string]$CallbackClientId = $env:PACS_ADAPTER_CLIENT_ID,
  [string]$CallbackClientSecret = $env:PACS_ADAPTER_CLIENT_SECRET,
  [string]$DicomServerCallbackContainer = "dicom_server_ksfh",
  [string]$DbContainer = $(if ($env:PACS_DB_CONTAINER) { $env:PACS_DB_CONTAINER } else { "udaya_pacs_api_postgres_1" }),
  [string]$DbUser = $(if ($env:PACS_DB_USER) { $env:PACS_DB_USER } else { "pacs_app_local_rw" }),
  [string]$DbName = $(if ($env:PACS_DB_NAME) { $env:PACS_DB_NAME } else { "emr_pacs_db" }),
  [string]$XrayModalityCode = "DX",
  [int]$ExpectedXrayRouteCount = 4
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminUsername) -or [string]::IsNullOrWhiteSpace($AdminPassword)) {
  throw "PACS_TEST_USERNAME/PACS_TEST_PASSWORD or -AdminUsername/-AdminPassword are required."
}

if ([string]::IsNullOrWhiteSpace($CallbackClientId)) {
  $CallbackClientId = (docker exec $DicomServerCallbackContainer printenv UDAYA_DICOM_SERVER_CALLBACK_CLIENT_ID 2>$null | Out-String).Trim()
}
if ([string]::IsNullOrWhiteSpace($CallbackClientSecret)) {
  $CallbackClientSecret = (docker exec $DicomServerCallbackContainer printenv UDAYA_DICOM_SERVER_CALLBACK_CLIENT_SECRET 2>$null | Out-String).Trim()
}
if ([string]::IsNullOrWhiteSpace($CallbackClientId) -or [string]::IsNullOrWhiteSpace($CallbackClientSecret)) {
  throw "Machine callback client credentials are required. Provide PACS_ADAPTER_CLIENT_ID/PACS_ADAPTER_CLIENT_SECRET or a running DicomServer callback container."
}

$results = New-Object System.Collections.Generic.List[object]

function Add-Result {
  param(
    [string]$Step,
    [string]$Status,
    [string]$Detail
  )
  $results.Add([pscustomobject]@{
    step = $Step
    status = $Status
    detail = $Detail
  })
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
  try {
    if ($null -eq $Body) {
      return Invoke-RestMethod -Method $Method -Uri $url -Headers $Headers -TimeoutSec 60 -ErrorAction Stop
    }

    $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
    return Invoke-RestMethod -Method $Method -Uri $url -Headers $Headers -Body $json -ContentType $ContentType -TimeoutSec 60 -ErrorAction Stop
  } catch {
    $raw = $_.ErrorDetails.Message
    if ([string]::IsNullOrWhiteSpace($raw) -and $_.Exception.Response) {
      $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      $raw = $reader.ReadToEnd()
      $reader.Close()
    }
    if (-not [string]::IsNullOrWhiteSpace($raw)) {
      try { return $raw | ConvertFrom-Json } catch {}
    }
    throw $_
  }
}

function Get-DataList {
  param([object]$Response)
  if ($null -eq $Response -or $null -eq $Response.body -or $null -eq $Response.body.data) {
    return @()
  }
  $data = $Response.body.data
  if ($data -is [System.Array]) {
    return $data
  }
  return @($data)
}

function Get-FirstData {
  param([object]$Response)
  $items = @(Get-DataList $Response)
  if ($items.Count -gt 0) {
    return $items[0]
  }
  return $null
}

function Get-ApiMessage {
  param([object]$Response)
  if ($Response -and $Response.body -and $Response.body.message) {
    return [string]$Response.body.message
  }
  if ($Response -and $Response.header -and $Response.header.errorText) {
    return [string]$Response.header.errorText
  }
  return ""
}

function Assert-ApiSuccess {
  param(
    [object]$Response,
    [string]$Message
  )
  if ($null -eq $Response -or -not $Response.success) {
    $apiMessage = Get-ApiMessage $Response
    if ([string]::IsNullOrWhiteSpace($apiMessage)) {
      $apiMessage = "Unknown API failure"
    }
    throw "${Message}: $apiMessage"
  }
}

function Invoke-SqlScalar {
  param([string]$Sql)
  $value = $Sql | docker exec -i $DbContainer psql -U $DbUser -d $DbName -t -A
  if ($null -eq $value) {
    return ""
  }
  return ($value | Out-String).Trim()
}

function Get-PatientIdByName {
  param(
    [string]$PatientName,
    [hashtable]$Headers
  )

  $patientList = Invoke-Api -Method "POST" -Path "/patient/patient-list" -Body @{
    page = 1
    rowsPerPage = 20
    patientName = $PatientName
  } -Headers $Headers
  Assert-ApiSuccess $patientList "Patient list failed"

  $patient = @(Get-DataList $patientList) |
    Where-Object { $_.patientName -eq $PatientName -or $_.name -eq $PatientName } |
    Select-Object -First 1
  if ($null -eq $patient) {
    throw "Unable to find created patient '$PatientName'."
  }
  return [long]$patient.id
}

function New-SmokePatient {
  param(
    [string]$PatientName,
    [string]$PhoneNumber,
    [hashtable]$Headers
  )

  $createPatient = Invoke-Api -Method "POST" -Path "/patient/patient-create" -Body @{
    patientName = $PatientName
    gender = "M"
    phoneNumber = $PhoneNumber
    dateOfBirth = "1990-01-01"
  } -Headers $Headers
  Assert-ApiSuccess $createPatient "Patient create failed"
  return Get-PatientIdByName -PatientName $PatientName -Headers $Headers
}

function New-XrayWorklist {
  param(
    [long]$PatientId,
    [long]$ModalityId,
    [string]$Description,
    [hashtable]$Headers
  )

  $assign = Invoke-Api -Method "POST" -Path "/worklist/worklist-assign" -Body @{
    patientId = $PatientId
    modalityId = $ModalityId
    studyDescription = $Description
    scheduledDate = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
    scheduledTime = "08:30"
    notes = "live xray route smoke"
  } -Headers $Headers
  Assert-ApiSuccess $assign "Worklist assign failed"

  $created = Get-FirstData $assign
  if ($null -eq $created -or $null -eq $created.worklistId) {
    throw "Worklist assign did not return worklistId."
  }
  return [long]$created.worklistId
}

function Get-WorklistDetail {
  param(
    [long]$WorklistId,
    [hashtable]$Headers
  )

  $detail = Invoke-Api -Method "POST" -Path "/worklist/worklist-find" -Body @{ id = $WorklistId } -Headers $Headers
  Assert-ApiSuccess $detail "Worklist find failed"
  $row = Get-FirstData $detail
  if ($null -eq $row) {
    throw "Worklist detail is empty for #$WorklistId."
  }
  return $row
}

function New-DicomUid {
  param([long]$RouteId)
  $stamp = Get-Date -Format "yyyyMMddHHmmssfff"
  $random = Get-Random -Minimum 100000 -Maximum 999999
  return "1.2.826.0.1.3680043.8.498.$stamp.$RouteId.$random"
}

Write-Host "1) Authenticate admin and callback machine client..."
$login = Invoke-Api -Method "POST" -Path "/auth/auth-login" -Body @{
  clientId = $AdminClientId
  username = $AdminUsername
  password = $AdminPassword
}
Assert-ApiSuccess $login "Admin login failed"
$accessToken = $login.body.data[0].accessToken
$authHeaders = @{
  Authorization = "Bearer $accessToken"
  "Content-Type" = "application/json"
}

$clientTokenResponse = Invoke-Api -Method "POST" -Path "/auth/auth-client-credentials" -Body @{
  clientId = $CallbackClientId
  clientSecret = $CallbackClientSecret
  scope = "pacs.api"
}
Assert-ApiSuccess $clientTokenResponse "Machine client credentials failed"
$callbackToken = $clientTokenResponse.body.data[0].accessToken
$callbackHeaders = @{
  Authorization = "Bearer $callbackToken"
  "Content-Type" = "application/json"
}
Add-Result "Auth" "PASS" "Admin user token and machine-client callback token accepted."

Write-Host "2) Find active X-Ray modality..."
$modalityDropdown = Invoke-Api -Method "POST" -Path "/dropdown/dropdown-modality" -Body @{
  page = 1
  rowsPerPage = 50
  searchText = ""
} -Headers $authHeaders
Assert-ApiSuccess $modalityDropdown "Modality dropdown failed"
$xray = @(Get-DataList $modalityDropdown) |
  Where-Object {
    $label = [string]$_.label
    $code = [string]$_.code
    $label.ToUpper().Contains($XrayModalityCode.ToUpper()) -or
      $label.ToUpper().Contains("X-RAY") -or
      $label.ToUpper().Contains("XRAY") -or
      $code.ToUpper() -eq $XrayModalityCode.ToUpper()
  } |
  Select-Object -First 1
if ($null -eq $xray) {
  $xray = @(Get-DataList $modalityDropdown) | Select-Object -First 1
}
if ($null -eq $xray -or $null -eq $xray.value) {
  throw "Active X-Ray modality '$XrayModalityCode' was not found."
}
$modalityId = [long]$xray.value
Add-Result "X-Ray modality" "PASS" "Using modalityId=$modalityId."

$runStamp = Get-Date -Format "yyyyMMddHHmmss"
$firstPatientName = "Xray Room Flow $runStamp Seed"
$firstPatientId = New-SmokePatient -PatientName $firstPatientName -PhoneNumber "090$($runStamp.Substring($runStamp.Length - 7))" -Headers $authHeaders
$firstWorklistId = New-XrayWorklist -PatientId $firstPatientId -ModalityId $modalityId -Description "X-Ray route seed $runStamp" -Headers $authHeaders
$firstDetail = Get-WorklistDetail -WorklistId $firstWorklistId -Headers $authHeaders
if ([string]$firstDetail.status -ne "WAITING") {
  throw "New Worklist #$firstWorklistId should be WAITING but was '$($firstDetail.status)'."
}
Add-Result "Assign waiting" "PASS" "Worklist #$firstWorklistId created as WAITING."

Write-Host "3) Load X-Ray room choices and verify no-room-selected behavior..."
$routeResponse = Invoke-Api -Method "POST" -Path "/worklist/worklist-machine-routes" -Body @{
  worklistId = $firstWorklistId
} -Headers $authHeaders
Assert-ApiSuccess $routeResponse "Machine route dropdown failed"
$routes = @(Get-DataList $routeResponse) |
  Where-Object { $_.id -gt 0 -and -not [string]::IsNullOrWhiteSpace([string]$_.machineName) } |
  Sort-Object machineName

if ($routes.Count -ne $ExpectedXrayRouteCount) {
  throw "Expected $ExpectedXrayRouteCount active X-Ray machine routes but found $($routes.Count)."
}
Add-Result "X-Ray room dropdown" "PASS" ("Routes: " + (($routes | ForEach-Object { "$($_.id):$($_.machineName)" }) -join ", "))

$sendWithoutRoute = Invoke-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Body @{
  worklistId = $firstWorklistId
} -Headers $authHeaders
if ($sendWithoutRoute.success) {
  throw "Send without selected route unexpectedly succeeded even though multiple X-Ray rooms exist."
}
$noRouteMessage = Get-ApiMessage $sendWithoutRoute
if ($noRouteMessage -notmatch "Multiple active machine routes") {
  throw "Send without selected route failed with an unexpected message: $noRouteMessage"
}
Add-Result "No room selected" "PASS" $noRouteMessage

$routeSummaries = New-Object System.Collections.Generic.List[object]
$pendingWorklists = New-Object System.Collections.Generic.List[long]
$allRoutes = @($routes)

Write-Host "4) Send one Worklist to every X-Ray room and simulate image callback..."
for ($i = 0; $i -lt $allRoutes.Count; $i++) {
  $route = $allRoutes[$i]
  if ($i -eq 0) {
    $worklistId = $firstWorklistId
    $patientName = $firstPatientName
  } else {
    $patientName = "Xray Room Flow $runStamp R$($i + 1)"
    $patientId = New-SmokePatient -PatientName $patientName -PhoneNumber "091$($runStamp.Substring($runStamp.Length - 7))$i" -Headers $authHeaders
    $worklistId = New-XrayWorklist -PatientId $patientId -ModalityId $modalityId -Description "X-Ray route $($i + 1) $runStamp" -Headers $authHeaders
  }
  $pendingWorklists.Add($worklistId) | Out-Null

  $send = Invoke-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Body @{
    worklistId = $worklistId
    routeId = [long]$route.id
  } -Headers $authHeaders
  Assert-ApiSuccess $send "Send to PACS failed for route $($route.id)"

  $afterSend = Get-WorklistDetail -WorklistId $worklistId -Headers $authHeaders
  if ([string]$afterSend.status -ne "IN_PROGRESS") {
    throw "Worklist #$worklistId should be IN_PROGRESS after send but was '$($afterSend.status)'."
  }
  if ($null -ne $afterSend.dicomRouteId -and [long]$afterSend.dicomRouteId -ne [long]$route.id) {
    throw "Worklist #$worklistId stored route '$($afterSend.dicomRouteId)' but selected route '$($route.id)'."
  }
  if ([string]::IsNullOrWhiteSpace([string]$afterSend.dicomServerWorklistId)) {
    throw "Worklist #$worklistId did not store an DicomServer worklist id after send."
  }

  $accessionNumber = [string]$afterSend.accessionNumber
  if ([string]::IsNullOrWhiteSpace($accessionNumber) -or $accessionNumber -eq "-") {
    $accessionNumber = [string]$afterSend.visitCode
  }
  $studyUid = New-DicomUid -RouteId ([long]$route.id)
  $dicomServerStudyId = "dicom_server_smoke-$runStamp-$($route.id)"
  $callback = Invoke-Api -Method "POST" -Path "/worklist/worklist-received-study" -Body @{
    event = "STUDY_RECEIVED"
    status = "IN_PROGRESS"
    accessionNumber = $accessionNumber
    visitCode = [string]$afterSend.visitCode
    dicomServerStudyId = $dicomServerStudyId
    dicomServerPatientId = "dicom_server_patient-$runStamp-$($route.id)"
    dicomServerSeriesIds = @("dicom_server_series-$runStamp-$($route.id)")
    studyInstanceUid = $studyUid
    patientId = "SMOKE-$runStamp-$($route.id)"
    patientName = $patientName
    patientSex = "M"
    studyDescription = "X-Ray image received $($route.machineName)"
    studyDate = (Get-Date).ToString("yyyyMMdd")
    studyTime = (Get-Date).ToString("HHmmss")
  } -Headers $callbackHeaders
  Assert-ApiSuccess $callback "Image received callback failed for route $($route.id)"

  if ($i -eq 0) {
    $callbackAgain = Invoke-Api -Method "POST" -Path "/worklist/worklist-received-study" -Body @{
      event = "STUDY_RECEIVED"
      status = "IN_PROGRESS"
      accessionNumber = $accessionNumber
      visitCode = [string]$afterSend.visitCode
      dicomServerStudyId = $dicomServerStudyId
      dicomServerPatientId = "dicom_server_patient-$runStamp-$($route.id)"
      dicomServerSeriesIds = @("dicom_server_series-$runStamp-$($route.id)")
      studyInstanceUid = $studyUid
      patientId = "SMOKE-$runStamp-$($route.id)"
      patientName = $patientName
      patientSex = "M"
      studyDescription = "X-Ray duplicate callback idempotency"
      studyDate = (Get-Date).ToString("yyyyMMdd")
      studyTime = (Get-Date).ToString("HHmmss")
    } -Headers $callbackHeaders
    Assert-ApiSuccess $callbackAgain "Duplicate image callback failed"
  }

  $afterCallback = Get-WorklistDetail -WorklistId $worklistId -Headers $authHeaders
  if ([string]$afterCallback.status -ne "IN_PROGRESS") {
    throw "Worklist #$worklistId should remain operational IN_PROGRESS after image callback but was '$($afterCallback.status)'."
  }
  if ([string]::IsNullOrWhiteSpace([string]$afterCallback.imageReceivedAt) -or [string]$afterCallback.imageReceivedAt -eq "-") {
    throw "Worklist #$worklistId did not get imageReceivedAt after callback."
  }
  if ([string]$afterCallback.studyInstanceUid -ne $studyUid) {
    throw "Worklist #$worklistId stored unexpected studyInstanceUid '$($afterCallback.studyInstanceUid)'."
  }

  $worklistList = Invoke-Api -Method "POST" -Path "/worklist/worklist-list" -Body @{
    page = 1
    rowsPerPage = 20
    visitCode = [string]$afterSend.visitCode
  } -Headers $authHeaders
  Assert-ApiSuccess $worklistList "Worklist list after image callback failed"
  $activeRows = @(Get-DataList $worklistList)
  if ($activeRows.Count -gt 0) {
    throw "Worklist #$worklistId still appears in active Worklist list after image callback."
  }

  $studyList = Invoke-Api -Method "POST" -Path "/study/study-list" -Body @{
    page = 1
    rowsPerPage = 20
    accessionNumberExact = $accessionNumber
    status = "IMAGE_RECEIVED"
  } -Headers $authHeaders
  Assert-ApiSuccess $studyList "Study list after image callback failed"
  $studyRows = @(Get-DataList $studyList)
  if ($studyRows.Count -lt 1) {
    throw "Study Archive did not return IMAGE_RECEIVED study for accession '$accessionNumber'."
  }
  $studyStatus = [string]$studyRows[0].status
  if ($studyStatus -ne "IMAGE_RECEIVED") {
    throw "Study Archive status should be IMAGE_RECEIVED but was '$studyStatus'."
  }

  $uidSql = $studyUid.Replace("'", "''")
  $dbStudyCount = Invoke-SqlScalar "SELECT COUNT(*) FROM pacs_studies WHERE study_instance_uid = '$uidSql';"
  if ([int]$dbStudyCount -ne 1) {
    throw "Expected one pacs_studies row for UID $studyUid but found $dbStudyCount."
  }

  $routeSummaries.Add([pscustomobject]@{
    worklistId = $worklistId
    routeId = [long]$route.id
    machine = [string]$route.machineName
    room = [string]$route.roomName
    visitCode = [string]$afterSend.visitCode
    accessionNumber = $accessionNumber
    dicomServerWorklistId = [string]$afterSend.dicomServerWorklistId
    studyStatus = $studyStatus
  }) | Out-Null
}

Add-Result "Selected X-Ray rooms" "PASS" "Sent and received image callback for $($routeSummaries.Count) room routes."

$pendingCsv = ($pendingWorklists | ForEach-Object { [string]$_ }) -join ","
$receivedCount = Invoke-SqlScalar "SELECT COUNT(*) FROM pacs_worklists WHERE id IN ($pendingCsv) AND status = 2 AND study_id IS NOT NULL AND image_received_at IS NOT NULL;"
if ([int]$receivedCount -ne $pendingWorklists.Count) {
  throw "Expected $($pendingWorklists.Count) received worklists in DB but found $receivedCount."
}
Add-Result "DB received link" "PASS" "$receivedCount Worklists are linked to studies with image_received_at."

Write-Host ""
Write-Host "X-Ray room flow summary"
$routeSummaries | Format-Table -AutoSize
Write-Host ""
$results | Format-Table -AutoSize
