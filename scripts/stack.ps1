Param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("local", "qa", "prod")]
    [string]$Target,

    [Parameter(Mandatory = $true)]
    [ValidateSet(
        "up",
        "down",
        "restart",
        "deploy",
        "logs",
        "ps",
        "health",
        "db-backup",
        "db-migrate",
        "db-validate",
        "db-refresh-cache",
        "db-partition-maintenance"
    )]
    [string]$Action,

    [switch]$Build,
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

function Get-EnvValue {
    param([string]$FilePath, [string]$Key)
    if (-not (Test-Path $FilePath)) { return "" }
    foreach ($line in Get-Content $FilePath) {
        if ($line -match '^\s*#' -or [string]::IsNullOrWhiteSpace($line)) { continue }
        $idx = $line.IndexOf("=")
        if ($idx -le 0) { continue }
        if ($line.Substring(0, $idx).Trim() -eq $Key) {
            return $line.Substring($idx + 1).Trim()
        }
    }
    return ""
}

function Get-EnvValueOrDefault {
    param([string]$FilePath, [string]$Key, [string]$DefaultValue)
    $value = Get-EnvValue -FilePath $FilePath -Key $Key
    if ([string]::IsNullOrWhiteSpace($value)) { return $DefaultValue }
    return $value
}

function Set-EnvValue {
    param([string]$FilePath, [string]$Key, [string]$Value)
    $lines = if (Test-Path $FilePath) { @(Get-Content $FilePath) } else { @() }
    $found = $false
    $next = foreach ($line in $lines) {
        if ($line -match ("^" + [regex]::Escape($Key) + "=")) {
            $found = $true
            "$Key=$Value"
        } else {
            $line
        }
    }
    if (-not $found) { $next += "$Key=$Value" }
    Set-Content -Path $FilePath -Value $next
}

function New-AlphaNumericSecret {
    param([int]$Length = 64)
    $alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    $chars = New-Object char[] $Length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $buffer = New-Object byte[] 1
    for ($i = 0; $i -lt $Length; $i++) {
        do {
            $rng.GetBytes($buffer)
            $value = [int]$buffer[0]
        } while ($value -ge (256 - (256 % $alphabet.Length)))
        $chars[$i] = $alphabet[$value % $alphabet.Length]
    }
    $rng.Dispose()
    return -join $chars
}

function Test-MissingOrPlaceholderValue {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return $true }
    $normalized = $Value.Trim().ToLowerInvariant()
    return $normalized -match '^(change|change_me|change_me_.*|replace_with_.*|<.*>)$'
}

function Get-PrimaryIPv4Address {
    try {
        $candidate = Get-NetIPConfiguration -ErrorAction Stop |
            Where-Object { $_.IPv4DefaultGateway -and $_.IPv4Address } |
            ForEach-Object { $_.IPv4Address.IPAddress } |
            Where-Object {
                $_ -and
                $_ -notmatch '^127\.' -and
                $_ -notmatch '^169\.254\.'
            } |
            Select-Object -First 1
        if (-not [string]::IsNullOrWhiteSpace($candidate)) { return $candidate }
    } catch {
        # Get-NetIPConfiguration is not available in every shell.
    }

    try {
        $candidate = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
            Where-Object {
                $_.IPAddress -and
                $_.IPAddress -notmatch '^127\.' -and
                $_.IPAddress -notmatch '^169\.254\.'
            } |
            Select-Object -First 1 -ExpandProperty IPAddress
        if (-not [string]::IsNullOrWhiteSpace($candidate)) { return $candidate }
    } catch {
        # Fall back to loopback when no routable host IP can be detected.
    }

    return "127.0.0.1"
}

function Resolve-BindHost {
    param([string]$Value)
    $normalized = if ($null -eq $Value) { "" } else { $Value.Trim() }
    if ([string]::IsNullOrWhiteSpace($normalized) -or $normalized -match '^(?i:auto)$') {
        if ($Target -eq "local") { return "127.0.0.1" }
        return Get-PrimaryIPv4Address
    }
    if ($normalized -in @("0.0.0.0", "*", "::", "[::]")) {
        if ($Target -eq "local") { return "127.0.0.1" }
        return Get-PrimaryIPv4Address
    }
    return $normalized
}

function Get-HealthHost {
    param([string]$BindHost)
    if ($BindHost -match '^(?i:localhost)$') { return "127.0.0.1" }
    return $BindHost
}

switch ($Target) {
    "local" {
        $envFile = if (Test-Path ".env.local") { ".env.local" } else { ".env" }
        $portKey = "LOCAL_API_PORT"
        $containerKey = "LOCAL_API_CONTAINER_NAME"
        $defaultPort = "8080"
    }
    "qa" {
        $envFile = ".env.qa"
        $portKey = "QA_API_PORT"
        $containerKey = "QA_API_CONTAINER_NAME"
        $defaultPort = "8080"
    }
    "prod" {
        $envFile = ".env.prod"
        $portKey = "PROD_API_PORT"
        $containerKey = "PROD_API_CONTAINER_NAME"
        $defaultPort = "8080"
    }
}

if (-not (Test-Path $envFile)) {
    throw "Missing env file: $envFile"
}

$serviceName = Get-EnvValue -FilePath $envFile -Key $containerKey
if ([string]::IsNullOrWhiteSpace($serviceName)) { $serviceName = "udaya_pacs_${Target}_api" }
$port = Get-EnvValue -FilePath $envFile -Key $portKey
if ([string]::IsNullOrWhiteSpace($port)) { $port = $defaultPort }
$imageName = Get-EnvValue -FilePath $envFile -Key "APP_IMAGE"
if ([string]::IsNullOrWhiteSpace($imageName)) { $imageName = "udaya_pacs_${Target}_api:latest" }
$targetUpper = $Target.ToUpperInvariant()
$candidatePort = Get-EnvValue -FilePath $envFile -Key "${targetUpper}_API_CANDIDATE_PORT"
if ([string]::IsNullOrWhiteSpace($candidatePort)) { $candidatePort = Get-EnvValue -FilePath $envFile -Key "API_CANDIDATE_PORT" }
if ([string]::IsNullOrWhiteSpace($candidatePort)) {
    $parsedPort = 0
    if ([int]::TryParse($port, [ref]$parsedPort)) {
        $candidatePort = [string]($parsedPort + 1000)
    } else {
        $candidatePort = "9080"
    }
}
$bindHost = Get-EnvValue -FilePath $envFile -Key "${targetUpper}_API_BIND_HOST"
if ([string]::IsNullOrWhiteSpace($bindHost)) { $bindHost = Get-EnvValue -FilePath $envFile -Key "API_BIND_HOST" }
$bindHost = Resolve-BindHost -Value $bindHost

