Param(
    [ValidateSet("local", "qa", "prod")]
    [string]$Target = "qa",
    [switch]$WithImage,
    [switch]$NoImage,
    [switch]$NoData,
    [string]$SourceContainer,
    [string]$SourceDatabase,
    [string]$SourceUser
)

$ErrorActionPreference = "Stop"
if ($WithImage -and $NoImage) {
    throw "Use either -WithImage or -NoImage, not both."
}
$includeImage = -not $NoImage
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

function Get-DotEnvValue {
    Param(
        [string]$Path,
        [string]$Name
    )
    if (-not (Test-Path $Path)) {
        return $null
    }
    $line = Get-Content $Path | Where-Object { $_ -match "^$([regex]::Escape($Name))=" } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($line)) {
        return $null
    }
    return ($line -replace "^$([regex]::Escape($Name))=", "").Trim()
}

function Set-EnvValue {
    Param([string]$FilePath, [string]$Key, [string]$Value)
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

function Get-TargetEnvPath {
    Param([string]$TargetName)

    $candidates = switch ($TargetName) {
        "local" { @(".env.local", ".env") }
        "qa" { @(".env.qa") }
        "prod" { @(".env.prod") }
    }

    foreach ($candidate in $candidates) {
        $path = Join-Path $projectRoot $candidate
        if (Test-Path $path) {
            return $path
        }
    }
    return $null
}

function Apply-TargetDatabaseEnv {
    Param(
        [string]$TargetName,
        [string]$FilePath
    )

    $targetEnvPath = Get-TargetEnvPath $TargetName
    if (-not $targetEnvPath) {
        return
    }

    $targetUpper = $TargetName.ToUpperInvariant()
    $url = Get-DotEnvValue $targetEnvPath "$targetUpper`_SPRING_DATASOURCE_URL"
    $username = Get-DotEnvValue $targetEnvPath "$targetUpper`_SPRING_DATASOURCE_USERNAME"
    $password = Get-DotEnvValue $targetEnvPath "$targetUpper`_SPRING_DATASOURCE_PASSWORD"

    if ($TargetName -eq "local") {
        $localName = Get-DotEnvValue $targetEnvPath "LOCAL_DB_NAME"
        $localUser = Get-DotEnvValue $targetEnvPath "LOCAL_DB_USER"
        $localPassword = Get-DotEnvValue $targetEnvPath "LOCAL_DB_PASSWORD"
        $localPort = Get-DotEnvValue $targetEnvPath "LOCAL_DB_PORT"
        if (-not [string]::IsNullOrWhiteSpace($localName)) { Set-EnvValue -FilePath $FilePath -Key "PACS_DB_NAME" -Value $localName }
        if (-not [string]::IsNullOrWhiteSpace($localUser)) { Set-EnvValue -FilePath $FilePath -Key "PACS_DB_USER" -Value $localUser }
        if (-not [string]::IsNullOrWhiteSpace($localPassword)) { Set-EnvValue -FilePath $FilePath -Key "PACS_DB_PASSWORD" -Value $localPassword }
        if (-not [string]::IsNullOrWhiteSpace($localPort)) { Set-EnvValue -FilePath $FilePath -Key "PACS_DB_PORT" -Value $localPort }
        Set-EnvValue -FilePath $FilePath -Key "PACS_DB_BIND_ADDRESS" -Value "127.0.0.1"
        return
    }

    if ($url -match '^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/([^?]+)') {
        $dbHost = $Matches[1]
        $dbPort = if ($Matches[2]) { $Matches[2] } else { "5432" }
        $dbName = $Matches[3]
        Set-EnvValue -FilePath $FilePath -Key "PACS_DB_BIND_ADDRESS" -Value $dbHost
        Set-EnvValue -FilePath $FilePath -Key "PACS_DB_PORT" -Value $dbPort
        Set-EnvValue -FilePath $FilePath -Key "PACS_DB_NAME" -Value $dbName
    }
    if (-not [string]::IsNullOrWhiteSpace($username)) { Set-EnvValue -FilePath $FilePath -Key "PACS_DB_USER" -Value $username }
    if (-not [string]::IsNullOrWhiteSpace($password)) { Set-EnvValue -FilePath $FilePath -Key "PACS_DB_PASSWORD" -Value $password }
}

$envDbPath = Join-Path $projectRoot ".env.db"
if ([string]::IsNullOrWhiteSpace($SourceContainer)) {
    $SourceContainer = Get-DotEnvValue $envDbPath "PACS_DB_CONTAINER_NAME"
}
if ([string]::IsNullOrWhiteSpace($SourceDatabase)) {
    $SourceDatabase = Get-DotEnvValue $envDbPath "PACS_DB_NAME"
}
if ([string]::IsNullOrWhiteSpace($SourceUser)) {
    $SourceUser = Get-DotEnvValue $envDbPath "PACS_DB_USER"
}
if ([string]::IsNullOrWhiteSpace($SourceContainer)) { $SourceContainer = "pacs-db" }
if ([string]::IsNullOrWhiteSpace($SourceDatabase)) { $SourceDatabase = "emr_pacs_db" }
if ([string]::IsNullOrWhiteSpace($SourceUser)) { $SourceUser = "pacs_app_local_rw" }

$distRoot = Join-Path $projectRoot "dist"
$bundleName = "udaya_pacs_${Target}_db"
$bundleDir = Join-Path $distRoot $bundleName
$zipPath = Join-Path $distRoot "$bundleName.zip"
$postgresImage = "postgres:18"
$postgresTar = "postgres-18.tar"
$dumpName = "pacs-db.dump"
$containerDumpPath = "/tmp/$dumpName"

if (Test-Path $bundleDir) {
    Remove-Item -Recurse -Force $bundleDir
}
New-Item -ItemType Directory -Path $bundleDir | Out-Null
New-Item -ItemType Directory -Path (Join-Path $bundleDir "init") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $bundleDir "scripts") | Out-Null

