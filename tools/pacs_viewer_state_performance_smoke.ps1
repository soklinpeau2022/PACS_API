param(
    [string]$BaseUrl = $(if ($env:UDAYA_PACS_API_BASE_URL) { $env:UDAYA_PACS_API_BASE_URL } else { "http://127.0.0.1:8080/pacsApi" }),
    [string]$ClientId = $(if ($env:PACS_TEST_CLIENT_ID) { $env:PACS_TEST_CLIENT_ID } else { "pacs-web" }),
    [string]$Username = $(if ($env:PACS_TEST_USERNAME) { $env:PACS_TEST_USERNAME } else { "admin" }),
    [string]$Password = $(if ($env:PACS_TEST_PASSWORD) { $env:PACS_TEST_PASSWORD } else { "1" }),
    [ValidateRange(100, 9000)]
    [int]$TargetPayloadKb = 800,
    [ValidateRange(1, 20)]
    [int]$Iterations = 5,
    [ValidateRange(1000, 120000)]
    [int]$MaxRequestMs = 30000,
    [ValidateRange(1, 120000)]
    [int]$MaxSaveP95Ms = 1000,
    [ValidateRange(1, 120000)]
    [int]$MaxFindP95Ms = 500
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$stateType = "PERF_VIEWER_STATE_$(Get-Date -Format 'yyyyMMddHHmmss')"
$editHeaders = @{}
$scopeBody = $null
$saved = $false

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body,
        [hashtable]$Headers = @{}
    )

    $parameters = @{
        Method = $Method
        Uri = $Uri
        Headers = $Headers
        ContentType = "application/json"
        TimeoutSec = [Math]::Max(1, [Math]::Ceiling($MaxRequestMs / 1000))
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 40 -Compress
    }
    return Invoke-RestMethod @parameters
}

function Get-DataRows {
    param([object]$Response)
    if ($null -eq $Response -or $null -eq $Response.body -or $null -eq $Response.body.data) {
        return @()
    }
    return @($Response.body.data)
}

function Require-Success {
    param([object]$Response, [string]$Action)
    if ($null -eq $Response -or $Response.success -eq $false -or $Response.header.result -eq $false) {
        $message = if ($Response.header.errorText) { $Response.header.errorText } else { "$Action failed." }
        throw $message
    }
}

function Add-IfPresent {
    param([hashtable]$Target, [string]$Name, [object]$Value)
    if ($null -ne $Value -and -not [string]::IsNullOrWhiteSpace([string]$Value)) {
        $Target[$Name] = $Value
    }
}

function New-RandomText {
    param([int]$Length)
    if ($Length -le 0) {
        return ""
    }
    $bytes = New-Object byte[] ([Math]::Ceiling($Length * 0.76) + 16)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    $text = [Convert]::ToBase64String($bytes)
    return $text.Substring(0, [Math]::Min($Length, $text.Length))
}

function Percentile {
    param([double[]]$Values, [double]$Percent)
    if ($Values.Count -eq 0) {
        return 0
    }
    $ordered = @($Values | Sort-Object)
    $index = [Math]::Min($ordered.Count - 1, [Math]::Ceiling($ordered.Count * $Percent) - 1)
    return [Math]::Round($ordered[$index], 2)
}