$redisContainerKey = "${targetUpper}_REDIS_CONTAINER_NAME"
$redisPasswordKey = "${targetUpper}_REDIS_PASSWORD"
$redisHostPortKey = "${targetUpper}_REDIS_HOST_PORT"
$redisContainerName = Get-EnvValue -FilePath $envFile -Key $redisContainerKey
if ([string]::IsNullOrWhiteSpace($redisContainerName)) { $redisContainerName = "udaya_pacs_${Target}_redis" }
$redisHostPort = Get-EnvValue -FilePath $envFile -Key $redisHostPortKey
if ([string]::IsNullOrWhiteSpace($redisHostPort)) {
    $redisHostPort = switch ($Target) {
        "local" { "6379" }
        "qa" { "6380" }
        "prod" { "6381" }
    }
}
$redisNetworkName = Get-EnvValue -FilePath $envFile -Key "${targetUpper}_REDIS_NETWORK_NAME"
if ([string]::IsNullOrWhiteSpace($redisNetworkName)) { $redisNetworkName = "udaya_pacs_${Target}_network" }
$redisImage = Get-EnvValueOrDefault -FilePath $envFile -Key "REDIS_IMAGE" -DefaultValue "redis:7-alpine"
$redisPort = Get-EnvValueOrDefault -FilePath $envFile -Key "REDIS_PORT" -DefaultValue "6379"

function Get-ConfigValue {
    param([string]$Key)
    $envValue = [Environment]::GetEnvironmentVariable($Key)
    if (-not [string]::IsNullOrWhiteSpace($envValue)) { return $envValue }
    return Get-EnvValue -FilePath $envFile -Key $Key
}

function Get-TelegramConfigValue {
    param([string[]]$Keys)
    foreach ($key in $Keys) {
        $value = Get-ConfigValue -Key $key
        if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    }
    return ""
}

function Get-TelegramBotToken {
    Get-TelegramConfigValue -Keys @("DEPLOY_TELEGRAM_API_TOKEN", "$($Target.ToUpperInvariant())_TELEGRAM_API_TOKEN", "TELEGRAM_API_TOKEN", "telegram.api.token")
}

function Get-TelegramChatId {
    Get-TelegramConfigValue -Keys @("DEPLOY_TELEGRAM_CHAT_ID", "$($Target.ToUpperInvariant())_TELEGRAM_CHAT_ID", "TELEGRAM_CHAT_ID", "telegram.chat.id")
}

function Test-TelegramEnabled {
    $enabled = Get-TelegramConfigValue -Keys @("DEPLOY_TELEGRAM_ENABLED", "TELEGRAM_DEPLOY_ENABLED", "$($Target.ToUpperInvariant())_TELEGRAM_DEPLOY_ENABLED")
    if ($enabled -match '^(?i:false|0|no)$') { return $false }
    return (-not [string]::IsNullOrWhiteSpace((Get-TelegramBotToken))) -and (-not [string]::IsNullOrWhiteSpace((Get-TelegramChatId)))
}

function Protect-DeployText {
    param([string]$Value)
    if ($null -eq $Value) { return "" }
    return ($Value -replace '(?i)((TOKEN|PASSWORD|SECRET|API_KEY|PRIVATE_KEY|PUBLIC_KEY|telegram\.api\.token)[A-Za-z0-9_.-]*[=:])\s*\S+', '$1***REDACTED***')
}

function New-DeployReport {
    param(
        [string]$Status,
        [string]$Summary,
        [string]$Line = "",
        [string]$ContainerName = ""
    )
    if ([string]::IsNullOrWhiteSpace($ContainerName)) { $ContainerName = $serviceName }
    $reportPath = Join-Path ([System.IO.Path]::GetTempPath()) ("udaya_pacs_api_{0}_{1}_deploy_{2}.json" -f $Target, $Status, ([Guid]::NewGuid().ToString("N")))
    $logs = ""
    $psOutput = ""
    $dockerDf = ""
    try { $logs = docker logs --tail 160 $ContainerName 2>&1 | Out-String } catch { $logs = $_.Exception.Message }
    try { $psOutput = docker ps -a --filter "name=$ContainerName" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}" 2>&1 | Out-String } catch { $psOutput = $_.Exception.Message }
    try { $dockerDf = docker system df 2>&1 | Out-String } catch { $dockerDf = $_.Exception.Message }
    $report = [ordered]@{
        system            = "UDAYA_PACS"
        app               = "UDAYA_PACS_API"
        target            = $Target
        action            = $Action
        status            = $Status
        summary           = $Summary
        line              = $Line
        datetime          = (Get-Date).ToString("o")
        hostname          = $env:COMPUTERNAME
        container         = $ContainerName
        image             = $imageName
        healthUrl         = Get-PublicApiHealthUrl
        dockerPs          = Protect-DeployText $psOutput
        dockerSystemDf    = Protect-DeployText $dockerDf
        containerLogsTail = Protect-DeployText $logs
    }
    $report | ConvertTo-Json -Depth 5 | Set-Content -Path $reportPath -Encoding UTF8
    return $reportPath
}

function Invoke-TelegramCurl {
    param([string[]]$Arguments)
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if (-not $curl) { $curl = Get-Command curl -ErrorAction SilentlyContinue }
    if (-not $curl) { return }
    try { & $curl.Source @Arguments | Out-Null } catch { Write-Warning "Telegram deploy notification failed." }
}

