param(
    [string]$BaseUrl = $(if ($env:UDAYA_PACS_API_BASE_URL) { $env:UDAYA_PACS_API_BASE_URL } else { "http://127.0.0.1:8080/pacsApi" }),
    [string]$ClientId = $(if ($env:PACS_TEST_CLIENT_ID) { $env:PACS_TEST_CLIENT_ID } else { "pacs-web" }),
    [string]$Username = $(if ($env:PACS_TEST_USERNAME) { $env:PACS_TEST_USERNAME } else { "admin" }),
    [string]$Password = $(if ($env:PACS_TEST_PASSWORD) { $env:PACS_TEST_PASSWORD } else { "1" }),
    [switch]$SkipSafeMutations,
    [switch]$IncludeNoCleanupMutations
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$BaseUrl = $BaseUrl.TrimEnd("/")
$Session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$Headers = @{}
$AccessToken = $null
$RefreshToken = $null
$Results = New-Object System.Collections.Generic.List[object]
$OpenApiOperations = @{}
$Stamp = Get-Date -Format "yyyyMMddHHmmss"

function Add-Result {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Name,
        [string]$Status,
        [string]$Category,
        [string]$Detail = "",
        [double]$ElapsedMs = 0
    )

    $Results.Add([pscustomobject]@{
        status = $Status
        category = $Category
        method = $Method.ToUpperInvariant()
        path = $Path
        name = $Name
        elapsedMs = [math]::Round($ElapsedMs, 2)
        detail = $Detail
    }) | Out-Null
}

function Get-ErrorDetail {
    param([System.Management.Automation.ErrorRecord]$ErrorRecord)

    if ($null -ne $ErrorRecord.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($ErrorRecord.ErrorDetails.Message)) {
        return $ErrorRecord.ErrorDetails.Message
    }

    try {
        if ($ErrorRecord.Exception -and $ErrorRecord.Exception.Response) {
            $stream = $ErrorRecord.Exception.Response.GetResponseStream()
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                $raw = $reader.ReadToEnd()
                if (-not [string]::IsNullOrWhiteSpace($raw)) {
                    return $raw
                }
            }
        }
    } catch {
        return $ErrorRecord.Exception.Message
    }

    return $ErrorRecord.Exception.Message
}

function Get-ResponseMessage {
    param([object]$Response)

    if ($null -eq $Response) { return "" }
    if ($Response.PSObject.Properties.Name -contains "body" -and $Response.body) {
        if ($Response.body.PSObject.Properties.Name -contains "message") {
            return [string]$Response.body.message
        }
    }
    if ($Response.PSObject.Properties.Name -contains "header" -and $Response.header) {
        if ($Response.header.PSObject.Properties.Name -contains "errorText") {
            return [string]$Response.header.errorText
        }
    }
    if ($Response.PSObject.Properties.Name -contains "message") {
        return [string]$Response.message
    }
    return ""
}

function Get-DataList {
    param([object]$Response)

    if ($null -eq $Response -or -not ($Response.PSObject.Properties.Name -contains "body") -or $null -eq $Response.body) {
        return @()
    }
    if (-not ($Response.body.PSObject.Properties.Name -contains "data") -or $null -eq $Response.body.data) {
        return @()
    }
    $data = $Response.body.data
    if ($data -is [System.Array]) {
        return @($data)
    }
    return @($data)
}

function Get-FirstData {
    param([object]$Response)
    $rows = @(Get-DataList $Response)
    if ($rows.Count -gt 0) {
        return $rows[0]
    }
    return $null
}

function Find-First {
    param(
        [object[]]$Rows,
        [string]$Property,
        [object]$Value
    )

    foreach ($row in @($Rows)) {
        if ($null -ne $row -and ($row.PSObject.Properties.Name -contains $Property) -and [string]$row.$Property -eq [string]$Value) {
            return $row
        }
    }
    return $null
}

function Get-PropValue {
    param(
        [object]$Row,
        [string[]]$Names
    )

    if ($null -eq $Row) { return $null }
    foreach ($name in $Names) {
        if ($Row.PSObject.Properties.Name -contains $name) {
            $value = $Row.$name
            if ($null -ne $value -and -not [string]::IsNullOrWhiteSpace([string]$value)) {
                return $value
            }
        }
    }
    return $null
}

function Get-EntityKey {
    param([object]$Row)
    return [string](Get-PropValue -Row $Row -Names @("publicKey", "public_key", "key", "uuid", "id", "value"))
}

function Get-EntityLong {
    param(
        [object]$Row,
        [string[]]$Names = @("id", "value")
    )

    $value = Get-PropValue -Row $Row -Names $Names
    if ($null -eq $value) { return $null }
    $parsed = 0L
    if ([long]::TryParse([string]$value, [ref]$parsed)) {
        return $parsed
    }
    return $null
}

function Add-BodyValue {
    param(
        [hashtable]$Body,
        [string]$Name,
        [object]$Value
    )

    if ($null -ne $Value -and -not [string]::IsNullOrWhiteSpace([string]$Value)) {
        $Body[$Name] = $Value
    }
}

function Invoke-ApiRaw {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$ExtraHeaders = @{}
    )

    $url = "$BaseUrl$Path"
    $requestHeaders = @{}
    foreach ($key in $Headers.Keys) {
        $requestHeaders[$key] = $Headers[$key]
    }
    foreach ($key in $ExtraHeaders.Keys) {
        $requestHeaders[$key] = $ExtraHeaders[$key]
    }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        if ($null -eq $Body) {
            $response = Invoke-RestMethod -Method $Method -Uri $url -Headers $requestHeaders -WebSession $Session -TimeoutSec 45
        } else {
            $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 30 -Compress }
            $response = Invoke-RestMethod -Method $Method -Uri $url -Headers $requestHeaders -WebSession $Session -ContentType "application/json" -Body $json -TimeoutSec 45
        }
        $sw.Stop()
        return [pscustomobject]@{
            ok = $true
            response = $response
            detail = Get-ResponseMessage $response
            elapsedMs = $sw.Elapsed.TotalMilliseconds
        }
    } catch {
        $sw.Stop()
        return [pscustomobject]@{
            ok = $false
            response = $null
            detail = Get-ErrorDetail $_
            elapsedMs = $sw.Elapsed.TotalMilliseconds
        }
    }
}

function Test-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Name,
        [object]$Body = $null,
        [string]$Category = "read",
        [switch]$AllowBusinessFalse,
        [hashtable]$ExtraHeaders = @{}
    )

    $result = Invoke-ApiRaw -Method $Method -Path $Path -Body $Body -ExtraHeaders $ExtraHeaders
    $status = "PASS"
    $detail = $result.detail
    if (-not $result.ok) {
        $status = "FAIL"
    } elseif (-not $AllowBusinessFalse -and $null -ne $result.response -and ($result.response.PSObject.Properties.Name -contains "success") -and $result.response.success -eq $false) {
        $status = "FAIL"
        if ([string]::IsNullOrWhiteSpace($detail)) {
            $detail = "Business response success=false"
        }
    }
    Add-Result -Method $Method -Path $Path -Name $Name -Status $status -Category $Category -Detail $detail -ElapsedMs $result.elapsedMs
    return $result.response
}

function Skip-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Name,
        [string]$Reason,
        [string]$Category = "skip"
    )
    Add-Result -Method $Method -Path $Path -Name $Name -Status "SKIP" -Category $Category -Detail $Reason -ElapsedMs 0
}