try {
    $login = Invoke-Json -Method "POST" -Uri "$BaseUrl/auth/auth-login" -Body @{
        clientId = $ClientId
        username = $Username
        password = $Password
    }
    Require-Success $login "Login"
    $loginRows = @(Get-DataRows $login)
    if ($loginRows.Count -eq 0 -or -not $loginRows[0].accessToken) {
        throw "Login did not return an access token."
    }
    $authHeaders = @{ Authorization = "Bearer $($loginRows[0].accessToken)" }

    $studies = @(Get-DataRows (Invoke-Json -Method "POST" -Uri "$BaseUrl/study/study-list" -Body @{} -Headers $authHeaders))
    $worklists = @(Get-DataRows (Invoke-Json -Method "POST" -Uri "$BaseUrl/worklist/worklist-list" -Body @{} -Headers $authHeaders))
    $study = $studies | Where-Object { $_.worklistPublicKey -and $_.hospitalPublicKey } | Select-Object -First 1
    if ($null -ne $study) {
        $worklistKey = [string]$study.worklistPublicKey
        $hospitalKey = [string]$study.hospitalPublicKey
    } else {
        $worklist = $worklists | Where-Object {
            ($_.dicomServerStudyId -or $_.studyId) -and $_.publicKey -and $_.hospitalPublicKey
        } | Select-Object -First 1
        if ($null -eq $worklist) {
            throw "No received worklist is available for viewer-state testing."
        }
        $worklistKey = [string]$worklist.publicKey
        $hospitalKey = [string]$worklist.hospitalPublicKey
    }

    $viewerInfo = Invoke-RestMethod -Method "GET" -Uri "$BaseUrl/worklist/$worklistKey/viewer-info?hospitalKey=$hospitalKey&viewerAccess=EDIT" -Headers $authHeaders
    Require-Success $viewerInfo "Viewer info"
    $viewerRows = @(Get-DataRows $viewerInfo)
    if ($viewerRows.Count -eq 0 -or -not $viewerRows[0].viewerApiKey) {
        throw "Viewer info did not return an editing doctor token."
    }
    $viewer = $viewerRows[0]
    $editHeaders = @{ "X-PACS-VIEWER-ACCESS" = [string]$viewer.viewerApiKey }

    $scopeBody = @{
        stateType = $stateType
        schemaVersion = 2
    }
    Add-IfPresent $scopeBody "hospitalKey" $viewer.hospitalPublicKey
    Add-IfPresent $scopeBody "modalityKey" $viewer.modalityPublicKey
    Add-IfPresent $scopeBody "worklistKey" $viewer.publicKey
    Add-IfPresent $scopeBody "studyKey" $viewer.studyPublicKey
    Add-IfPresent $scopeBody "patientKey" $viewer.patientPublicKey
    Add-IfPresent $scopeBody "studyInstanceUid" $viewer.studyInstanceUid
    Add-IfPresent $scopeBody "accessionNumber" $viewer.accessionNumber
    Add-IfPresent $scopeBody "patientCode" $viewer.patientUid

    $saveBody = @{}
    foreach ($key in $scopeBody.Keys) {
        $saveBody[$key] = $scopeBody[$key]
    }
    $saveBody.viewerState = @{ source = "800kb-performance-smoke" }
    $saveBody.measurements = @(
        @{ uid = "measurement-performance"; label = "Renamed measurement"; isLocked = $true; isVisible = $true }
    )
    $saveBody.annotations = @(
        @{
            annotationUID = "annotation-performance"
            metadata = @{ toolName = "Angle" }
            data = @{ label = "Renamed measurement" }
            pacsPresentation = @{ locked = $true; visible = $true }
        }
    )
    $saveBody.segmentations = @(
        @{
            segmentationId = "segmentation-performance"
            label = "Renamed segmentation"
            representationTypes = @("Labelmap")
            segments = @{
                "1" = @{ segmentIndex = 1; label = "Renamed lesion"; locked = $true; active = $true }
            }
            representations = @(
                @{
                    viewportId = "viewport-1"
                    type = "Labelmap"
                    segments = @(
                        @{ segmentIndex = 1; label = "Renamed lesion"; locked = $true; visible = $true; color = @(12, 145, 240, 255) }
                    )
                }
            )
        }
    )
    $saveBody.labelmapSegmentations = @(
        @{
            segmentationId = "segmentation-performance"
            label = "Renamed segmentation"
            representationTypes = @("Labelmap")
            segments = @{
                "1" = @{ segmentIndex = 1; label = "Renamed lesion"; locked = $true; active = $true }
            }
            representations = @(
                @{
                    viewportId = "viewport-1"
                    type = "Labelmap"
                    segments = @(
                        @{ segmentIndex = 1; label = "Renamed lesion"; locked = $true; visible = $true; color = @(12, 145, 240, 255) }
                    )
                }
            )
            labelmap = @{ sparseLabelmap = @{ truncated = $false; totalVoxels = 2; slices = @() } }
        }
    )
    $saveBody.contourSegmentations = @()
    $saveBody.surfaceSegmentations = @()
    $saveBody.additionalFindings = @()
    $saveBody.presentationState = @{ activeViewportId = "viewport-1" }
    $saveBody.toolState = @{ activeTool = "Angle" }
    $saveBody.metadata = @{ test = "large-viewer-state"; performancePayload = "" }

    $targetBytes = $TargetPayloadKb * 1024
    $baseBytes = [System.Text.Encoding]::UTF8.GetByteCount(($saveBody | ConvertTo-Json -Depth 40 -Compress))
    $saveBody.metadata.performancePayload = New-RandomText ([Math]::Max(0, $targetBytes - $baseBytes))
    $actualBytes = [System.Text.Encoding]::UTF8.GetByteCount(($saveBody | ConvertTo-Json -Depth 40 -Compress))

    $saveTimes = New-Object System.Collections.Generic.List[double]
    $findTimes = New-Object System.Collections.Generic.List[double]
    for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
        $saveBody.metadata.iteration = $iteration
        $saveWatch = [Diagnostics.Stopwatch]::StartNew()
        $saveResponse = Invoke-Json -Method "POST" -Uri "$BaseUrl/pacs-result-api/pacs-result-viewer-state-save" -Body $saveBody -Headers $editHeaders
        $saveWatch.Stop()
        Require-Success $saveResponse "Large viewer-state save"
        $saveTimes.Add($saveWatch.Elapsed.TotalMilliseconds)
        $saved = $true

        $findWatch = [Diagnostics.Stopwatch]::StartNew()
        $findResponse = Invoke-Json -Method "POST" -Uri "$BaseUrl/pacs-result-api/pacs-result-viewer-state-find" -Body $scopeBody -Headers $editHeaders
        $findWatch.Stop()
        Require-Success $findResponse "Large viewer-state find"
        $findTimes.Add($findWatch.Elapsed.TotalMilliseconds)

        $foundRows = @(Get-DataRows $findResponse)
        if ($foundRows.Count -ne 1) {
            throw "Expected one saved viewer-state row, found $($foundRows.Count)."
        }
        $found = $foundRows[0]
        $segment = $found.labelmapSegmentations[0].segments.'1'
        $presentation = $found.labelmapSegmentations[0].representations[0].segments[0]
        if ($segment.label -ne "Renamed lesion" -or $segment.locked -ne $true) {
            throw "Renamed and locked segment state did not round trip."
        }
        if (($presentation.color -join ",") -ne "12,145,240,255") {
            throw "Segment color did not round trip."
        }
        if ([long]$found.payloadSizeBytes -lt ($targetBytes * 0.95)) {
            throw "Stored payload accounting is smaller than expected."
        }
    }

    $readViewerInfo = Invoke-RestMethod -Method "GET" -Uri "$BaseUrl/worklist/$worklistKey/viewer-info?hospitalKey=$hospitalKey&viewerAccess=READ" -Headers $authHeaders
    Require-Success $readViewerInfo "Read-only viewer info"
    $readRows = @(Get-DataRows $readViewerInfo)
    if ($readRows.Count -eq 0 -or -not $readRows[0].viewerApiKey) {
        throw "Viewer info did not return a read-only token."
    }
    $readDeleteRejected = $false
    $readDeleteStatus = 0
    try {
        $readDelete = Invoke-Json -Method "POST" -Uri "$BaseUrl/pacs-result-api/pacs-result-viewer-state-delete" -Body $scopeBody -Headers @{
            "X-PACS-VIEWER-ACCESS" = [string]$readRows[0].viewerApiKey
        }
        if ($null -ne $readDelete.header.statusCode) {
            $readDeleteStatus = [int]$readDelete.header.statusCode
        }
        $readDeleteRejected = $readDeleteStatus -eq 403
    } catch {
        if ($null -ne $_.Exception.Response -and $null -ne $_.Exception.Response.StatusCode) {
            $readDeleteStatus = [int]$_.Exception.Response.StatusCode
        }
        $readDeleteRejected = $readDeleteStatus -eq 403
    }
    if (-not $readDeleteRejected) {
        throw "Read-only viewer delete must return 403, received status $readDeleteStatus."
    }

    $deleteResponse = Invoke-Json -Method "POST" -Uri "$BaseUrl/pacs-result-api/pacs-result-viewer-state-delete" -Body $scopeBody -Headers $editHeaders
    Require-Success $deleteResponse "Doctor viewer-state delete"
    $saved = $false
    $afterDelete = Invoke-Json -Method "POST" -Uri "$BaseUrl/pacs-result-api/pacs-result-viewer-state-find" -Body $scopeBody -Headers $editHeaders
    if (@(Get-DataRows $afterDelete).Count -ne 0) {
        throw "Deleted viewer state is still visible."
    }

    $p95SaveMs = Percentile -Values $saveTimes.ToArray() -Percent 0.95
    $p95FindMs = Percentile -Values $findTimes.ToArray() -Percent 0.95
    if ($p95SaveMs -gt $MaxSaveP95Ms) {
        throw "Save P95 $p95SaveMs ms exceeds the $MaxSaveP95Ms ms performance limit."
    }
    if ($p95FindMs -gt $MaxFindP95Ms) {
        throw "Find P95 $p95FindMs ms exceeds the $MaxFindP95Ms ms performance limit."
    }

    [pscustomobject]@{
        Status = "PASS"
        TargetPayloadKb = $TargetPayloadKb
        ActualRequestKb = [Math]::Round($actualBytes / 1024, 2)
        Iterations = $Iterations
        AverageSaveMs = [Math]::Round(($saveTimes | Measure-Object -Average).Average, 2)
        P95SaveMs = $p95SaveMs
        MaxSaveP95Ms = $MaxSaveP95Ms
        AverageFindMs = [Math]::Round(($findTimes | Measure-Object -Average).Average, 2)
        P95FindMs = $p95FindMs
        MaxFindP95Ms = $MaxFindP95Ms
        ReadOnlyDeleteRejected = $readDeleteRejected
        DoctorDeletePassed = $true
        PostDeleteRows = 0
    } | Format-List
} finally {
    if ($saved -and $null -ne $scopeBody -and $editHeaders.Count -gt 0) {
        try {
            $null = Invoke-Json -Method "POST" -Uri "$BaseUrl/pacs-result-api/pacs-result-viewer-state-delete" -Body $scopeBody -Headers $editHeaders
        } catch {
            Write-Warning "Cleanup failed for state type $stateType."
        }
    }
}
