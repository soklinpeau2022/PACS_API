Param(
    [ValidateSet("local", "qa", "prod")]
    [string]$Target,
    [switch]$SkipTests,
    [switch]$SkipPentest
)

$ErrorActionPreference = "Stop"
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

[xml]$pom = Get-Content (Join-Path $projectRoot "pom.xml")
$projectVersion = $pom.project.version
if ([string]::IsNullOrWhiteSpace($projectVersion)) {
    throw "Failed to resolve project version from pom.xml"
}

$distRoot = Join-Path $projectRoot "dist"
$bundleName = "udaya_pacs_${Target}_api"
$bundleDir = Join-Path $distRoot $bundleName
$imageName = "udaya_pacs_${Target}_api`:latest"
$imageTar = "udaya_pacs_${Target}_api.tar"
$envFileSource = ""
$envFileName = ""

function Copy-LinuxScript {
    Param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    $content = [System.IO.File]::ReadAllText($Source)
    $content = $content -replace "`r`n", "`n"
    $content = $content -replace "`r", "`n"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Destination, $content, $utf8NoBom)
}

function Set-EnvValue {
    Param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$Value
    )

    if (Select-String -Path $FilePath -Pattern "^$([regex]::Escape($Key))=" -Quiet) {
        (Get-Content $FilePath) -replace "^$([regex]::Escape($Key))=.*$", "$Key=$Value" | Set-Content $FilePath
    } else {
        Add-Content -Path $FilePath -Value "$Key=$Value"
    }
}

function Get-EnvValue {
    Param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string]$Key
    )

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

if ([string]::IsNullOrWhiteSpace($Target)) {
    throw "Target is required. Use -Target local|qa|prod"
}

switch ($Target) {
    "qa" {
        $envFileSource = Join-Path $projectRoot ".env.qa"
        $envFileName = ".env.qa"
    }
    "prod" {
        $envFileSource = Join-Path $projectRoot ".env.prod"
        $envFileName = ".env.prod"
    }
    "local" {
        $localEnv = Join-Path $projectRoot ".env.local"
        if (Test-Path $localEnv) {
            $envFileSource = $localEnv
            $envFileName = ".env.local"
        } else {
            $envFileSource = Join-Path $projectRoot ".env"
            $envFileName = ".env"
        }
    }
}

if (-not (Test-Path $envFileSource)) {
    throw "Required env file not found for target '$Target': $envFileSource. Create it with real values before packaging."
}

$targetUpper = $Target.ToUpperInvariant()
$apiBaseUrl = Get-EnvValue -FilePath $envFileSource -Key "${targetUpper}_API_AUTH_URL"
if ([string]::IsNullOrWhiteSpace($apiBaseUrl)) {
    $apiBaseUrl = Get-EnvValue -FilePath $envFileSource -Key "API_AUTH_URL"
}
if ([string]::IsNullOrWhiteSpace($apiBaseUrl)) {
    $apiBaseUrl = "http://localhost:8080/pacsApi"
}
$healthUrl = $apiBaseUrl.TrimEnd("/") + "/actuator/health"

if (Test-Path $bundleDir) {
    Remove-Item -Recurse -Force $bundleDir
}
New-Item -ItemType Directory -Path $bundleDir | Out-Null
New-Item -ItemType Directory -Path (Join-Path $bundleDir "artifact") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $bundleDir "scripts") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $bundleDir "runtime-image") | Out-Null

if (-not $SkipPentest) {
    powershell -ExecutionPolicy Bypass -File .\scripts\test-gate.ps1 -Target $Target -Tag $projectVersion -Context "package-deploy"
}

Write-Host "Building JAR..."
if ($SkipTests) {
    ./mvnw -q clean package -DskipTests
} else {
    ./mvnw -q clean package
}