function Test-FileUploadRoundTrip {
    param()

    $path = "/file/file-upload"
    $tmpFile = Join-Path $env:TEMP ("udaya-api-smoke-$Stamp.png")
    $pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    [System.IO.File]::WriteAllBytes($tmpFile, [Convert]::FromBase64String($pngBase64))

    $client = New-Object System.Net.Http.HttpClient
    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $AccessToken)
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $content = New-Object System.Net.Http.MultipartFormDataContent
        $bytes = [System.IO.File]::ReadAllBytes($tmpFile)
        $fileContent = New-Object System.Net.Http.ByteArrayContent -ArgumentList (, $bytes)
        $fileContent.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("image/png")
        $content.Add($fileContent, "file", [System.IO.Path]::GetFileName($tmpFile))
        $httpResponse = $client.PostAsync("$BaseUrl$path", $content).Result
        $raw = $httpResponse.Content.ReadAsStringAsync().Result
        $sw.Stop()
        $parsed = $null
        try { $parsed = $raw | ConvertFrom-Json } catch { $parsed = $null }
        $ok = $httpResponse.IsSuccessStatusCode -and $null -ne $parsed -and $parsed.success
        Add-Result -Method "POST" -Path $path -Name "Upload file" -Status $(if ($ok) { "PASS" } else { "FAIL" }) -Category "safe-mutation" -Detail $(if ($ok) { Get-ResponseMessage $parsed } else { $raw }) -ElapsedMs $sw.Elapsed.TotalMilliseconds

        if ($ok) {
            $storedPath = Get-ResponseMessage $parsed
            $filename = Split-Path -Leaf $storedPath
            $null = Test-Api -Method "GET" -Path "/file/file-upload/$filename" -Name "Get uploaded file" -Category "read"
            $encoded = [System.Uri]::EscapeDataString($storedPath)
            $null = Test-Api -Method "DELETE" -Path "/file/file-delete?path=$encoded" -Name "Delete uploaded file" -Category "safe-mutation"
        } else {
            Skip-Api -Method "GET" -Path "/file/file-upload/{filename}" -Name "Get uploaded file" -Reason "Upload failed; no file to read."
            Skip-Api -Method "DELETE" -Path "/file/file-delete" -Name "Delete uploaded file" -Reason "Upload failed; no file to delete."
        }
    } catch {
        $sw.Stop()
        Add-Result -Method "POST" -Path $path -Name "Upload file" -Status "FAIL" -Category "safe-mutation" -Detail $_.Exception.Message -ElapsedMs $sw.Elapsed.TotalMilliseconds
        Skip-Api -Method "GET" -Path "/file/file-upload/{filename}" -Name "Get uploaded file" -Reason "Upload failed; no file to read."
        Skip-Api -Method "DELETE" -Path "/file/file-delete" -Name "Delete uploaded file" -Reason "Upload failed; no file to delete."
    } finally {
        $client.Dispose()
        Remove-Item -LiteralPath $tmpFile -Force -ErrorAction SilentlyContinue
    }
}

function Get-OpenApiOperations {
    try {
        $request = @{
            Method = "Get"
            Uri = "$BaseUrl/api-docs"
            TimeoutSec = 45
        }
        if ($Headers.Count -gt 0) {
            $request.Headers = $Headers
        }
        $doc = Invoke-RestMethod @request
        foreach ($pathProperty in $doc.paths.PSObject.Properties) {
            $path = $pathProperty.Name
            if ($path -eq "/error") { continue }
            foreach ($methodProperty in $pathProperty.Value.PSObject.Properties) {
                $method = $methodProperty.Name.ToUpperInvariant()
                if (@("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS") -notcontains $method) { continue }
                $OpenApiOperations["$method $path"] = $methodProperty.Value.summary
            }
        }
        Add-Result -Method "GET" -Path "/api-docs" -Name "Load OpenAPI endpoint inventory" -Status "PASS" -Category "docs" -Detail "$($OpenApiOperations.Count) documented operations" -ElapsedMs 0
    } catch {
        $statusCode = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        if (@(401, 403, 404) -contains $statusCode) {
            Skip-Api -Method "GET" -Path "/api-docs" -Name "Load OpenAPI endpoint inventory" -Reason "OpenAPI docs are private or disabled for this profile."
        } else {
            Add-Result -Method "GET" -Path "/api-docs" -Name "Load OpenAPI endpoint inventory" -Status "FAIL" -Category "docs" -Detail $_.Exception.Message -ElapsedMs 0
        }
    }
}