$bundleComposePath = Join-Path $bundleDir "docker-compose.yml"
Copy-Item (Join-Path $projectRoot "docker-compose.db.yml") $bundleComposePath
(Get-Content $bundleComposePath) -replace '^  udaya_pacs_db:', "  ${bundleName}:" |
    Set-Content $bundleComposePath
$envDbExamplePath = Join-Path $bundleDir ".env.db.example"
Copy-Item (Join-Path $projectRoot ".env.db.example") $envDbExamplePath
Set-EnvValue -FilePath $envDbExamplePath -Key "PACS_DB_CONTAINER_NAME" -Value $bundleName
Set-EnvValue -FilePath $envDbExamplePath -Key "PACS_DB_NETWORK_NAME" -Value "$bundleName-network"
Set-EnvValue -FilePath $envDbExamplePath -Key "PACS_DB_COMPOSE_PROJECT_NAME" -Value $bundleName

$envDbPath = Join-Path $bundleDir ".env.db"
Copy-Item $envDbExamplePath $envDbPath

foreach ($path in @($envDbExamplePath, $envDbPath)) {
    Set-EnvValue -FilePath $path -Key "PACS_DB_CONTAINER_NAME" -Value $bundleName
    Set-EnvValue -FilePath $path -Key "PACS_DB_NETWORK_NAME" -Value "$bundleName-network"
    Set-EnvValue -FilePath $path -Key "PACS_DB_COMPOSE_PROJECT_NAME" -Value $bundleName
}
Apply-TargetDatabaseEnv -TargetName $Target -FilePath $envDbPath