$jar = Get-ChildItem -Path (Join-Path $projectRoot "target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } |
    Select-Object -First 1

if (-not $jar) {
    throw "No JAR produced in target/."
}
Copy-Item $jar.FullName (Join-Path $bundleDir "artifact\app.jar")

Write-Host "Building Docker image $imageName..."
docker build -t $imageName .

Write-Host "Exporting Docker image..."
docker save -o (Join-Path $bundleDir $imageTar) $imageName

Copy-Item (Join-Path $projectRoot "docker-compose.runtime.yml") (Join-Path $bundleDir "docker-compose.yml")
Copy-Item (Join-Path $projectRoot "docker-compose.db.yml") (Join-Path $bundleDir "docker-compose.db.yml")
Copy-Item (Join-Path $projectRoot ".env.db.example") (Join-Path $bundleDir ".env.db.example")
$bundleEnvPath = Join-Path $bundleDir $envFileName
Copy-Item $envFileSource $bundleEnvPath
if (Select-String -Path $bundleEnvPath -Pattern '^APP_IMAGE=' -Quiet) {
    (Get-Content $bundleEnvPath) -replace '^APP_IMAGE=.*$', ("APP_IMAGE=" + $imageName) | Set-Content $bundleEnvPath
} else {
    Add-Content -Path $bundleEnvPath -Value ("APP_IMAGE=" + $imageName)
}
$containerKey = "$($Target.ToUpperInvariant())_API_CONTAINER_NAME"
Set-EnvValue -FilePath $bundleEnvPath -Key $containerKey -Value $bundleName
Set-EnvValue -FilePath $bundleEnvPath -Key "API_COMPOSE_PROJECT_NAME" -Value $bundleName
Copy-LinuxScript (Join-Path $projectRoot "scripts\stack.sh") (Join-Path $bundleDir "scripts\stack.sh")
Copy-LinuxScript (Join-Path $projectRoot "scripts\normalize-hospital-image-folders.sh") (Join-Path $bundleDir "scripts\normalize-hospital-image-folders.sh")
Copy-Item (Join-Path $projectRoot "scripts\stack.ps1") (Join-Path $bundleDir "scripts\stack.ps1")

$deployReadme = @'
# UDAYA_PACS_API Deploy Bundle

This bundle runs UDAYA_PACS_API with Docker. It does not include PostgreSQL.
Run PostgreSQL on the DB server or point `__ENV_FILE__` to an existing PostgreSQL host.

## 1) Prepare Ubuntu Folders
```bash
sudo mkdir -p /var/www/__BUNDLE_NAME__ /var/ut-image /var/ut-key
```

## 2) Review Env

Open `__ENV_FILE__` and confirm:

```env
KEY_PATH=/var/ut-key
HOSPITAL_IMAGE_HOST_PATH=/var/ut-image
HOSPITAL_IMAGE_ROOT_PATH=/var/ut-image
SECURITY_JWT_PRIVATE_KEY=file:/app/config/key/private_key.pem
SECURITY_JWT_PUBLIC_KEY=file:/app/config/key/public_key.pem
PACS_RESULT_STATIC_AUTH_ENABLED=true
```

`scripts/stack.sh` auto-corrects bad key paths, auto-creates `/var/ut-key/private_key.pem` and `/var/ut-key/public_key.pem` if missing, and sets owner `10001:10001` on Linux.
`/var/ut-key` is the normal QA/PROD key path. For disposable smoke tests or custom installs only, run with `DEPLOY_KEY_PATH=/some/linux/path`.

Required QA values:

```env
QA_API_PORT=8080
QA_SPRING_DATASOURCE_URL=jdbc:postgresql://192.168.8.11:5432/emr_pacs_db
QA_SPRING_DATASOURCE_USERNAME=pacs_app_qa
QA_SPRING_DATASOURCE_PASSWORD=<db password>
QA_API_AUTH_URL=http://192.168.8.10:8080/pacsApi
QA_SPRINGDOC_SERVER_URL=http://192.168.8.10:8080/pacsApi
CORS_ALLOWED_ORIGINS=http://192.168.8.10:4173,http://192.168.8.10:3005
```

If `QA_SECURITY_JWT_REFRESH_TOKEN_ENCRYPTION_KEY`, `SECURITY_JWT_KEY_ID`, or `PACS_RESULT_API_KEY` is empty or placeholder, the deploy script generates it and writes it into `__ENV_FILE__`.

If Viewer is deployed, Viewer `.env.viewer` must use the same `PACS_RESULT_API_KEY`.

## 3) Load Docker Image
```bash
sudo docker load -i ./__IMAGE_TAR__
```

## 4) Normalize Hospital Image Folders
Deploy runs this automatically. You can also run it manually after copying old files:

```bash
sudo bash ./scripts/normalize-hospital-image-folders.sh /var/ut-image
sudo find /var/ut-image -maxdepth 3 -type d | sort
```

Expected structure:

```text
/var/ut-image/H001_UDAYA_HOSPITAL/LOGO
/var/ut-image/H001_UDAYA_HOSPITAL/CT_COMPUTED_TOMOGRAPHY
/var/ut-image/H001_UDAYA_HOSPITAL/MR_MAGNETIC_RESONANCE_IMAGING
```

## 5) Deploy
```bash
sudo bash ./scripts/stack.sh __TARGET__ deploy --no-build
sudo bash ./scripts/stack.sh __TARGET__ health
```

## 6) Health Check
```bash
curl -fsS __HEALTH_URL__
sudo bash ./scripts/stack.sh __TARGET__ logs
```

## 7) Stop
```bash
sudo bash ./scripts/stack.sh __TARGET__ down
```
'@
$deployReadme = $deployReadme.Replace("__IMAGE_TAR__", $imageTar)
$deployReadme = $deployReadme.Replace("__ENV_FILE__", $envFileName)
$deployReadme = $deployReadme.Replace("__BUNDLE_NAME__", $bundleName)
$deployReadme = $deployReadme.Replace("__TARGET__", $Target)
$deployReadme = $deployReadme.Replace("__HEALTH_URL__", $healthUrl)

Set-Content -Path (Join-Path $bundleDir "README-DEPLOY.txt") -Value $deployReadme -NoNewline

$zipPath = Join-Path $distRoot "$bundleName.zip"
if (Test-Path $zipPath) {
    Remove-Item -Force $zipPath
}
$previousProgressPreference = $ProgressPreference
$ProgressPreference = "SilentlyContinue"
try {
    Compress-Archive -Path (Join-Path $bundleDir "*") -DestinationPath $zipPath -Force
} finally {
    $ProgressPreference = $previousProgressPreference
}

Write-Host "Deploy bundle ready:"
Write-Host $zipPath