function Run-SafeMutations {
    param(
        [object]$Hospital,
        [object]$Modality,
        [object]$Role,
        [object]$User,
        [string]$HospitalKeyForPayload = "",
        [string]$ModalityKeyForPayload = ""
    )

    if ($SkipSafeMutations) {
        Skip-Api -Method "POST" -Path "/modality/modality-create" -Name "Create/update/delete temporary modality" -Reason "SkipSafeMutations was set."
        Skip-Api -Method "POST" -Path "/role/role-add" -Name "Create/update/delete temporary role" -Reason "SkipSafeMutations was set."
        Skip-Api -Method "POST" -Path "/user/user-create" -Name "Create/update/delete temporary user" -Reason "SkipSafeMutations was set."
        Skip-Api -Method "POST" -Path "/dicom-server/dicom-server-create" -Name "Create/update/delete temporary DICOM server, machine, route" -Reason "SkipSafeMutations was set."
        return
    }

    $hospitalKey = if (-not [string]::IsNullOrWhiteSpace($HospitalKeyForPayload)) { $HospitalKeyForPayload } else { Get-EntityKey $Hospital }
    $modalityKey = if (-not [string]::IsNullOrWhiteSpace($ModalityKeyForPayload)) { $ModalityKeyForPayload } else { Get-EntityKey $Modality }
    if ([string]::IsNullOrWhiteSpace($hospitalKey) -or [string]::IsNullOrWhiteSpace($modalityKey)) {
        Add-Result -Method "POST" -Path "/dicom-server/dicom-server-create" -Name "Safe mutation suite" -Status "FAIL" -Category "safe-mutation" -Detail "Missing hospital/modality public keys required by create payloads."
        return
    }

    $modalityAbbr = "SMK" + (Get-Date -Format "mmss")
    $modalityName = "Smoke Modality $Stamp"
    $null = Test-Api -Method "POST" -Path "/modality/modality-create" -Name "Create temporary modality" -Body @{
        abbr = $modalityAbbr
        name = $modalityName
        isActive = 1
    } -Category "safe-mutation"
    $modalityRows = Get-DataList (Test-Api -Method "POST" -Path "/modality/modality-list" -Name "List modalities after temp create" -Body @{ rowsPerPage = 100; searchText = $modalityAbbr } -Category "read")
    $tempModality = Find-First -Rows $modalityRows -Property "abbr" -Value $modalityAbbr
    if ($null -ne $tempModality) {
        $tempModalityKey = Get-EntityKey $tempModality
        $null = Test-Api -Method "POST" -Path "/modality/modality-find/$tempModalityKey" -Name "Find temporary modality" -Category "read"
        $tempModalityUpdate = @{
            publicKey = $tempModalityKey
            abbr = $modalityAbbr
            name = "$modalityName Updated"
            isActive = 1
        }
        $null = Test-Api -Method "POST" -Path "/modality/modality-update" -Name "Update temporary modality" -Body $tempModalityUpdate -Category "safe-mutation"
        $null = Test-Api -Method "POST" -Path "/modality/modality-delete/$tempModalityKey" -Name "Delete temporary modality" -Category "safe-mutation"
    } else {
        Add-Result -Method "POST" -Path "/modality/modality-find/{id}" -Name "Find temporary modality" -Status "FAIL" -Category "safe-mutation" -Detail "Temporary modality was not returned by list."
        Skip-Api -Method "POST" -Path "/modality/modality-update" -Name "Update temporary modality" -Reason "Temporary modality id unavailable."
        Skip-Api -Method "POST" -Path "/modality/modality-delete/{id}" -Name "Delete temporary modality" -Reason "Temporary modality id unavailable."
    }

    $roleName = "Smoke Role $Stamp"
    $null = Test-Api -Method "POST" -Path "/role/role-add" -Name "Create temporary role" -Body @{
        name = $roleName
        userKeys = @()
        moduleDetailKeys = @()
    } -Category "safe-mutation"
    $roleRows = Get-DataList (Test-Api -Method "POST" -Path "/role/role-list" -Name "List roles after temp create" -Body @{ rowsPerPage = 100; searchText = $roleName } -Category "read")
    $tempRole = Find-First -Rows $roleRows -Property "name" -Value $roleName
    if ($null -ne $tempRole) {
        $tempRoleKey = Get-EntityKey $tempRole
        $tempRoleId = Get-EntityLong $tempRole
        $null = Test-Api -Method "POST" -Path "/role/role-find/$tempRoleKey" -Name "Find temporary role" -Category "read"
        if (-not [string]::IsNullOrWhiteSpace($tempRoleKey)) {
            $null = Test-Api -Method "POST" -Path "/permission/permission-save-role-permissions" -Name "Save permissions for temporary role" -Body @{
                roleKey = $tempRoleKey
                moduleDetailKeys = @()
            } -Category "safe-mutation"
        } else {
            Skip-Api -Method "POST" -Path "/permission/permission-save-role-permissions" -Name "Save permissions for temporary role" -Reason "Temporary role key unavailable."
        }
        $tempRoleUpdate = @{
            publicKey = $tempRoleKey
            name = "$roleName Updated"
            userKeys = @()
            moduleDetailKeys = @()
        }
        $null = Test-Api -Method "POST" -Path "/role/role-update" -Name "Update temporary role" -Body $tempRoleUpdate -Category "safe-mutation"
        $null = Test-Api -Method "POST" -Path "/role/role-delete/$tempRoleKey" -Name "Delete temporary role" -Category "safe-mutation"
    } else {
        Add-Result -Method "POST" -Path "/role/role-find/{id}" -Name "Find temporary role" -Status "FAIL" -Category "safe-mutation" -Detail "Temporary role was not returned by list."
        Skip-Api -Method "POST" -Path "/permission/permission-save-role-permissions" -Name "Save permissions for temporary role" -Reason "Temporary role id unavailable."
        Skip-Api -Method "POST" -Path "/role/role-update" -Name "Update temporary role" -Reason "Temporary role id unavailable."
        Skip-Api -Method "POST" -Path "/role/role-delete/{id}" -Name "Delete temporary role" -Reason "Temporary role id unavailable."
    }

    $userName = "smoke.user.$Stamp"
    $targetRoleKey = if ($null -ne $Role) { Get-EntityKey $Role } else { $null }
    $userGroupKeys = @()
    if (-not [string]::IsNullOrWhiteSpace($targetRoleKey)) { $userGroupKeys = @($targetRoleKey) }
    $null = Test-Api -Method "POST" -Path "/user/user-create" -Name "Create temporary user" -Body @{
        username = $userName
        email = "$userName@example.local"
        password = "SmokePass#2026"
        firstName = "Smoke"
        lastName = "User"
        hospitalKeys = @($hospitalKey)
        userGroupKeys = $userGroupKeys
    } -Category "safe-mutation"
    $userRows = Get-DataList (Test-Api -Method "POST" -Path "/user/user-list" -Name "List users after temp create" -Body @{ rowsPerPage = 100; searchText = $userName } -Category "read")
    $tempUser = Find-First -Rows $userRows -Property "username" -Value $userName
    if ($null -ne $tempUser) {
        $tempUserKey = Get-EntityKey $tempUser
        $tempUserId = Get-EntityLong $tempUser
        $null = Test-Api -Method "POST" -Path "/user/user-find/$tempUserKey" -Name "Find temporary user" -Category "read"
        $tempUserUpdate = @{
            publicKey = $tempUserKey
            username = $userName
            firstName = "Smoke"
            lastName = "User Updated"
            telephone = "010$($Stamp.Substring($Stamp.Length - 6))"
            email = "$userName@example.local"
            isActive = 1
            hospitalKeys = @($hospitalKey)
            userGroupKeys = $userGroupKeys
        }
        $null = Test-Api -Method "POST" -Path "/user/user-update" -Name "Update temporary user" -Body $tempUserUpdate -Category "safe-mutation"
        if (-not [string]::IsNullOrWhiteSpace($tempUserKey)) {
            $null = Test-Api -Method "POST" -Path "/user/user-change-password" -Name "Change password for temporary user" -Body @{
                userKey = $tempUserKey
                newPassword = "SmokePass#2027"
                confirmPassword = "SmokePass#2027"
            } -Category "safe-mutation"
        } else {
            Skip-Api -Method "POST" -Path "/user/user-change-password" -Name "Change password for temporary user" -Reason "Temporary user key unavailable."
        }
        $null = Test-Api -Method "POST" -Path "/user/user-delete/$tempUserKey" -Name "Delete temporary user" -Category "safe-mutation"
    } else {
        Add-Result -Method "POST" -Path "/user/user-find/{id}" -Name "Find temporary user" -Status "FAIL" -Category "safe-mutation" -Detail "Temporary user was not returned by list."
        Skip-Api -Method "POST" -Path "/user/user-update" -Name "Update temporary user" -Reason "Temporary user id unavailable."
        Skip-Api -Method "POST" -Path "/user/user-change-password" -Name "Change password for temporary user" -Reason "Temporary user id unavailable."
        Skip-Api -Method "POST" -Path "/user/user-delete/{id}" -Name "Delete temporary user" -Reason "Temporary user id unavailable."
    }

    $portSeed = [int]((Get-Date).Ticks % 1000)
    $serverName = "Smoke DICOM Server $Stamp"
    $serverAe = "SMKSRV$($Stamp.Substring($Stamp.Length - 6))"
    $null = Test-Api -Method "POST" -Path "/dicom-server/dicom-server-create" -Name "Create temporary DICOM server" -Body @{
        hospitalKey = $hospitalKey
        name = $serverName
        ipAddress = "127.0.0.1"
        port = 18000 + $portSeed
        dicomPort = 19000 + $portSeed
        aeTitle = $serverAe
        dicomwebPath = "/dicom-web"
        viewerBaseUrl = "http://127.0.0.1:3005"
        pacsApiCallbackBaseUrl = $BaseUrl
        username = "smoke_user"
        password = "SmokePass#2026"
        isActive = 1
    } -Category "safe-mutation"
    $serverRows = Get-DataList (Test-Api -Method "POST" -Path "/dicom-server/dicom-server-list" -Name "List DICOM servers after temp create" -Body @{ rowsPerPage = 100; searchText = $serverAe } -Category "read")
    $tempServer = Find-First -Rows $serverRows -Property "name" -Value $serverName

    $machineAe = "SMKMCH$($Stamp.Substring($Stamp.Length - 6))"
    $machineName = "Smoke Machine $Stamp"
    $null = Test-Api -Method "POST" -Path "/dicom-machine/dicom-machine-create" -Name "Create temporary DICOM machine" -Body @{
        hospitalKey = $hospitalKey
        modalityKey = $modalityKey
        machineName = $machineName
        machineAeTitle = $machineAe
        machineHost = "10.254.$($portSeed % 200).$($portSeed % 250 + 1)"
        machinePort = 104
    } -Category "safe-mutation"
    $machineRows = Get-DataList (Test-Api -Method "POST" -Path "/dicom-machine/dicom-machine-list" -Name "List DICOM machines after temp create" -Body @{ rowsPerPage = 100; searchText = $machineAe } -Category "read")
    $tempMachine = Find-First -Rows $machineRows -Property "machineAeTitle" -Value $machineAe

    if ($null -ne $tempServer -and $null -ne $tempMachine) {
        $tempServerKey = Get-EntityKey $tempServer
        $tempServerId = Get-EntityLong $tempServer
        $tempMachineKey = Get-EntityKey $tempMachine
        $tempMachineId = Get-EntityLong $tempMachine
        $null = Test-Api -Method "POST" -Path "/dicom-server/dicom-server-find/$tempServerKey" -Name "Find temporary DICOM server" -Category "read"
        $tempServerUpdate = @{
            publicKey = $tempServerKey
            hospitalKey = $hospitalKey
            name = "$serverName Updated"
            ipAddress = "127.0.0.1"
            port = 18000 + $portSeed
            dicomPort = 19000 + $portSeed
            aeTitle = $serverAe
            dicomwebPath = "/dicom-web"
            viewerBaseUrl = "http://127.0.0.1:3005"
            pacsApiCallbackBaseUrl = $BaseUrl
            username = "smoke_user"
            password = "SmokePass#2026"
            isActive = 1
        }
        $null = Test-Api -Method "POST" -Path "/dicom-server/dicom-server-update" -Name "Update temporary DICOM server" -Body $tempServerUpdate -Category "safe-mutation"
        $null = Test-Api -Method "POST" -Path "/dicom-machine/dicom-machine-find/$tempMachineKey" -Name "Find temporary DICOM machine" -Category "read"
        $tempMachineUpdate = @{
            publicKey = $tempMachineKey
            hospitalKey = $hospitalKey
            modalityKey = $modalityKey
            machineName = "$machineName Updated"
            machineAeTitle = $machineAe
            machineHost = "10.254.$($portSeed % 200).$($portSeed % 250 + 1)"
            machinePort = 104
        }
        $null = Test-Api -Method "POST" -Path "/dicom-machine/dicom-machine-update" -Name "Update temporary DICOM machine" -Body $tempMachineUpdate -Category "safe-mutation"
        if ($tempServerKey -and $tempMachineKey) {
            $null = Test-Api -Method "POST" -Path "/dicom-routing/dicom-routing-create" -Name "Create temporary DICOM route" -Body @{
                hospitalKey = $hospitalKey
                dicomServerKey = $tempServerKey
                routes = @(@{
                    machineKey = $tempMachineKey
                    modalityKey = $modalityKey
                })
            } -Category "safe-mutation"
            $routingRows = Get-DataList (Test-Api -Method "POST" -Path "/dicom-routing/dicom-routing-list" -Name "List DICOM routes after temp create" -Body @{ rowsPerPage = 100; searchText = $serverAe } -Category "read")
            $tempRoute = $null
            foreach ($route in $routingRows) {
                if ($route.dicomServerPublicKey -and [string]$route.dicomServerPublicKey -eq [string]$tempServerKey) {
                    $tempRoute = $route
                    break
                }
            }
            if ($null -ne $tempRoute) {
                $tempRouteKey = Get-EntityKey $tempRoute
                $routeChildKey = $null
                if ($tempRoute.routes) {
                    $routeChildKey = Get-EntityKey (@($tempRoute.routes)[0])
                }
                $null = Test-Api -Method "POST" -Path "/dicom-routing/dicom-routing-find/$tempRouteKey" -Name "Find temporary DICOM route" -Category "read"
                $updateRouteItem = @{
                    machineKey = $tempMachineKey
                    modalityKey = $modalityKey
                }
                if (-not [string]::IsNullOrWhiteSpace($routeChildKey)) { $updateRouteItem.publicKey = $routeChildKey }
                $tempRouteUpdate = @{
                    publicKey = $tempRouteKey
                    hospitalKey = $hospitalKey
                    dicomServerKey = $tempServerKey
                    routes = @($updateRouteItem)
                }
                $null = Test-Api -Method "POST" -Path "/dicom-routing/dicom-routing-update" -Name "Update temporary DICOM route" -Body $tempRouteUpdate -Category "safe-mutation"
                $null = Test-Api -Method "POST" -Path "/dicom-routing/dicom-routing-build-config/$tempRouteKey" -Name "Build temporary DICOM route package" -Category "safe-mutation"
                $null = Test-Api -Method "POST" -Path "/dicom-routing/dicom-routing-delete/$tempRouteKey" -Name "Delete temporary DICOM route" -Category "safe-mutation"
            } else {
                Add-Result -Method "POST" -Path "/dicom-routing/dicom-routing-find/{id}" -Name "Find temporary DICOM route" -Status "FAIL" -Category "safe-mutation" -Detail "Temporary DICOM route was not returned by list."
                Skip-Api -Method "POST" -Path "/dicom-routing/dicom-routing-update" -Name "Update temporary DICOM route" -Reason "Temporary route key unavailable."
                Skip-Api -Method "POST" -Path "/dicom-routing/dicom-routing-build-config/{id}" -Name "Build temporary DICOM route package" -Reason "Temporary route key unavailable."
                Skip-Api -Method "POST" -Path "/dicom-routing/dicom-routing-delete/{id}" -Name "Delete temporary DICOM route" -Reason "Temporary route key unavailable."
            }
        } else {
            Skip-Api -Method "POST" -Path "/dicom-routing/dicom-routing-create" -Name "Create temporary DICOM route" -Reason "DICOM routing create still requires numeric dicomServerId and machineId; temporary rows expose publicKey."
            Skip-Api -Method "POST" -Path "/dicom-routing/dicom-routing-find/{id}" -Name "Find temporary DICOM route" -Reason "Temporary route was not created."
            Skip-Api -Method "POST" -Path "/dicom-routing/dicom-routing-update" -Name "Update temporary DICOM route" -Reason "Temporary route was not created."
            Skip-Api -Method "POST" -Path "/dicom-routing/dicom-routing-build-config/{id}" -Name "Build temporary DICOM route package" -Reason "Temporary route was not created."
            Skip-Api -Method "POST" -Path "/dicom-routing/dicom-routing-delete/{id}" -Name "Delete temporary DICOM route" -Reason "Temporary route was not created."
        }
        $null = Test-Api -Method "POST" -Path "/dicom-machine/dicom-machine-delete/$tempMachineKey" -Name "Delete temporary DICOM machine" -Category "safe-mutation"
        $null = Test-Api -Method "POST" -Path "/dicom-server/dicom-server-delete/$tempServerKey" -Name "Delete temporary DICOM server" -Category "safe-mutation"
    } else {
        Add-Result -Method "POST" -Path "/dicom-server/dicom-server-find/{id}" -Name "Find temporary DICOM server" -Status "FAIL" -Category "safe-mutation" -Detail "Temporary server or machine was not returned by list."
        Skip-Api -Method "POST" -Path "/dicom-routing/dicom-routing-create" -Name "Create temporary DICOM route" -Reason "Temporary server or machine id unavailable."
        if ($null -ne $tempMachine) {
            $null = Test-Api -Method "POST" -Path "/dicom-machine/dicom-machine-delete/$(Get-EntityKey $tempMachine)" -Name "Delete temporary DICOM machine" -Category "safe-mutation"
        }
        if ($null -ne $tempServer) {
            $null = Test-Api -Method "POST" -Path "/dicom-server/dicom-server-delete/$(Get-EntityKey $tempServer)" -Name "Delete temporary DICOM server" -Category "safe-mutation"
        }
    }

    Test-FileUploadRoundTrip
}