if (-not $NoData) {
    $running = docker inspect -f "{{.State.Running}}" $SourceContainer 2>$null
    if ($LASTEXITCODE -ne 0 -or $running -ne "true") {
        throw "Source DB container '$SourceContainer' is not running. Start it first, or use -NoData for an empty DB package."
    }

    Write-Host "Exporting DB from container '$SourceContainer', database '$SourceDatabase'..."
    docker exec $SourceContainer pg_dump `
        -U $SourceUser `
        -d $SourceDatabase `
        --format=custom `
        --blobs `
        --no-owner `
        --no-acl `
        -f $containerDumpPath
    docker cp "$SourceContainer`:$containerDumpPath" (Join-Path $bundleDir "init\$dumpName")
    docker exec $SourceContainer rm -f $containerDumpPath | Out-Null

    $backupInfo = @"
PACS DB backup
CreatedAt=$((Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz"))
SourceContainer=$SourceContainer
SourceDatabase=$SourceDatabase
SourceUser=$SourceUser
Format=pg_dump custom format
Includes=schema,data,indexes,constraints,sequences,functions,views
"@
    Set-Content -Path (Join-Path $bundleDir "backup-info.txt") -Value $backupInfo -NoNewline
}

$initScript = @'
#!/usr/bin/env bash
set -e

DUMP_FILE="/docker-entrypoint-initdb.d/pacs-db.dump"

if [ -f "$DUMP_FILE" ]; then
  echo "Restoring PACS database dump into ${POSTGRES_DB}..."
  pg_restore \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --no-owner \
    --no-acl \
    "$DUMP_FILE"
  echo "PACS database restore completed."
else
  echo "No PACS database dump found. Starting with an empty database."
fi
'@
Set-Content -Path (Join-Path $bundleDir "init\01-restore-pacs-db.sh") -Value $initScript -NoNewline

$importPs = @'
Param(
    [string]$ContainerName = "__DB_CONTAINER_NAME__",
    [string]$DbName = "emr_pacs_db",
    [string]$DbUser = "pacs_app_local_rw",
    [string]$DumpPath = ".\init\pacs-db.dump"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $DumpPath)) {
    throw "Dump file not found: $DumpPath"
}

docker cp $DumpPath "$ContainerName`:/tmp/pacs-db.dump"
docker exec $ContainerName pg_restore `
    --username $DbUser `
    --dbname $DbName `
    --clean `
    --if-exists `
    --no-owner `
    --no-acl `
    /tmp/pacs-db.dump
docker exec $ContainerName rm -f /tmp/pacs-db.dump | Out-Null
Write-Host "DB import completed."
'@
Set-Content -Path (Join-Path $bundleDir "scripts\import-db.ps1") -Value $importPs -NoNewline
(Get-Content (Join-Path $bundleDir "scripts\import-db.ps1")) -replace "__DB_CONTAINER_NAME__", $bundleName |
    Set-Content (Join-Path $bundleDir "scripts\import-db.ps1")

$importSh = @'
#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-__DB_CONTAINER_NAME__}"
DB_NAME="${DB_NAME:-emr_pacs_db}"
DB_USER="${DB_USER:-pacs_app_local_rw}"
DUMP_PATH="${DUMP_PATH:-./init/pacs-db.dump}"

if [[ ! -f "$DUMP_PATH" ]]; then
  echo "Dump file not found: $DUMP_PATH"
  exit 1
fi

docker cp "$DUMP_PATH" "$CONTAINER_NAME:/tmp/pacs-db.dump"
docker exec "$CONTAINER_NAME" pg_restore \
  --username "$DB_USER" \
  --dbname "$DB_NAME" \
  --clean \
  --if-exists \
  --no-owner \
  --no-acl \
  /tmp/pacs-db.dump
docker exec "$CONTAINER_NAME" rm -f /tmp/pacs-db.dump >/dev/null
echo "DB import completed."
'@
Set-Content -Path (Join-Path $bundleDir "scripts\import-db.sh") -Value $importSh -NoNewline
(Get-Content (Join-Path $bundleDir "scripts\import-db.sh")) -replace "__DB_CONTAINER_NAME__", $bundleName |
    Set-Content (Join-Path $bundleDir "scripts\import-db.sh")

$resetPs = @'
Param(
    [string]$EnvFile = ".\.env.db",
    [string]$ComposeFile = ".\docker-compose.yml"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $EnvFile)) {
    throw "Env file not found: $EnvFile"
}

Write-Host "Resetting DB container and volume. Existing DB data will be removed."
docker compose -f $ComposeFile --env-file $EnvFile down -v --remove-orphans
docker compose -f $ComposeFile --env-file $EnvFile up -d
Write-Host "DB reset completed."
'@
Set-Content -Path (Join-Path $bundleDir "scripts\reset-db.ps1") -Value $resetPs -NoNewline

$resetSh = @'
#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.db}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

echo "Resetting DB container and volume. Existing DB data will be removed."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down -v --remove-orphans
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d
echo "DB reset completed."
'@
Set-Content -Path (Join-Path $bundleDir "scripts\reset-db.sh") -Value $resetSh -NoNewline

$syncCredentialsPs = @'
Param(
    [string]$EnvFile = ".\.env.db"
)

$ErrorActionPreference = "Stop"

function Get-EnvValue {
    param([string]$Path, [string]$Key)
    if (-not (Test-Path $Path)) { return "" }
    foreach ($line in Get-Content $Path) {
        if ($line -match '^\s*#' -or [string]::IsNullOrWhiteSpace($line)) { continue }
        $idx = $line.IndexOf("=")
        if ($idx -le 0) { continue }
        if ($line.Substring(0, $idx).Trim() -eq $Key) {
            return $line.Substring($idx + 1).Trim()
        }
    }
    return ""
}

function ConvertTo-SqlLiteral {
    param([string]$Value)
    return "'" + ($Value -replace "'", "''") + "'"
}

if (-not (Test-Path $EnvFile)) {
    throw "Env file not found: $EnvFile"
}

$containerName = Get-EnvValue -Path $EnvFile -Key "PACS_DB_CONTAINER_NAME"
$dbName = Get-EnvValue -Path $EnvFile -Key "PACS_DB_NAME"
$dbUser = Get-EnvValue -Path $EnvFile -Key "PACS_DB_USER"
$dbPassword = Get-EnvValue -Path $EnvFile -Key "PACS_DB_PASSWORD"
if ([string]::IsNullOrWhiteSpace($containerName)) { $containerName = "__DB_CONTAINER_NAME__" }
if ([string]::IsNullOrWhiteSpace($dbName) -or [string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbPassword)) {
    throw "PACS_DB_NAME, PACS_DB_USER, and PACS_DB_PASSWORD are required in $EnvFile"
}

$adminUser = ""
foreach ($candidate in @($dbUser, "postgres")) {
    docker exec $containerName psql -U $candidate -d postgres -v ON_ERROR_STOP=1 -c "select 1" *> $null
    if ($LASTEXITCODE -eq 0) {
        $adminUser = $candidate
        break
    }
}
if ([string]::IsNullOrWhiteSpace($adminUser)) {
    throw "Cannot connect to PostgreSQL inside $containerName. Make sure the DB container is running."
}

$dbLiteral = ConvertTo-SqlLiteral $dbName
$userLiteral = ConvertTo-SqlLiteral $dbUser
$passwordLiteral = ConvertTo-SqlLiteral $dbPassword
$sql = @"
DO `$`$
DECLARE
  role_name text := $userLiteral;
  role_password text := $passwordLiteral;
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
    EXECUTE format('ALTER ROLE %I WITH LOGIN SUPERUSER PASSWORD %L', role_name, role_password);
  ELSE
    EXECUTE format('CREATE ROLE %I WITH LOGIN SUPERUSER PASSWORD %L', role_name, role_password);
  END IF;
END
`$`$;
SELECT format('CREATE DATABASE %I OWNER %I', $dbLiteral, $userLiteral)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = $dbLiteral)
\gexec
"@

$sql | docker exec -i $containerName psql -U $adminUser -d postgres -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) {
    throw "Failed to sync DB credentials."
}

docker exec -e "PGPASSWORD=$dbPassword" $containerName psql -h 127.0.0.1 -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -c "select 1" *> $null
if ($LASTEXITCODE -ne 0) {
    throw "DB credential sync finished, but password login still failed."
}

Write-Host "DB credentials OK for $dbUser on $dbName."
'@
Set-Content -Path (Join-Path $bundleDir "scripts\sync-db-credentials.ps1") -Value $syncCredentialsPs -NoNewline
(Get-Content (Join-Path $bundleDir "scripts\sync-db-credentials.ps1")) -replace "__DB_CONTAINER_NAME__", $bundleName |
    Set-Content (Join-Path $bundleDir "scripts\sync-db-credentials.ps1")

$syncCredentialsSh = @'
#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.db}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

env_value() {
  local key="$1"
  awk -F= -v wanted="$key" '$1 == wanted { sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit }' "$ENV_FILE"
}

sql_literal() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"
}

CONTAINER_NAME="$(env_value PACS_DB_CONTAINER_NAME)"
DB_NAME="$(env_value PACS_DB_NAME)"
DB_USER="$(env_value PACS_DB_USER)"
DB_PASSWORD="$(env_value PACS_DB_PASSWORD)"
CONTAINER_NAME="${CONTAINER_NAME:-__DB_CONTAINER_NAME__}"

if [[ -z "$DB_NAME" || -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "PACS_DB_NAME, PACS_DB_USER, and PACS_DB_PASSWORD are required in $ENV_FILE" >&2
  exit 1
fi

if ! docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  echo "DB container not found: $CONTAINER_NAME" >&2
  exit 1
fi

ADMIN_USER=""
for candidate in "$DB_USER" postgres; do
  if docker exec "$CONTAINER_NAME" psql -U "$candidate" -d postgres -v ON_ERROR_STOP=1 -c "select 1" >/dev/null 2>&1; then
    ADMIN_USER="$candidate"
    break
  fi
done

if [[ -z "$ADMIN_USER" ]]; then
  echo "Cannot connect to PostgreSQL inside $CONTAINER_NAME. Make sure the DB container is running." >&2
  exit 1
fi

DB_LITERAL="$(sql_literal "$DB_NAME")"
USER_LITERAL="$(sql_literal "$DB_USER")"
PASSWORD_LITERAL="$(sql_literal "$DB_PASSWORD")"

docker exec -i "$CONTAINER_NAME" psql -U "$ADMIN_USER" -d postgres -v ON_ERROR_STOP=1 <<SQL
DO \$\$
DECLARE
  role_name text := ${USER_LITERAL};
  role_password text := ${PASSWORD_LITERAL};
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
    EXECUTE format('ALTER ROLE %I WITH LOGIN SUPERUSER PASSWORD %L', role_name, role_password);
  ELSE
    EXECUTE format('CREATE ROLE %I WITH LOGIN SUPERUSER PASSWORD %L', role_name, role_password);
  END IF;
END
\$\$;
SELECT format('CREATE DATABASE %I OWNER %I', ${DB_LITERAL}, ${USER_LITERAL})
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = ${DB_LITERAL})
\gexec
SQL

if ! docker exec -e "PGPASSWORD=$DB_PASSWORD" "$CONTAINER_NAME" psql -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -c "select 1" >/dev/null 2>&1; then
  echo "DB credential sync finished, but password login still failed." >&2
  exit 1
fi

echo "DB credentials OK for $DB_USER on $DB_NAME."
'@
Set-Content -Path (Join-Path $bundleDir "scripts\sync-db-credentials.sh") -Value $syncCredentialsSh -NoNewline
(Get-Content (Join-Path $bundleDir "scripts\sync-db-credentials.sh")) -replace "__DB_CONTAINER_NAME__", $bundleName |
    Set-Content (Join-Path $bundleDir "scripts\sync-db-credentials.sh")

$deployDbPs = @'
Param(
    [string]$EnvFile = ".\.env.db",
    [string]$ComposeFile = ".\docker-compose.yml"
)

$ErrorActionPreference = "Stop"

function Get-EnvValue {
    param([string]$Path, [string]$Key)
    if (-not (Test-Path $Path)) { return "" }
    foreach ($line in Get-Content $Path) {
        if ($line -match '^\s*#' -or [string]::IsNullOrWhiteSpace($line)) { continue }
        $idx = $line.IndexOf("=")
        if ($idx -le 0) { continue }
        if ($line.Substring(0, $idx).Trim() -eq $Key) {
            return $line.Substring($idx + 1).Trim()
        }
    }
    return ""
}

if (-not (Test-Path $EnvFile)) {
    throw "Env file not found: $EnvFile"
}
if (-not (Test-Path $ComposeFile)) {
    throw "Compose file not found: $ComposeFile"
}
if (Test-Path ".\postgres-18.tar") {
    docker load -i .\postgres-18.tar
}

docker compose -f $ComposeFile --env-file $EnvFile up -d

$containerName = Get-EnvValue -Path $EnvFile -Key "PACS_DB_CONTAINER_NAME"
if ([string]::IsNullOrWhiteSpace($containerName)) { $containerName = "__DB_CONTAINER_NAME__" }

for ($attempt = 1; $attempt -le 60; $attempt++) {
    docker exec $containerName pg_isready *> $null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 1
    if ($attempt -eq 60) {
        docker logs --tail=120 $containerName
        throw "DB did not become ready: $containerName"
    }
}

powershell -ExecutionPolicy Bypass -File .\scripts\sync-db-credentials.ps1 -EnvFile $EnvFile
docker ps --filter "name=$containerName"
Write-Host "DB deploy completed: $containerName"
'@
Set-Content -Path (Join-Path $bundleDir "scripts\deploy-db.ps1") -Value $deployDbPs -NoNewline
(Get-Content (Join-Path $bundleDir "scripts\deploy-db.ps1")) -replace "__DB_CONTAINER_NAME__", $bundleName |
    Set-Content (Join-Path $bundleDir "scripts\deploy-db.ps1")

$deployDbSh = @'
#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.db}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi
if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

sed -i 's/\r$//' "$ENV_FILE" "$COMPOSE_FILE" ./scripts/*.sh 2>/dev/null || true

if [[ -f "./postgres-18.tar" ]]; then
  docker load -i ./postgres-18.tar
fi

docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

env_value() {
  local key="$1"
  awk -F= -v wanted="$key" '$1 == wanted { sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit }' "$ENV_FILE"
}

CONTAINER_NAME="$(env_value PACS_DB_CONTAINER_NAME)"
CONTAINER_NAME="${CONTAINER_NAME:-__DB_CONTAINER_NAME__}"

for attempt in $(seq 1 60); do
  if docker exec "$CONTAINER_NAME" pg_isready >/dev/null 2>&1; then
    break
  fi
  sleep 1
  if [[ "$attempt" == "60" ]]; then
    docker logs --tail=120 "$CONTAINER_NAME" || true
    echo "DB did not become ready: $CONTAINER_NAME" >&2
    exit 1
  fi
done

bash ./scripts/sync-db-credentials.sh
docker ps --filter "name=$CONTAINER_NAME"
echo "DB deploy completed: $CONTAINER_NAME"
'@
Set-Content -Path (Join-Path $bundleDir "scripts\deploy-db.sh") -Value $deployDbSh -NoNewline
(Get-Content (Join-Path $bundleDir "scripts\deploy-db.sh")) -replace "__DB_CONTAINER_NAME__", $bundleName |
    Set-Content (Join-Path $bundleDir "scripts\deploy-db.sh")

if ($includeImage) {
    Write-Host "Pulling $postgresImage..."
    docker pull $postgresImage
    Write-Host "Saving $postgresImage..."
    docker save -o (Join-Path $bundleDir $postgresTar) $postgresImage
}

if ($includeImage) {
    $loadImageStep = @"
## 1. Load image
~~~powershell
docker load -i .\$postgresTar
~~~

"@
} else {
    $loadImageStep = @"
## 1. Pull image
~~~powershell
docker pull $postgresImage
~~~

"@
}

$dataStep = if ($NoData) {
    "This package does not include a local database dump."
} else {
    "This package includes init\$dumpName exported from the local DB. It restores automatically on the first container start with a fresh volume."
}

$deployReadme = @"
# UDAYA_PACS_DB Deploy

Target: $Target
Package: $bundleName

This package is DB only. It creates PostgreSQL database emr_pacs_db.
$dataStep

$loadImageStep## 2. Review env
~~~powershell
notepad .env.db
~~~

For local-only DB testing, keep PACS_DB_BIND_ADDRESS=127.0.0.1.
For a separate API server, set PACS_DB_BIND_ADDRESS to the DB server private IP and protect port 5432 with firewall rules.

## 3. Start DB, auto-import, and sync credentials
~~~powershell
powershell -ExecutionPolicy Bypass -File .\scripts\deploy-db.ps1
~~~

Linux:

~~~bash
bash ./scripts/deploy-db.sh
~~~

Auto-import runs only when the Postgres volume is new/empty. Credential sync runs every deploy so an existing volume with an old password is repaired without deleting data.

## 4. Sync DB credentials without deleting data
PostgreSQL applies POSTGRES_USER and POSTGRES_PASSWORD only when the volume is empty. Run this manually only if you edited .env.db after deploy:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-db-credentials.ps1
~~~

Linux:

~~~bash
bash ./scripts/sync-db-credentials.sh
~~~

## 5. Reset DB credentials / fresh DB
Use this only when you want a clean database because it removes the DB volume:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\scripts\reset-db.ps1
~~~

Linux:

~~~bash
bash ./scripts/reset-db.sh
~~~

## 6. Existing volume import
If the DB container already has data and you need to overwrite it from the bundled dump:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import-db.ps1
~~~

## 7. Check health
~~~powershell
docker inspect $bundleName --format '{{.State.Health.Status}}'
~~~
"@
Set-Content -Path (Join-Path $bundleDir "README-DB-DEPLOY.md") -Value $deployReadme -NoNewline

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
foreach ($linuxScript in @(
    (Join-Path $bundleDir "init\01-restore-pacs-db.sh"),
    (Join-Path $bundleDir "scripts\deploy-db.sh"),
    (Join-Path $bundleDir "scripts\import-db.sh"),
    (Join-Path $bundleDir "scripts\reset-db.sh"),
    (Join-Path $bundleDir "scripts\sync-db-credentials.sh")
)) {
    if (Test-Path $linuxScript) {
        $content = [System.IO.File]::ReadAllText($linuxScript)
        $content = $content -replace "`r`n", "`n"
        $content = $content -replace "`r", "`n"
        [System.IO.File]::WriteAllText($linuxScript, $content, $utf8NoBom)
    }
}

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

Write-Host "DB deploy zip ready:"
Write-Host $zipPath