function Send-DeployNotification {
    param(
        [string]$Status,
        [string]$Summary,
        [string]$Line = "",
        [string]$ContainerName = ""
    )
    if ([string]::IsNullOrWhiteSpace($ContainerName)) { $ContainerName = $serviceName }
    if ($Action -ne "deploy" -or -not (Test-TelegramEnabled)) { return }
    $token = Get-TelegramBotToken
    $chatId = Get-TelegramChatId
    $message = @"
UDAYA_PACS_API deploy $Status
Target: $Target
Container: $ContainerName
Image: $imageName
Host: $env:COMPUTERNAME
Time: $((Get-Date).ToString("o"))
Summary: $Summary
"@
    Invoke-TelegramCurl -Arguments @("-fsS", "--max-time", "20", "-X", "POST", "https://api.telegram.org/bot$token/sendMessage", "-F", "chat_id=$chatId", "-F", "text=$message")
    if ($Status -ne "SUCCESS") {
        $reportPath = New-DeployReport -Status $Status -Summary $Summary -Line $Line -ContainerName $ContainerName
        try {
            Invoke-TelegramCurl -Arguments @("-fsS", "--max-time", "30", "-X", "POST", "https://api.telegram.org/bot$token/sendDocument", "-F", "chat_id=$chatId", "-F", "caption=UDAYA_PACS_API deploy $Status report ($Target)", "-F", "document=@$reportPath;type=application/json")
        } finally {
            Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
        }
    }
}

function Invoke-Compose {
    param([string[]]$ComposeArgs)
    & docker compose --env-file $envFile --profile $Target -f docker-compose.yml @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($ComposeArgs -join ' ') failed (exit $LASTEXITCODE). Aborting deploy so a stale image is not promoted."
    }
}

function Invoke-EndpointGate {
    Write-Host "Running API endpoint/security unit gate..."
    & powershell -ExecutionPolicy Bypass -File ".\scripts\test-gate.ps1" -Target $Target -Tag "latest" -Context "stack-build"
    if ($LASTEXITCODE -ne 0) {
        throw "API endpoint/security unit gate failed. Build blocked."
    }
}

function Show-LocalDockerPsSnapshot {
    if ($Target -ne "local" -or $Action -notin @("up", "deploy")) { return }
    if ($env:UDAYA_PACS_DOCKER_PS_LOGGED -eq "1") { return }

    Write-Host ""
    Write-Host ("Docker containers before local API {0}:" -f $Action)
    docker ps | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "docker ps failed before local API $Action." }
    $env:UDAYA_PACS_DOCKER_PS_LOGGED = "1"
}

function Resolve-HostPath {
    param([string]$PathValue, [string]$DefaultValue)
    $value = if ([string]::IsNullOrWhiteSpace($PathValue)) { $DefaultValue } else { $PathValue }
    if ([System.IO.Path]::IsPathRooted($value)) { return $value }
    $resolvedPath = Join-Path $projectRoot $value
    if (-not (Test-Path $resolvedPath)) {
        New-Item -ItemType Directory -Force -Path $resolvedPath | Out-Null
    }
    return (Resolve-Path $resolvedPath).Path
}

function Test-PathUnder {
    param([string]$ChildPath, [string]$ParentPath)
    $child = [System.IO.Path]::GetFullPath($ChildPath).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $parent = [System.IO.Path]::GetFullPath($ParentPath).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    if ($child.Equals($parent, [System.StringComparison]::OrdinalIgnoreCase)) { return $true }
    return $child.StartsWith($parent + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)
}

function Assert-DicomUploadTempPath {
    param([string]$PathValue)
    $apiRoot = [System.IO.Path]::GetFullPath($projectRoot)
    $frontendRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot "..\PACS_Frontend"))
    if ((Test-PathUnder -ChildPath $PathValue -ParentPath $apiRoot) -or (Test-PathUnder -ChildPath $PathValue -ParentPath $frontendRoot)) {
        throw "PACS_DICOM_UPLOAD_TEMP_HOST_PATH must be outside PACS_API and PACS_Frontend: $PathValue"
    }
}

function Get-TargetEnvValue {
    param([string]$Suffix, [string]$FallbackKey = "")
    $prefixKey = "$($Target.ToUpperInvariant())_$Suffix"
    $value = Get-EnvValue -FilePath $envFile -Key $prefixKey
    if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    if (-not [string]::IsNullOrWhiteSpace($FallbackKey)) {
        $value = Get-EnvValue -FilePath $envFile -Key $FallbackKey
        if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    }
    return ""
}

function Get-ApiBaseUrl {
    $value = Get-TargetEnvValue -Suffix "API_AUTH_URL" -FallbackKey "API_AUTH_URL"
    if ([string]::IsNullOrWhiteSpace($value)) {
        $baseHost = if ($Target -eq "local") { "127.0.0.1" } else { Get-HealthHost -BindHost $bindHost }
        $value = "http://${baseHost}:${port}/pacsApi"
    }
    return $value.TrimEnd("/")
}

function Get-PublicApiHealthUrl {
    return "$(Get-ApiBaseUrl)/actuator/health"
}

function Test-TargetNetworkConfig {
    if ($Target -eq "local") { return }
    $apiUrl = Get-ApiBaseUrl
    $origins = Get-EnvValue -FilePath $envFile -Key "CORS_ALLOWED_ORIGINS"
    if ([string]::IsNullOrWhiteSpace($origins)) {
        throw "CORS_ALLOWED_ORIGINS is required for $Target."
    }
    if ($apiUrl -match '(?i)localhost|127\.0\.0\.1|host\.docker\.internal') {
        throw "$($Target.ToUpperInvariant())_API_AUTH_URL must use the deployed server IP or DNS name: $apiUrl"
    }
    if ($origins -match '(?i)localhost|127\.0\.0\.1|host\.docker\.internal') {
        throw "CORS_ALLOWED_ORIGINS must use deployed server IPs or DNS names for $Target."
    }
}