$login = Test-Api -Method "POST" -Path "/auth/auth-login" -Name "Log in" -Body @{
    clientId = $ClientId
    username = $Username
    password = $Password
} -Category "auth"
if ($null -eq $login -or -not $login.success) {
    throw "Login failed; cannot continue endpoint smoke."
}
$loginData = Get-FirstData $login
if ($null -eq $loginData -or -not $loginData.accessToken) {
    throw "Login response did not include an access token."
}
$AccessToken = [string]$loginData.accessToken
$RefreshToken = [string]$loginData.refreshToken
$Headers = @{ Authorization = "Bearer $AccessToken" }
Get-OpenApiOperations

if (-not [string]::IsNullOrWhiteSpace($RefreshToken)) {
    $refresh = Test-Api -Method "POST" -Path "/auth/auth-refresh" -Name "Refresh token" -Body @{
        clientId = $ClientId
        refreshToken = $RefreshToken
    } -Category "auth"
    $refreshData = Get-FirstData $refresh
    if ($null -ne $refreshData -and $refreshData.accessToken) {
        $AccessToken = [string]$refreshData.accessToken
        $Headers = @{ Authorization = "Bearer $AccessToken" }
        if ($refreshData.refreshToken) {
            $RefreshToken = [string]$refreshData.refreshToken
        }
    }
} else {
    Skip-Api -Method "POST" -Path "/auth/auth-refresh" -Name "Refresh token" -Reason "Login response did not return a refresh token."
}

