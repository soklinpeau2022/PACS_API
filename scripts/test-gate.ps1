Param(
    [ValidateSet("local", "qa", "prod")]
    [string]$Target,
    [string]$Tag = "latest",
    [string]$Context = "build"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Target)) {
    throw "Target is required. Use -Target local|qa|prod"
}

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

function Get-EnvValue {
    param([string]$FilePath, [string]$Key)
    if (-not (Test-Path $FilePath)) { return "" }
    $line = Select-String -Path $FilePath -Pattern "^\s*$Key\s*=\s*(.*)\s*$" | Select-Object -First 1
    if (-not $line) { return "" }
    return $line.Matches[0].Groups[1].Value.Trim()
}

function Resolve-TelegramCreds {
    param([string]$Profile)
    $envFile = switch ($Profile) {
        "qa" { ".env.qa" }
        "prod" { ".env.prod" }
        default { if (Test-Path ".env.local") { ".env.local" } else { ".env" } }
    }

    $token = ""
    $chatId = ""
    switch ($Profile) {
        "qa" {
            $token = Get-EnvValue -FilePath $envFile -Key "QA_TELEGRAM_API_TOKEN"
            $chatId = Get-EnvValue -FilePath $envFile -Key "QA_TELEGRAM_CHAT_ID"
        }
        "prod" {
            $token = Get-EnvValue -FilePath $envFile -Key "PROD_TELEGRAM_API_TOKEN"
            $chatId = Get-EnvValue -FilePath $envFile -Key "PROD_TELEGRAM_CHAT_ID"
        }
        default {
            $token = Get-EnvValue -FilePath $envFile -Key "TELEGRAM_API_TOKEN"
            $chatId = Get-EnvValue -FilePath $envFile -Key "TELEGRAM_CHAT_ID"
        }
    }
    return @{ Token = $token; ChatId = $chatId }
}

function Send-TelegramAlert {
    param([string]$Status, [string]$Details)
    $creds = Resolve-TelegramCreds -Profile $Target
    if ([string]::IsNullOrWhiteSpace($creds.Token) -or [string]::IsNullOrWhiteSpace($creds.ChatId)) { return }

    $text = "<b>UDAYA_PACS_API Gate $Status</b>`n<b>Target:</b> $Target`n<b>Tag:</b> $Tag`n<b>Context:</b> $Context`n<b>Details:</b> $Details"
    try {
        Invoke-RestMethod -Method Post -Uri ("https://api.telegram.org/bot" + $creds.Token + "/sendMessage") -Body @{
            chat_id    = $creds.ChatId
            parse_mode = "HTML"
            text       = $text
        } | Out-Null
    } catch {
        # Do not fail build because Telegram notification fails
    }
}

$tests = "EndpointPentestSmokeTest,SecurityThreatDetectionFilterTest,SecurityRateLimitFilterTest,GlobalRequestSizeLimitFilterTest,DicomUploadServiceImplTest,AuthServiceRefreshTokenTest,UserMapperXmlHardeningTest,RefreshTokenCryptoServiceTest,EndpointContractCoverageTest,ApiConstantsCoverageTest,MigrationSafetyPolicyTest,GlobalExceptionHandlerTest,SystemErrorAlertServiceTest,SecurityIncidentReporterTest,MyBatisSqlInjectionGuardInterceptorTest,ModulePermissionFilterTest,ActiveHospitalFilterTest,RevokedTokenFilterTest,RequestPayloadGuardTest,SqlSanitizerHelperTest"

Write-Host "Running endpoint/security gate tests..."
try {
    ./mvnw -q "-Dtest=$tests" test
    Send-TelegramAlert -Status "PASSED" -Details "Endpoint+security test gate passed"
    Write-Host "Gate passed."
} catch {
    Send-TelegramAlert -Status "FAILED" -Details "Endpoint+security test gate failed. Build blocked."
    throw
}