function Test-DatabaseEndpoint {
    if ($Target -eq "local") { return }
    $url = Get-TargetEnvValue -Suffix "SPRING_DATASOURCE_URL" -FallbackKey "SPRING_DATASOURCE_URL"
    if ($url -notmatch '^jdbc:postgresql://([^/:]+)(?::(\d+))?/.+$') {
        throw "$($Target.ToUpperInvariant())_SPRING_DATASOURCE_URL must be a jdbc:postgresql:// URL."
    }
    $hostName = $Matches[1]
    $dbPort = if ([string]::IsNullOrWhiteSpace($Matches[2])) { 5432 } else { [int]$Matches[2] }
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.BeginConnect($hostName, $dbPort, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne(5000)) {
            throw "Database endpoint unreachable from PACS server: ${hostName}:${dbPort}"
        }
        $client.EndConnect($connect)
        Write-Host "Database endpoint reachable: ${hostName}:${dbPort}"
    } catch {
        throw "Database endpoint unreachable from PACS server: ${hostName}:${dbPort}. $($_.Exception.Message)"
    } finally {
        $client.Dispose()
    }
}

function Test-DatabaseEndpointFromApiNetwork {
    if ($Target -eq "local") { return }
    $url = Get-TargetEnvValue -Suffix "SPRING_DATASOURCE_URL" -FallbackKey "SPRING_DATASOURCE_URL"
    if ($url -notmatch '^jdbc:postgresql://([^/:]+)(?::(\d+))?/.+$') {
        throw "$($Target.ToUpperInvariant())_SPRING_DATASOURCE_URL must be a jdbc:postgresql:// URL."
    }
    $hostName = $Matches[1]
    $dbPort = if ([string]::IsNullOrWhiteSpace($Matches[2])) { 5432 } else { [int]$Matches[2] }
    & docker run --rm --network $redisNetworkName --entrypoint bash $imageName -c "timeout 5 bash -c ':</dev/tcp/${hostName}/${dbPort}'" *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Database endpoint unreachable from API Docker network: ${hostName}:${dbPort}. Check routing/firewall from the PACS server Docker bridge to the database server."
    }
    Write-Host "Database endpoint reachable from API Docker network: ${hostName}:${dbPort}"
}

function Ensure-RedisEnvValues {
    $changed = $false
    $defaults = [ordered]@{
        "APP_CACHE_PROVIDER" = "redis"
        "APP_CACHE_TTL_SECONDS" = "600"
        "APP_CACHE_MAX_ENTRIES" = "5000"
        "APP_CACHE_REDIS_KEY_PREFIX" = "udaya_pacs_${Target}"
        "APP_CACHE_REDIS_HEALTH_ENABLED" = "true"
        "REDIS_IMAGE" = "redis:7-alpine"
        "REDIS_PORT" = "6379"
        "REDIS_DATABASE" = "0"
        "REDIS_TIMEOUT" = "2s"
        "REDIS_MAXMEMORY" = "512mb"
        "REDIS_MAXMEMORY_POLICY" = "allkeys-lru"
    }
    $defaults[$redisContainerKey] = $redisContainerName
    $defaults[$redisHostPortKey] = $redisHostPort
    $defaults["${targetUpper}_REDIS_NETWORK_NAME"] = $redisNetworkName
    foreach ($entry in $defaults.GetEnumerator()) {
        $current = Get-EnvValue -FilePath $envFile -Key $entry.Key
        if ([string]::IsNullOrWhiteSpace($current)) {
            Set-EnvValue -FilePath $envFile -Key $entry.Key -Value $entry.Value
            $changed = $true
        }
    }
    $redisPassword = Get-EnvValue -FilePath $envFile -Key $redisPasswordKey
    if (Test-MissingOrPlaceholderValue $redisPassword) {
        Set-EnvValue -FilePath $envFile -Key $redisPasswordKey -Value (New-AlphaNumericSecret 72)
        $changed = $true
        Write-Host "Generated $redisPasswordKey in $envFile"
    }
    if ($changed) {
        Write-Host "Redis cache settings are ready in $envFile"
    }
}

function Remove-ContainerIfExists {
    param([string]$Name)
    $exists = $false
    try {
        docker container inspect $Name *> $null
        $exists = $true
    } catch {
        $exists = $false
    }
    if ($exists) {
        docker rm -f $Name | Out-Null
    }
}

function Ensure-DockerNetwork {
    param([string]$Name)
    try {
        docker network inspect $Name *> $null
    } catch {
        docker network create $Name | Out-Null
    }
}

function Test-RedisContainerHealth {
    param([int]$Attempts = 30, [int]$DelayMilliseconds = 500)
    $password = Get-EnvValue -FilePath $envFile -Key $redisPasswordKey
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $output = docker exec $redisContainerName redis-cli --no-auth-warning -a $password ping 2>$null
            if (($output | Out-String) -match "PONG") {
                return $true
            }
        } catch {
        }
        Start-Sleep -Milliseconds $DelayMilliseconds
    }
    return $false
}