if ($env:PACS_TEST_CLIENT_SECRET) {
    $null = Test-Api -Method "POST" -Path "/auth/auth-client-credentials" -Name "Client credentials token" -Body @{
        clientId = $(if ($env:PACS_TEST_SERVICE_CLIENT_ID) { $env:PACS_TEST_SERVICE_CLIENT_ID } else { "dicomserver-adapter" })
        clientSecret = $env:PACS_TEST_CLIENT_SECRET
    } -Category "auth"
} else {
    Skip-Api -Method "POST" -Path "/auth/auth-client-credentials" -Name "Client credentials token" -Reason "PACS_TEST_CLIENT_SECRET is not set; not probing service-client secrets."
}

$hospitals = Get-DataList (Test-Api -Method "POST" -Path "/hospital/hospital-list" -Name "List hospitals" -Body @{} -Category "read")
$modalities = Get-DataList (Test-Api -Method "POST" -Path "/modality/modality-list" -Name "List modalities" -Body @{} -Category "read")
$servers = Get-DataList (Test-Api -Method "POST" -Path "/dicom-server/dicom-server-list" -Name "List DICOM servers" -Body @{} -Category "read")
$machines = Get-DataList (Test-Api -Method "POST" -Path "/dicom-machine/dicom-machine-list" -Name "List DICOM machines" -Body @{} -Category "read")
$routes = Get-DataList (Test-Api -Method "POST" -Path "/dicom-routing/dicom-routing-list" -Name "List DICOM routes" -Body @{} -Category "read")
$patients = Get-DataList (Test-Api -Method "POST" -Path "/patient/patient-list" -Name "List patients" -Body @{} -Category "read")
$worklists = Get-DataList (Test-Api -Method "POST" -Path "/worklist/worklist-list" -Name "List worklists" -Body @{} -Category "read")
$studies = Get-DataList (Test-Api -Method "POST" -Path "/study/study-list" -Name "List studies" -Body @{} -Category "read")
$users = Get-DataList (Test-Api -Method "POST" -Path "/user/user-list" -Name "List users" -Body @{} -Category "read")
$roles = Get-DataList (Test-Api -Method "POST" -Path "/role/role-list" -Name "List roles" -Body @{} -Category "read")
$userGroups = Get-DataList (Test-Api -Method "POST" -Path "/user/user-group-list" -Name "List user groups" -Body @{} -Category "read")
$moduleTypes = Get-DataList (Test-Api -Method "POST" -Path "/module-type/module-type-list" -Name "List module types" -Body @{} -Category "read")
$systemActivities = Get-DataList (Test-Api -Method "POST" -Path "/system-activity/system-activity-list" -Name "List system activity logs" -Body @{} -Category "read")
$userLogs = Get-DataList (Test-Api -Method "POST" -Path "/report/user-log/user-log-list" -Name "List user logs" -Body @{} -Category "read")
$notifications = Get-DataList (Test-Api -Method "POST" -Path "/notification/notification-list" -Name "List notifications" -Body @{} -Category "read")

$hospital = if ($hospitals.Count -gt 0) { $hospitals[0] } else { $null }
$modality = if ($modalities.Count -gt 0) { $modalities[0] } else { $null }
$server = if ($servers.Count -gt 0) { $servers[0] } else { $null }
$machine = if ($machines.Count -gt 0) { $machines[0] } else { $null }
$route = if ($routes.Count -gt 0) { $routes[0] } else { $null }
$patient = if ($patients.Count -gt 0) { $patients[0] } else { $null }
$worklist = if ($worklists.Count -gt 0) { $worklists[0] } else { $null }
$study = if ($studies.Count -gt 0) { $studies[0] } else { $null }
$user = if ($users.Count -gt 0) { $users[0] } else { $null }
$role = if ($roles.Count -gt 0) { $roles[0] } else { $null }
$moduleType = if ($moduleTypes.Count -gt 0) { $moduleTypes[0] } else { $null }
$activity = if ($systemActivities.Count -gt 0) { $systemActivities[0] } else { $null }
$userLog = if ($userLogs.Count -gt 0) { $userLogs[0] } else { $null }
$viewerResultHeaders = @{}
$viewerBrowserHeaders = @{}
$viewerInfoData = $null

$null = Test-Api -Method "POST" -Path "/dashboard/dashboard-overview" -Name "Dashboard overview" -Body @{} -Category "read"
$null = Test-Api -Method "POST" -Path "/hospital-modality" -Name "List hospitals with modalities by user" -Body @{} -Category "read"
$null = Test-Api -Method "POST" -Path "/permission/permission-tree" -Name "Permission tree" -Category "read"
$null = Test-Api -Method "POST" -Path "/role/user-group-list" -Name "List roles with users" -Body @{} -Category "read"
$null = Test-Api -Method "POST" -Path "/role/role-menu" -Name "Role menu" -Category "read"
$null = Test-Api -Method "POST" -Path "/user/user-me" -Name "Current user" -Category "read"
$null = Test-Api -Method "POST" -Path "/user-profile/user-profile-get" -Name "Current user profile" -Category "read"

foreach ($dropdownPath in @(
    "/dropdown/dropdown-nationality",
    "/dropdown/dropdown-hospital",
    "/dropdown/dropdown-modality",
    "/dropdown/dropdown-dicom-server",
    "/dropdown/dropdown-user-group-member",
    "/dropdown/dropdown-user",
    "/dropdown/dropdown-patient",
    "/dropdown/dropdown-user-group"
)) {
    $null = Test-Api -Method "POST" -Path $dropdownPath -Name $dropdownPath.TrimStart("/") -Body @{} -Category "read"
}

$hospitalDropdownRows = Get-DataList (Test-Api -Method "POST" -Path "/dropdown/dropdown-hospital" -Name "dropdown/dropdown-hospital relation ids" -Body @{} -Category "read")
$modalityDropdownRows = Get-DataList (Test-Api -Method "POST" -Path "/dropdown/dropdown-modality" -Name "dropdown/dropdown-modality relation ids" -Body @{} -Category "read")
$hospitalPayloadKey = if ($hospitalDropdownRows.Count -gt 0) { Get-EntityKey $hospitalDropdownRows[0] } else { Get-EntityKey $hospital }
$modalityPayloadKey = if ($modalityDropdownRows.Count -gt 0) { Get-EntityKey $modalityDropdownRows[0] } else { Get-EntityKey $modality }

if ($null -ne $hospital) {
    $null = Test-Api -Method "POST" -Path "/hospital/hospital-find/$(Get-EntityKey $hospital)" -Name "Find hospital" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/hospital/hospital-find/{id}" -Name "Find hospital" -Reason "No hospital row returned by list."
}
if ($null -ne $modality) {
    $null = Test-Api -Method "POST" -Path "/modality/modality-find/$(Get-EntityKey $modality)" -Name "Find modality" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/modality/modality-find/{id}" -Name "Find modality" -Reason "No modality row returned by list."
}
if ($null -ne $server) {
    $null = Test-Api -Method "POST" -Path "/dicom-server/dicom-server-find/$(Get-EntityKey $server)" -Name "Find DICOM server" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/dicom-server/dicom-server-find/{id}" -Name "Find DICOM server" -Reason "No DICOM server row returned by list."
}
if ($null -ne $machine) {
    $null = Test-Api -Method "POST" -Path "/dicom-machine/dicom-machine-find/$(Get-EntityKey $machine)" -Name "Find DICOM machine" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/dicom-machine/dicom-machine-find/{id}" -Name "Find DICOM machine" -Reason "No DICOM machine row returned by list."
}
if ($null -ne $route) {
    $null = Test-Api -Method "POST" -Path "/dicom-routing/dicom-routing-find/$(Get-EntityKey $route)" -Name "Find DICOM route" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/dicom-routing/dicom-routing-find/{id}" -Name "Find DICOM route" -Reason "No DICOM route row returned by list."
}
if ($null -ne $patient) {
    $null = Test-Api -Method "POST" -Path "/patient/patient-find/$(Get-EntityKey $patient)" -Name "Find patient" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/patient/patient-find/{id}" -Name "Find patient" -Reason "No patient row returned by list."
}
if ($null -ne $worklist) {
    $worklistKey = Get-EntityKey $worklist
    $worklistHospitalKey = Get-PropValue -Row $worklist -Names @("hospitalPublicKey", "hospitalKey")
    $null = Test-Api -Method "POST" -Path "/worklist/worklist-find" -Name "Find worklist" -Body @{ publicKey = $worklistKey; hospitalKey = $worklistHospitalKey } -Category "read"
    $machineRouteWorklist = $worklists | Where-Object {
        $statusValue = [string]$_.status
        $statusValue -eq "WAITING" -or $statusValue -eq "FAILED" -or $statusValue -eq "1" -or $statusValue -eq "4"
    } | Select-Object -First 1
    if ($null -ne $machineRouteWorklist) {
        $routeWorklistHospitalKey = Get-PropValue -Row $machineRouteWorklist -Names @("hospitalPublicKey", "hospitalKey")
        $null = Test-Api -Method "POST" -Path "/worklist/worklist-machine-routes" -Name "Worklist machine routes" -Body @{ worklistKey = (Get-EntityKey $machineRouteWorklist); publicKey = (Get-EntityKey $machineRouteWorklist); hospitalKey = $routeWorklistHospitalKey } -Category "read"
    } else {
        Skip-Api -Method "POST" -Path "/worklist/worklist-machine-routes" -Name "Worklist machine routes" -Reason "No WAITING or FAILED worklist is available for machine-route selection."
    }
    $null = Test-Api -Method "POST" -Path "/worklist/worklist-sync-result" -Name "Sync worklist result" -Body @{ publicKey = $worklistKey; hospitalKey = $worklistHospitalKey; notes = "api smoke sync only" } -Category "workflow" -AllowBusinessFalse
    $viewerWorklistId = $null
    $viewerWorklistKey = $null
    $viewerHospitalKey = $null
    if ($null -ne $study -and $study.worklistPublicKey) {
        $viewerWorklistKey = [string]$study.worklistPublicKey
        $viewerHospitalKey = [string]$study.hospitalPublicKey
    } elseif ($worklist.dicomServerStudyId -or $worklist.studyId) {
        $viewerWorklistKey = $worklistKey
        $viewerHospitalKey = [string]$worklistHospitalKey
    }
    if ($viewerWorklistKey -and $viewerHospitalKey) {
        $viewerKeyForPath = $viewerWorklistKey
        $viewerViewBody = @{ hospitalKey = $viewerHospitalKey; worklistKey = $viewerWorklistKey; publicKey = $viewerWorklistKey }
        $null = Test-Api -Method "POST" -Path "/worklist/worklist-view-study" -Name "View worklist study" -Body $viewerViewBody -Category "read" -AllowBusinessFalse
        $viewerInfo = Test-Api -Method "GET" -Path "/worklist/$viewerKeyForPath/viewer-info?hospitalKey=$viewerHospitalKey" -Name "Viewer info" -Category "read" -AllowBusinessFalse
        $viewerInfoData = Get-FirstData $viewerInfo
        if ($null -ne $viewerInfoData -and $viewerInfoData.viewerApiKey) {
            $viewerBrowserHeaders = @{
                "X-PACS-VIEWER-ACCESS" = [string]$viewerInfoData.viewerApiKey
            }
            if ($env:PACS_TEST_RESULT_API_KEY) {
                $viewerResultHeaders = @{
                    "X-PACS-VIEWER-ACCESS" = [string]$viewerInfoData.viewerApiKey
                    "X-PACS-RESULT-API-KEY" = [string]$env:PACS_TEST_RESULT_API_KEY
                }
            }
        }
    } else {
        Skip-Api -Method "POST" -Path "/worklist/worklist-view-study" -Name "View worklist study" -Reason "Sample worklist has no received study."
        Skip-Api -Method "GET" -Path "/worklist/{worklistId}/viewer-info" -Name "Viewer info" -Reason "Sample worklist has no received study."
    }
} else {
    Skip-Api -Method "POST" -Path "/worklist/worklist-find" -Name "Find worklist" -Reason "No worklist row returned by list."
    Skip-Api -Method "POST" -Path "/worklist/worklist-machine-routes" -Name "Worklist machine routes" -Reason "No worklist row returned by list."
    Skip-Api -Method "POST" -Path "/worklist/worklist-sync-result" -Name "Sync worklist result" -Reason "No worklist row returned by list."
    Skip-Api -Method "POST" -Path "/worklist/worklist-view-study" -Name "View worklist study" -Reason "No worklist row returned by list."
    Skip-Api -Method "GET" -Path "/worklist/{worklistId}/viewer-info" -Name "Viewer info" -Reason "No worklist row returned by list."
}
if ($null -ne $study) {
    $null = Test-Api -Method "POST" -Path "/study/study-find/$(Get-EntityKey $study)" -Name "Find study" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/study/study-find/{id}" -Name "Find study" -Reason "No study row returned by list."
}
if ($null -ne $user) {
    $null = Test-Api -Method "POST" -Path "/user/user-find/$(Get-EntityKey $user)" -Name "Find user" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/user/user-find/{id}" -Name "Find user" -Reason "No user row returned by list."
}
if ($null -ne $role) {
    $null = Test-Api -Method "POST" -Path "/role/role-find/$(Get-EntityKey $role)" -Name "Find role" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/role/role-find/{id}" -Name "Find role" -Reason "No role row returned by list."
}
if ($null -ne $moduleType) {
    $null = Test-Api -Method "POST" -Path "/module-type/find/$(Get-EntityKey $moduleType)" -Name "Find module type" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/module-type/find/{id}" -Name "Find module type" -Reason "No module type row returned by list."
}
if ($null -ne $activity) {
    $null = Test-Api -Method "POST" -Path "/system-activity/system-activity-find/$(Get-EntityKey $activity)" -Name "Find system activity" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/system-activity/system-activity-find/{id}" -Name "Find system activity" -Reason "No activity row returned by list."
}
if ($null -ne $userLog) {
    $null = Test-Api -Method "POST" -Path "/report/user-log/user-log-find/$(Get-EntityKey $userLog)" -Name "Find user log" -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/report/user-log/user-log-find/{id}" -Name "Find user log" -Reason "No user-log row returned by list."
}

if ($hospitalDropdownRows.Count -gt 0 -and $modalityDropdownRows.Count -gt 0) {
    $missingRoutes = New-Object System.Collections.Generic.List[object]
    foreach ($h in $hospitalDropdownRows) {
        foreach ($m in $modalityDropdownRows) {
            $routeHospitalKey = Get-EntityKey $h
            $routeModalityKey = Get-EntityKey $m
            if ([string]::IsNullOrWhiteSpace($routeHospitalKey) -or [string]::IsNullOrWhiteSpace($routeModalityKey)) { continue }
            $availability = Test-Api -Method "POST" -Path "/worklist/worklist-route-availability" -Name "Route availability $($h.abbr)/$($m.abbr)" -Body @{
                hospitalKey = $routeHospitalKey
                modalityKey = $routeModalityKey
            } -Category "read"
            $availabilityData = Get-FirstData $availability
            if ($null -eq $availabilityData -or $availabilityData.hasActiveRouting -ne $true) {
                $missingRoutes.Add([pscustomobject]@{ hospital = (Get-PropValue $h @("label", "abbr", "name")); modality = (Get-PropValue $m @("label", "abbr", "name")) }) | Out-Null
            }
        }
    }
    if ($missingRoutes.Count -gt 0) {
        Add-Result -Method "POST" -Path "/worklist/worklist-route-availability" -Name "Route availability coverage summary" -Status "FAIL" -Category "audit" -Detail ("Missing active routes: " + (($missingRoutes | ForEach-Object { "$($_.hospital)/$($_.modality)" }) -join ", "))
    } else {
        Add-Result -Method "POST" -Path "/worklist/worklist-route-availability" -Name "Route availability coverage summary" -Status "PASS" -Category "audit" -Detail "$($hospitalDropdownRows.Count) hospitals x $($modalityDropdownRows.Count) modalities covered."
    }
} else {
    Skip-Api -Method "POST" -Path "/worklist/worklist-route-availability" -Name "Route availability coverage summary" -Reason "Hospital or modality list is empty."
}