function Ensure-RedisContainer {
    Ensure-RedisEnvValues
    Ensure-DockerNetwork -Name $redisNetworkName
    $password = Get-EnvValue -FilePath $envFile -Key $redisPasswordKey
    $hasRedis = $false
    try {
        docker container inspect $redisContainerName *> $null
        $hasRedis = $true
    } catch {
        $hasRedis = $false
    }
    if ($hasRedis -and (Test-RedisContainerHealth -Attempts 3 -DelayMilliseconds 300)) {
        return
    }
    if ($hasRedis) {
        Remove-ContainerIfExists -Name $redisContainerName
    }
    docker run -d `
        --name $redisContainerName `
        --network $redisNetworkName `
        -p "127.0.0.1:${redisHostPort}:6379" `
        --restart unless-stopped `
        -v "${redisContainerName}_data:/data" `
        $redisImage `
        redis-server --requirepass $password --appendonly yes --maxmemory (Get-EnvValueOrDefault -FilePath $envFile -Key "REDIS_MAXMEMORY" -DefaultValue "512mb") --maxmemory-policy (Get-EnvValueOrDefault -FilePath $envFile -Key "REDIS_MAXMEMORY_POLICY" -DefaultValue "allkeys-lru") | Out-Null
    if (-not (Test-RedisContainerHealth)) {
        throw "Redis health check failed for $redisContainerName"
    }
    Write-Host "Redis OK: $redisContainerName on 127.0.0.1:$redisHostPort"
}

function Invoke-HospitalImageFolderNormalization {
    $imagePath = Resolve-HostPath -PathValue (Get-EnvValue -FilePath $envFile -Key "HOSPITAL_IMAGE_HOST_PATH") -DefaultValue "./runtime-image"
    if (-not (Test-Path $imagePath)) {
        New-Item -ItemType Directory -Path $imagePath | Out-Null
    }
    $rootPath = (Resolve-Path $imagePath).Path

    Get-ChildItem -LiteralPath $rootPath -Directory | ForEach-Object {
        $legacyDir = $_
        $legacyDirPath = $legacyDir.FullName
        if ($legacyDir.Name -like "*_*") { return }

        $legacyLogoDir = Join-Path $legacyDirPath "LOGO"
        if (-not (Test-Path $legacyLogoDir)) { return }

        $candidates = @(Get-ChildItem -LiteralPath $rootPath -Directory -Filter "$($legacyDir.Name)_*" | Sort-Object Name)
        if ($candidates.Count -ne 1) {
            Write-Host "Skip $($legacyDir.Name): expected one canonical $($legacyDir.Name)_* folder, found $($candidates.Count)"
            return
        }

        $canonicalLogoDir = Join-Path $candidates[0].FullName "LOGO"
        if (-not (Test-Path $canonicalLogoDir)) {
            New-Item -ItemType Directory -Path $canonicalLogoDir | Out-Null
        }

        Get-ChildItem -LiteralPath $legacyLogoDir -File | ForEach-Object {
            $targetPath = Join-Path $canonicalLogoDir $_.Name
            if (Test-Path $targetPath) {
                $baseName = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)
                $extension = [System.IO.Path]::GetExtension($_.Name)
                $targetPath = Join-Path $canonicalLogoDir ("{0}-migrated-{1}{2}" -f $baseName, ([Guid]::NewGuid().ToString("N").Substring(0, 8)), $extension)
            }
            Move-Item -LiteralPath $_.FullName -Destination $targetPath
            Write-Host "Moved $($legacyDir.Name)/LOGO/$($_.Name) -> $($candidates[0].Name)/LOGO/$(Split-Path $targetPath -Leaf)"
        }

        try {
            if (Test-Path -LiteralPath $legacyLogoDir) {
                Remove-Item -LiteralPath $legacyLogoDir -Force -ErrorAction SilentlyContinue
            }
            if (Test-Path -LiteralPath $legacyDirPath) {
                Remove-Item -LiteralPath $legacyDirPath -Force -ErrorAction SilentlyContinue
            }
        } catch {
            Write-Host "Skip removing legacy image folder $($legacyDir.Name): $($_.Exception.Message)"
        }
    }

    if (Get-Command chown -ErrorAction SilentlyContinue) {
        try { & chown -R "10001:10001" $rootPath | Out-Null } catch {}
    }
}

function Remove-LegacyLiveAliasIfNeeded {
    foreach ($name in @("udaya_pacs_api_local", "udaya_pacs_api")) {
        if ($name -ne $serviceName) {
            Remove-ContainerIfExists -Name $name
        }
    }
}

function Start-ApiContainer {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$HostPort,
        [switch]$RestartUnlessStopped,
        [switch]$PublicBind,
        [string]$ImageOverride = ""
    )

    Remove-ContainerIfExists -Name $Name

    $keyPath = Resolve-HostPath -PathValue (Get-EnvValue -FilePath $envFile -Key "KEY_PATH") -DefaultValue "./src/main/resources/key"
    $imagePath = Resolve-HostPath -PathValue (Get-EnvValue -FilePath $envFile -Key "HOSPITAL_IMAGE_HOST_PATH") -DefaultValue "./runtime-image"
    $resolvedImage = if ([string]::IsNullOrWhiteSpace($ImageOverride)) { $imageName } else { $ImageOverride }
    $dicomUploadMount = ""
    if ($Target -eq "local") {
        $composeProject = Get-EnvValueOrDefault -FilePath $envFile -Key "API_COMPOSE_PROJECT_NAME" -DefaultValue "udaya_pacs_api"
        $dicomUploadVolume = "${composeProject}_udaya_pacs_local_dicom_upload_temp"
        docker volume create $dicomUploadVolume | Out-Null
        & docker run --rm --user 0 -v "${dicomUploadVolume}:/var/ut-dicom-upload-temp" --entrypoint sh $resolvedImage -c "chown -R 10001:10001 /var/ut-dicom-upload-temp" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Unable to initialize local DICOM upload volume $dicomUploadVolume." }
        $dicomUploadMount = "${dicomUploadVolume}:/var/ut-dicom-upload-temp"
    } else {
        $dicomUploadTempPath = Resolve-HostPath -PathValue (Get-EnvValue -FilePath $envFile -Key "PACS_DICOM_UPLOAD_TEMP_HOST_PATH") -DefaultValue "../runtime-dicom-upload-temp"
        Assert-DicomUploadTempPath -PathValue $dicomUploadTempPath
        if (-not (Test-Path $dicomUploadTempPath)) {
            New-Item -ItemType Directory -Path $dicomUploadTempPath | Out-Null
        }
        if (Get-Command chown -ErrorAction SilentlyContinue) {
            try { & chown -R "10001:10001" $dicomUploadTempPath | Out-Null } catch {}
        }
        $dicomUploadMount = "${dicomUploadTempPath}:/var/ut-dicom-upload-temp"
    }
    if (-not (Test-Path $imagePath)) {
        New-Item -ItemType Directory -Path $imagePath | Out-Null
    }
    if (Get-Command chown -ErrorAction SilentlyContinue) {
        try { & chown -R "10001:10001" $imagePath | Out-Null } catch {}
    }
    Ensure-DockerNetwork -Name $redisNetworkName

    $baseImageArchiveHostPath = Resolve-HostPath -PathValue (Get-EnvValue -FilePath $envFile -Key "PACS_DICOM_SERVER_BASE_IMAGE_ARCHIVE_HOST_PATH") -DefaultValue "./dicom-server-images/dicom_server_base.tar"
    $baseImageArchiveContainerPath = Get-EnvValueOrDefault -FilePath $envFile -Key "PACS_DICOM_SERVER_BASE_IMAGE_ARCHIVE_CONTAINER_PATH" -DefaultValue "/app/dicom-server-images/dicom_server_base.tar"
    $restartPolicy = if ($RestartUnlessStopped) { "unless-stopped" } else { "no" }
    $redisKeyPrefixDefault = "udaya_pacs_${Target}"
    $envPairs = @(
        "SPRING_PROFILES_ACTIVE=$Target",
        "APP_CACHE_PROVIDER=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'APP_CACHE_PROVIDER' -DefaultValue 'redis')",
        "APP_CACHE_TTL_SECONDS=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'APP_CACHE_TTL_SECONDS' -DefaultValue '600')",
        "APP_CACHE_MAX_ENTRIES=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'APP_CACHE_MAX_ENTRIES' -DefaultValue '5000')",
        "APP_CACHE_REDIS_KEY_PREFIX=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'APP_CACHE_REDIS_KEY_PREFIX' -DefaultValue $redisKeyPrefixDefault)",
        "APP_CACHE_REDIS_HEALTH_ENABLED=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'APP_CACHE_REDIS_HEALTH_ENABLED' -DefaultValue 'true')",
        "REDIS_HOST=$redisContainerName",
        "REDIS_PORT=$redisPort",
        "REDIS_PASSWORD=$(Get-TargetEnvValue -Suffix 'REDIS_PASSWORD' -FallbackKey 'REDIS_PASSWORD')",
        "REDIS_DATABASE=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'REDIS_DATABASE' -DefaultValue '0')",
        "REDIS_TIMEOUT=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'REDIS_TIMEOUT' -DefaultValue '2s')",
        "PACS_CACHE_SCHEDULER_ENABLED=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_CACHE_SCHEDULER_ENABLED' -DefaultValue 'true')",
        "PACS_PARTITION_SCHEDULER_ENABLED=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_PARTITION_SCHEDULER_ENABLED' -DefaultValue 'true')",
        "SPRING_DATASOURCE_URL=$(Get-TargetEnvValue -Suffix 'SPRING_DATASOURCE_URL' -FallbackKey 'SPRING_DATASOURCE_URL')",
        "SPRING_DATASOURCE_USERNAME=$(Get-TargetEnvValue -Suffix 'SPRING_DATASOURCE_USERNAME' -FallbackKey 'SPRING_DATASOURCE_USERNAME')",
        "SPRING_DATASOURCE_PASSWORD=$(Get-TargetEnvValue -Suffix 'SPRING_DATASOURCE_PASSWORD' -FallbackKey 'SPRING_DATASOURCE_PASSWORD')",
        "SECURITY_JWT_REFRESH_TOKEN_ENCRYPTION_KEY=$(Get-TargetEnvValue -Suffix 'SECURITY_JWT_REFRESH_TOKEN_ENCRYPTION_KEY' -FallbackKey 'SECURITY_JWT_REFRESH_TOKEN_ENCRYPTION_KEY')",
        "SECURITY_JWT_PRIVATE_KEY=$(Get-EnvValue -FilePath $envFile -Key 'SECURITY_JWT_PRIVATE_KEY')",
        "SECURITY_JWT_PUBLIC_KEY=$(Get-EnvValue -FilePath $envFile -Key 'SECURITY_JWT_PUBLIC_KEY')",
        "SECURITY_JWT_KEY_ID=$(Get-EnvValue -FilePath $envFile -Key 'SECURITY_JWT_KEY_ID')",
        "HOSPITAL_IMAGE_ROOT_PATH=$(Get-EnvValue -FilePath $envFile -Key 'HOSPITAL_IMAGE_ROOT_PATH')",
        "PACS_DICOM_UPLOAD_TEMP_DIR=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_DICOM_UPLOAD_TEMP_DIR' -DefaultValue '/var/ut-dicom-upload-temp')",
        "SPRING_SERVLET_MULTIPART_LOCATION=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'SPRING_SERVLET_MULTIPART_LOCATION' -DefaultValue '/var/ut-dicom-upload-temp')",
        "PACS_DICOM_UPLOAD_INSTANCE_PARALLELISM=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_DICOM_UPLOAD_INSTANCE_PARALLELISM' -DefaultValue '24')",
        "PACS_DICOM_UPLOAD_MAX_CONCURRENT_PROCESSING_JOBS=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_DICOM_UPLOAD_MAX_CONCURRENT_PROCESSING_JOBS' -DefaultValue '1')",
        "PACS_DICOM_UPLOAD_INSTANCE_MAX_ATTEMPTS=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_DICOM_UPLOAD_INSTANCE_MAX_ATTEMPTS' -DefaultValue '8')",
        "PACS_DICOM_UPLOAD_INSTANCE_RETRY_BACKOFF_MS=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_DICOM_UPLOAD_INSTANCE_RETRY_BACKOFF_MS' -DefaultValue '500')",
        "PACS_DICOM_UPLOAD_IN_MEMORY_ENTRY_MAX_BYTES=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_DICOM_UPLOAD_IN_MEMORY_ENTRY_MAX_BYTES' -DefaultValue '1048576')",
        "APP_SECURITY_DICOM_UPLOAD_MAX_REQUEST_BYTES=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'APP_SECURITY_DICOM_UPLOAD_MAX_REQUEST_BYTES' -DefaultValue '4294967296')",
        "APP_SECURITY_DICOM_UPLOAD_MAX_TRANSPORT_REQUEST_BYTES=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'APP_SECURITY_DICOM_UPLOAD_MAX_TRANSPORT_REQUEST_BYTES' -DefaultValue '4362076160')",
        "SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE' -DefaultValue '4GB')",
        "SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE' -DefaultValue '4160MB')",
        "PACS_DICOM_SERVER_CLIENT_READ_TIMEOUT_MS=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_DICOM_SERVER_CLIENT_READ_TIMEOUT_MS' -DefaultValue '7200000')",
        "DICOM_SERVER_HEALTH_TIMEOUT_MS=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'DICOM_SERVER_HEALTH_TIMEOUT_MS' -DefaultValue '5000')",
        "DICOM_SERVER_HEALTH_OFFLINE_FAILURE_THRESHOLD=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'DICOM_SERVER_HEALTH_OFFLINE_FAILURE_THRESHOLD' -DefaultValue '3')",
        "DICOM_SERVER_HEALTH_OFFLINE_GRACE_MS=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'DICOM_SERVER_HEALTH_OFFLINE_GRACE_MS' -DefaultValue '180000')",
        "PACS_RESULT_STATIC_AUTH_ENABLED=$(Get-EnvValue -FilePath $envFile -Key 'PACS_RESULT_STATIC_AUTH_ENABLED')",
        "PACS_RESULT_API_KEY=$(Get-EnvValue -FilePath $envFile -Key 'PACS_RESULT_API_KEY')",
        "PACS_RESULT_UPLOAD_ROOT=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_RESULT_UPLOAD_ROOT' -DefaultValue '/var/ut-image')",
        "PACS_RESULT_MAX_IMAGE_BYTES=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_RESULT_MAX_IMAGE_BYTES' -DefaultValue '10485760')",
        "PACS_DICOM_SERVER_PACKAGE_INCLUDE_BASE_IMAGE=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_DICOM_SERVER_PACKAGE_INCLUDE_BASE_IMAGE' -DefaultValue 'false')",
        "PACS_DICOM_SERVER_PACKAGE_BASE_IMAGE_ARCHIVE_PATH=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_DICOM_SERVER_PACKAGE_BASE_IMAGE_ARCHIVE_PATH' -DefaultValue $baseImageArchiveContainerPath)",
        "PACS_DICOM_SERVER_PACKAGE_MAX_EMBEDDED_BASE_IMAGE_BYTES=$(Get-EnvValueOrDefault -FilePath $envFile -Key 'PACS_DICOM_SERVER_PACKAGE_MAX_EMBEDDED_BASE_IMAGE_BYTES' -DefaultValue '67108864')",
        "SPRING_MAIL_USERNAME=$(Get-TargetEnvValue -Suffix 'SPRING_MAIL_USERNAME')",
        "SPRING_MAIL_PASSWORD=$(Get-TargetEnvValue -Suffix 'SPRING_MAIL_PASSWORD')",
        "API_AUTH_URL=$(Get-TargetEnvValue -Suffix 'API_AUTH_URL' -FallbackKey 'API_AUTH_URL')",
        "SPRINGDOC_SERVER_URL=$(Get-TargetEnvValue -Suffix 'SPRINGDOC_SERVER_URL' -FallbackKey 'SPRINGDOC_SERVER_URL')",
        "CORS_ALLOWED_ORIGINS=$(Get-EnvValue -FilePath $envFile -Key 'CORS_ALLOWED_ORIGINS')",
        "CORS_ALLOW_CREDENTIALS=$(Get-EnvValue -FilePath $envFile -Key 'CORS_ALLOW_CREDENTIALS')",
        "APP_SECURITY_CLIENT_ALLOW_PATHS=$(Get-EnvValue -FilePath $envFile -Key 'APP_SECURITY_CLIENT_ALLOW_PATHS')",
        "APP_SECURITY_CLIENT_ALLOW_CLIENT_IDS=$(Get-EnvValue -FilePath $envFile -Key 'APP_SECURITY_CLIENT_ALLOW_CLIENT_IDS')",
        "TELEGRAM_CHAT_ID=$(Get-TargetEnvValue -Suffix 'TELEGRAM_CHAT_ID')",
        "TELEGRAM_API_TOKEN=$(Get-TargetEnvValue -Suffix 'TELEGRAM_API_TOKEN')",
        "TZ=$(Get-EnvValue -FilePath $envFile -Key 'TZ')"
    )

    $livePortBinding = "${bindHost}:${HostPort}:8080"
    $portBinding = if ($PublicBind) { $livePortBinding } else { "127.0.0.1:${HostPort}:8080" }

    $args = @(
        "run", "-d",
        "--name", $Name,
        "-p", $portBinding,
        "--restart", $restartPolicy,
        "--init",
        "--read-only",
        "--cap-drop", "ALL",
        "--security-opt", "no-new-privileges:true",
        "--network", $redisNetworkName,
        "--tmpfs", "/tmp:size=64m,mode=1777",
        "-v", "${keyPath}:/app/config/key:ro",
        "-v", "${imagePath}:/var/ut-image",
        "-v", $dicomUploadMount
    )
    if (Test-Path -LiteralPath $baseImageArchiveHostPath) {
        $args += @("-v", "${baseImageArchiveHostPath}:${baseImageArchiveContainerPath}:ro")
    }
    $localApiDockerIp = Get-EnvValue -FilePath $envFile -Key "LOCAL_API_DOCKER_IP"
    if ($Target -eq "local" -and $PublicBind -and -not [string]::IsNullOrWhiteSpace($localApiDockerIp)) {
        $args += @("--ip", $localApiDockerIp)
    }
    foreach ($pair in $envPairs) {
        $idx = $pair.IndexOf("=")
        if ($idx -gt 0 -and $pair.Substring($idx + 1).Length -gt 0) {
            $args += @("-e", $pair)
        }
    }
    $args += $resolvedImage
    & docker @args | Out-Null
}

function Test-ApiHealth {
    param([int]$Attempts = 60, [int]$DelayMilliseconds = 1000)

    $url = "http://127.0.0.1:$port/pacsApi/actuator/health"
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                Write-Host "API OK: $url"
                return
            }
        } catch {
            if ($attempt -eq $Attempts) {
                throw "API health check failed for $url. $($_.Exception.Message)"
            }
        }
        Start-Sleep -Milliseconds $DelayMilliseconds
    }
}

function Test-PublicApiHealth {
    param([int]$Attempts = 5, [int]$DelayMilliseconds = 1000)

    $url = Get-PublicApiHealthUrl
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                Write-Host "API OK: $url"
                return
            }
        } catch {
            if ($attempt -eq $Attempts) {
                throw "API health check failed for $url. $($_.Exception.Message)"
            }
        }
        Start-Sleep -Milliseconds $DelayMilliseconds
    }
}

function Show-ContainerFailure {
    param([string]$Name)
    Write-Warning "Container diagnostics: $Name"
    try {
        docker ps -a --filter "name=^/$Name$" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
    } catch {
        Write-Warning $_.Exception.Message
    }
    Write-Warning "Last 200 container log lines:"
    $logs = ""
    try {
        $logs = docker logs --tail 200 $Name 2>&1 | Out-String
        Protect-DeployText $logs
    } catch {
        Write-Warning $_.Exception.Message
    }
    if ($logs -like "*password authentication failed for user*") {
        Write-Warning @"
Detected DB password authentication failure.
On the DB server, run this from the DB deploy folder:
  cd /var/www/udaya_pacs_qa_db
  sudo bash ./scripts/deploy-db.sh

Then redeploy the API:
  cd /var/www/udaya_pacs_qa_api
  sudo bash ./scripts/stack.sh qa deploy --no-build
"@
    }
}

Show-LocalDockerPsSnapshot

switch ($Action) {
    "db-backup" {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "db-admin.ps1") -Action backup -Target $Target
    }
    "db-migrate" {
        $dbArgs = @(
            "-ExecutionPolicy", "Bypass",
            "-File", (Join-Path $PSScriptRoot "db-admin.ps1"),
            "-Action", "migrate",
            "-Target", $Target
        )
        if ($Build) { $dbArgs += "-Build" }
        if ($NoBuild) { $dbArgs += "-NoBuild" }
        & powershell @dbArgs
    }
    "db-validate" {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "db-admin.ps1") -Action validate -Target $Target
    }
    "db-refresh-cache" {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "db-admin.ps1") -Action refresh-cache -Target $Target
    }
    "db-partition-maintenance" {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "db-admin.ps1") -Action partition-maintenance -Target $Target
    }
    "up" {
        $args = @("-ExecutionPolicy", "Bypass", "-File", $PSCommandPath, "-Target", $Target, "-Action", "deploy")
        if ($Build) { $args += "-Build" }
        if ($NoBuild) { $args += "-NoBuild" }
        & powershell @args
    }
    "down" {
        Remove-ContainerIfExists -Name $serviceName
        Remove-ContainerIfExists -Name "${serviceName}_tmp"
        Remove-ContainerIfExists -Name $redisContainerName
        docker ps -a --format "{{.Names}}" |
            Where-Object { $_ -like "${serviceName}_rollback_*" } |
            ForEach-Object {
                Remove-ContainerIfExists -Name $_
            }
    }
    "restart" {
        & powershell -ExecutionPolicy Bypass -File $PSCommandPath -Target $Target -Action down
        & powershell -ExecutionPolicy Bypass -File $PSCommandPath -Target $Target -Action deploy -NoBuild
    }
    "deploy" {
        $deployFailureContainerName = $serviceName
        $deployFailureLine = "deploy"
        try {
            $tmpName = "${serviceName}_tmp"

            Test-TargetNetworkConfig
            Test-DatabaseEndpoint
            Ensure-RedisContainer
            Invoke-HospitalImageFolderNormalization

            if ($Build -and -not $NoBuild) {
                Invoke-EndpointGate
                Invoke-Compose -ComposeArgs @("build")
            }

            Test-DatabaseEndpointFromApiNetwork
            Write-Host "Testing API tmp container $tmpName on 127.0.0.1:$candidatePort..."
            Start-ApiContainer -Name $tmpName -HostPort $candidatePort
            try {
                $oldPort = $port
                $port = $candidatePort
                try {
                    Test-ApiHealth
                } catch {
                    Show-ContainerFailure -Name $tmpName
                    $deployFailureContainerName = $tmpName
                    $deployFailureLine = "tmp-health"
                    throw
                }
            } finally {
                $port = $oldPort
            }

            Write-Host "Tmp is healthy. Promoting $serviceName..."
            Remove-LegacyLiveAliasIfNeeded
            $backupName = "${serviceName}_rollback_$(Get-Date -Format yyyyMMddHHmmss)"
            $hadLive = $false
            try {
                docker container inspect $serviceName *> $null
                $hadLive = $true
            } catch {
                $hadLive = $false
            }
            if ($hadLive) {
                docker stop $serviceName *> $null
                docker rename $serviceName $backupName
            }

            try {
                Start-ApiContainer -Name $serviceName -HostPort $port -RestartUnlessStopped -PublicBind -ImageOverride $imageName
                Test-PublicApiHealth -Attempts 60
                Remove-ContainerIfExists -Name $tmpName
                if ($hadLive) {
                    Remove-ContainerIfExists -Name $backupName
                }
            } catch {
                Write-Warning "Promotion failed. Attempting rollback to previous API container."
                Remove-ContainerIfExists -Name $serviceName
                if ($hadLive) {
                    docker rename $backupName $serviceName
                    docker start $serviceName | Out-Null
                    Test-PublicApiHealth -Attempts 30
                }
                throw
            } finally {
                Remove-ContainerIfExists -Name $tmpName
            }
            Send-DeployNotification -Status "SUCCESS" -Summary "API deployed and health check passed."
        } catch {
            Send-DeployNotification -Status "FAILED" -Summary $_.Exception.Message -Line $deployFailureLine -ContainerName $deployFailureContainerName
            throw
        }
    }
    "logs" {
        & docker logs -f --tail 200 $serviceName
    }
    "ps" {
        Invoke-Compose -ComposeArgs @("ps")
    }
    "health" {
        if (-not (Test-RedisContainerHealth -Attempts 5 -DelayMilliseconds 500)) {
            throw "Redis health check failed for $redisContainerName"
        }
        Write-Host "Redis OK: $redisContainerName"
        Test-PublicApiHealth -Attempts 5
    }
}