if (-not [string]::IsNullOrWhiteSpace($hospitalPayloadKey)) {
    $null = Test-Api -Method "POST" -Path "/worklist/worklist-routed-modality-list" -Name "List routed Worklist modalities" -Body @{
        hospitalKey = $hospitalPayloadKey
    } -Category "read"
} else {
    Skip-Api -Method "POST" -Path "/worklist/worklist-routed-modality-list" -Name "List routed Worklist modalities" -Reason "No hospital public key available."
}

if ($viewerResultHeaders.Count -gt 0 -and $null -ne $viewerInfoData) {
    if ($viewerInfoData.studyPublicKey -and $viewerInfoData.hospitalPublicKey -and $viewerInfoData.modalityPublicKey) {
        $null = Test-Api -Method "POST" -Path "/pacs-result/pacs-result-find-by-study" -Name "Find PACS result by study" -Body @{
            hospitalKey = [string]$viewerInfoData.hospitalPublicKey
            modalityKey = [string]$viewerInfoData.modalityPublicKey
            studyKey = [string]$viewerInfoData.studyPublicKey
        } -Category "read" -ExtraHeaders $viewerResultHeaders
        $null = Test-Api -Method "POST" -Path "/pacs-result/pacs-result-context" -Name "Resolve PACS result context" -Body @{
            hospitalKey = [string]$viewerInfoData.hospitalPublicKey
            modalityKey = [string]$viewerInfoData.modalityPublicKey
            worklistKey = [string]$viewerInfoData.publicKey
            studyKey = [string]$viewerInfoData.studyPublicKey
            studyInstanceUid = $viewerInfoData.studyInstanceUid
            accessionNumber = $viewerInfoData.accessionNumber
            patientCode = $viewerInfoData.patientUid
        } -Category "read" -AllowBusinessFalse -ExtraHeaders $viewerResultHeaders
        $null = Test-Api -Method "POST" -Path "/pacs-result/pacs-result-template-list" -Name "List PACS result templates" -Body @{
            hospitalKey = [string]$viewerInfoData.hospitalPublicKey
            modalityKey = [string]$viewerInfoData.modalityPublicKey
        } -Category "read" -ExtraHeaders $viewerResultHeaders
        $null = Test-Api -Method "POST" -Path "/pacs-result/pacs-result-find-by-worklist" -Name "Find PACS result by worklist" -Body @{
            hospitalKey = [string]$viewerInfoData.hospitalPublicKey
            worklistKey = [string]$viewerInfoData.publicKey
        } -Category "read" -ExtraHeaders $viewerResultHeaders
    } else {
        Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-find-by-study" -Name "Find PACS result by study" -Reason "Viewer info did not include hospital, modality, and study ids."
        Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-context" -Name "Resolve PACS result context" -Reason "Viewer info did not include hospital, modality, and study ids."
        Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-template-list" -Name "List PACS result templates" -Reason "Viewer info did not include hospital, modality, and study ids."
        Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-find-by-worklist" -Name "Find PACS result by worklist" -Reason "Viewer info did not include worklist context."
    }
} else {
    $pacsResultSkipReason = "PACS_TEST_RESULT_API_KEY is not set, or viewer-info did not return a viewer access token."
    Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-find-by-study" -Name "Find PACS result by study" -Reason $pacsResultSkipReason
    Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-context" -Name "Resolve PACS result context" -Reason $pacsResultSkipReason
    Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-template-list" -Name "List PACS result templates" -Reason $pacsResultSkipReason
    Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-find-by-worklist" -Name "Find PACS result by worklist" -Reason $pacsResultSkipReason
}

if ($viewerBrowserHeaders.Count -gt 0 -and $null -ne $viewerInfoData) {
    $viewerStateType = "API_SMOKE_VIEWER_STATE_$Stamp"
    $viewerStateBody = @{
        stateType = $viewerStateType
        schemaVersion = 2
    }
    Add-BodyValue -Body $viewerStateBody -Name "hospitalKey" -Value $viewerInfoData.hospitalPublicKey
    Add-BodyValue -Body $viewerStateBody -Name "modalityKey" -Value $viewerInfoData.modalityPublicKey
    Add-BodyValue -Body $viewerStateBody -Name "worklistKey" -Value $viewerInfoData.publicKey
    Add-BodyValue -Body $viewerStateBody -Name "studyKey" -Value $viewerInfoData.studyPublicKey
    Add-BodyValue -Body $viewerStateBody -Name "patientKey" -Value $viewerInfoData.patientPublicKey
    Add-BodyValue -Body $viewerStateBody -Name "studyInstanceUid" -Value $viewerInfoData.studyInstanceUid
    Add-BodyValue -Body $viewerStateBody -Name "accessionNumber" -Value $viewerInfoData.accessionNumber
    Add-BodyValue -Body $viewerStateBody -Name "patientCode" -Value $viewerInfoData.patientUid

    $viewerStateSaveBody = @{}
    foreach ($key in $viewerStateBody.Keys) {
        $viewerStateSaveBody[$key] = $viewerStateBody[$key]
    }
    $viewerStateSaveBody["viewerState"] = @{
        source = "api-smoke"
        savedAt = (Get-Date).ToUniversalTime().ToString("o")
    }
    $viewerStateSaveBody["measurements"] = @(
        @{ uid = "measurement-$Stamp"; label = "Smoke angle"; value = 57.8; unit = "deg" }
    )
    $viewerStateSaveBody["annotations"] = @(
        @{
            annotationUID = "annotation-$Stamp"
            metadata = @{ toolName = "Angle" }
            data = @{ label = "Smoke annotation" }
        }
    )
    $viewerStateSaveBody["labelmapSegmentations"] = @(
        @{
            segmentationId = "labelmap-$Stamp"
            representationTypes = @("Labelmap")
            labelmap = @{ sparseLabelmap = @{ truncated = $false; totalVoxels = 2; slices = @() } }
        }
    )
    $viewerStateSaveBody["contourSegmentations"] = @(
        @{
            segmentationId = "contour-$Stamp"
            representationTypes = @("Contour")
            contour = @{ annotationUIDsBySegment = @{ "1" = @("annotation-$Stamp") } }
        }
    )
    $viewerStateSaveBody["surfaceSegmentations"] = @(
        @{
            segmentationId = "surface-$Stamp"
            representationTypes = @("Surface")
            surface = @{ data = @{ geometryIds = @("geometry-$Stamp") } }
        }
    )
    $viewerStateSaveBody["presentationState"] = @{ activeViewportId = "viewport-1" }
    $viewerStateSaveBody["toolState"] = @{ activeTool = "Angle" }
    $viewerStateSaveBody["metadata"] = @{ testRun = $Stamp }

    $null = Test-Api -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-save" -Name "Save PACS viewer state" -Body $viewerStateSaveBody -Category "safe-mutation" -ExtraHeaders $viewerBrowserHeaders
    $foundViewerState = Test-Api -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-find" -Name "Find PACS viewer state" -Body $viewerStateBody -Category "read" -ExtraHeaders $viewerBrowserHeaders
    $foundViewerStateRow = Get-FirstData $foundViewerState
    if ($null -ne $foundViewerStateRow -and [string]$foundViewerStateRow.stateType -eq $viewerStateType) {
        Add-Result -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-find" -Name "Validate PACS viewer state round trip" -Status "PASS" -Category "audit" -Detail "Typed measurement and segmentation state returned from the database."
    } else {
        Add-Result -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-find" -Name "Validate PACS viewer state round trip" -Status "FAIL" -Category "audit" -Detail "Saved viewer state was not returned with the expected state type."
    }

    $null = Test-Api -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-delete" -Name "Delete PACS viewer state" -Body $viewerStateBody -Category "safe-mutation" -ExtraHeaders $viewerBrowserHeaders
    $deletedViewerState = Test-Api -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-find" -Name "Confirm PACS viewer state deletion" -Body $viewerStateBody -Category "read" -ExtraHeaders $viewerBrowserHeaders
    if (@(Get-DataList $deletedViewerState).Count -eq 0) {
        Add-Result -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-delete" -Name "Validate PACS viewer state cleanup" -Status "PASS" -Category "audit" -Detail "Soft-deleted viewer state is no longer returned."
    } else {
        Add-Result -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-delete" -Name "Validate PACS viewer state cleanup" -Status "FAIL" -Category "audit" -Detail "Viewer state remained visible after deletion."
    }
} else {
    $viewerStateSkipReason = "viewer-info did not return a scoped viewer access token."
    Skip-Api -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-save" -Name "Save PACS viewer state" -Reason $viewerStateSkipReason -Category "safe-mutation"
    Skip-Api -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-find" -Name "Find PACS viewer state" -Reason $viewerStateSkipReason
    Skip-Api -Method "POST" -Path "/pacs-result-api/pacs-result-viewer-state-delete" -Name "Delete PACS viewer state" -Reason $viewerStateSkipReason -Category "safe-mutation"
}

if (($null -ne $hospital -or -not [string]::IsNullOrWhiteSpace($hospitalPayloadKey)) -and ($null -ne $modality -or -not [string]::IsNullOrWhiteSpace($modalityPayloadKey)) -and $null -ne $role -and $null -ne $user) {
    Run-SafeMutations -Hospital $hospital -Modality $modality -Role $role -User $user -HospitalKeyForPayload $hospitalPayloadKey -ModalityKeyForPayload $modalityPayloadKey
} else {
    Skip-Api -Method "POST" -Path "/dicom-server/dicom-server-create" -Name "Safe mutation suite" -Reason "Missing required hospital, modality, role, or user seed data."
}

Skip-Api -Method "POST" -Path "/hospital/hospital-create" -Name "Create hospital" -Reason "No API delete endpoint exists; skipped to keep database clean." -Category "unsafe-mutation"
Skip-Api -Method "POST" -Path "/hospital/hospital-update" -Name "Update hospital" -Reason "Would modify live hospital configuration; list/find covered." -Category "unsafe-mutation"
if ($IncludeNoCleanupMutations) {
    $patientName = "Smoke Patient $Stamp"
    $null = Test-Api -Method "POST" -Path "/patient/patient-create" -Name "Create patient without API cleanup" -Body @{
        hospitalKey = $hospitalPayloadKey
        patientName = $patientName
        phoneNumber = "010$($Stamp.Substring($Stamp.Length - 6))"
        gender = "M"
        dateOfBirth = "1990-01-01"
    } -Category "no-cleanup-mutation"
    Skip-Api -Method "POST" -Path "/patient/patient-update" -Name "Update patient without API cleanup" -Reason "Patient create has no API delete cleanup in this smoke script." -Category "unsafe-mutation"
} else {
    Skip-Api -Method "POST" -Path "/patient/patient-create" -Name "Create patient" -Reason "No API delete endpoint exists; use -IncludeNoCleanupMutations only when test rows are acceptable." -Category "unsafe-mutation"
    Skip-Api -Method "POST" -Path "/patient/patient-update" -Name "Update patient" -Reason "No API delete endpoint exists; skipped to keep database clean." -Category "unsafe-mutation"
}
Skip-Api -Method "POST" -Path "/user-profile/user-profile-update" -Name "Update user profile" -Reason "Requires current password and writes to admin profile; skipped by smoke."
Skip-Api -Method "POST" -Path "/worklist/worklist-assign" -Name "Assign patient to worklist" -Reason "No EMR worklist delete endpoint; end-to-end simulator covers this separately." -Category "unsafe-mutation"
Skip-Api -Method "POST" -Path "/worklist/worklist-update" -Name "Update worklist" -Reason "Would modify a clinical worklist; skipped by smoke." -Category "unsafe-mutation"
Skip-Api -Method "POST" -Path "/worklist/worklist-send-to-pacs" -Name "Send worklist to PACS" -Reason "Creates/updates DICOM server worklist; end-to-end simulator covers this separately." -Category "unsafe-mutation"
Skip-Api -Method "POST" -Path "/worklist/worklist-cancel" -Name "Cancel worklist" -Reason "Would cancel a clinical worklist; skipped by smoke." -Category "unsafe-mutation"
Skip-Api -Method "POST" -Path "/worklist/worklist-return" -Name "Return worklist" -Reason "Alias of cancel; would cancel a clinical worklist." -Category "unsafe-mutation"
Skip-Api -Method "POST" -Path "/worklist/worklist-received-study" -Name "Receive stable-study callback" -Reason "Internal DICOM server callback requiring machine-client token and real study payload." -Category "internal"
Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-create" -Name "Create PACS result" -Reason "Writes clinical result data and has no result delete endpoint." -Category "unsafe-mutation"
Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-update" -Name "Update PACS result" -Reason "Writes clinical result data and has no result delete endpoint." -Category "unsafe-mutation"
Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-image-upload" -Name "Upload PACS result images" -Reason "Requires persistent result id; skipped to avoid orphaned clinical images." -Category "unsafe-mutation"
Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-image-delete" -Name "Delete PACS result image" -Reason "No disposable result image available." -Category "unsafe-mutation"
Skip-Api -Method "POST" -Path "/pacs-result/pacs-result-image-content" -Name "Read PACS result image" -Reason "No disposable result image available." -Category "read"
Skip-Api -Method "GET" -Path "/worklist/worklist-view-study-preview/{worklistId}/{instanceId}" -Name "View study preview image" -Reason "Requires a concrete image instance id from a viewer session." -Category "read"

$null = Test-Api -Method "POST" -Path "/auth/auth-logout" -Name "Log out" -Body @{} -Category "auth" -AllowBusinessFalse

$covered = @{}
foreach ($r in $Results) {
    if ($r.path -like "/api-docs*") { continue }
    $canonical = "$($r.method) $($r.path)"
    foreach ($operationKey in $OpenApiOperations.Keys) {
        $parts = $operationKey.Split(" ", 2)
        $operationMethod = $parts[0]
        $operationPath = $parts[1]
        $escapedOperationPath = [regex]::Escape($operationPath)
        $escapedOperationPath = [regex]::Replace($escapedOperationPath, "\\\{[^}]+\}", "[^/]+")
        $regex = "^" + $escapedOperationPath + "(\?.*)?$"
        if ($r.method -eq $operationMethod -and $r.path -match $regex) {
            $covered[$operationKey] = $true
        }
    }
}

foreach ($operationKey in ($OpenApiOperations.Keys | Sort-Object)) {
    if (-not $covered.ContainsKey($operationKey)) {
        $parts = $operationKey.Split(" ", 2)
        Add-Result -Method $parts[0] -Path $parts[1] -Name $OpenApiOperations[$operationKey] -Status "SKIP" -Category "unmapped" -Detail "Documented endpoint was not mapped to a smoke payload."
    }
}

$summary = $Results | Group-Object status | Sort-Object Name | ForEach-Object {
    [pscustomobject]@{ status = $_.Name; count = $_.Count }
}
$failures = @($Results | Where-Object { $_.status -eq "FAIL" })
$skips = @($Results | Where-Object { $_.status -eq "SKIP" })

Write-Host ""
Write-Host "UDAYA PACS API endpoint smoke summary" -ForegroundColor Cyan
$summary | Format-Table -AutoSize
Write-Host "Total results: $($Results.Count)"
Write-Host "Documented OpenAPI operations: $($OpenApiOperations.Count)"

if ($failures.Count -gt 0) {
    Write-Host ""
    Write-Host "Failures" -ForegroundColor Red
    $failures | Select-Object method, path, name, detail | Format-Table -Wrap -AutoSize
}

if ($skips.Count -gt 0) {
    Write-Host ""
    Write-Host "Skipped by design" -ForegroundColor Yellow
    $skips | Select-Object method, path, name, category, detail | Format-Table -Wrap -AutoSize
}

if ($failures.Count -gt 0) {
    exit 1
}
