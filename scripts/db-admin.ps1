Param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("backup", "migrate", "validate", "refresh-cache", "partition-maintenance", "explain")]
    [string]$Action,

    [ValidateSet("local", "qa", "prod")]
    [string]$Target = "local",

    [string]$DbContainer = $(if ($env:PACS_DB_CONTAINER) { $env:PACS_DB_CONTAINER } else { "" }),
    [string]$BackupDirectory = "",
    [switch]$Build,
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

function Get-EnvFileValue {
    param([string]$FilePath, [string]$Key)
    if (-not (Test-Path $FilePath)) { return "" }
    foreach ($line in Get-Content $FilePath) {
        if ($line -match '^\s*#' -or [string]::IsNullOrWhiteSpace($line)) { continue }
        $separator = $line.IndexOf("=")
        if ($separator -le 0) { continue }
        if ($line.Substring(0, $separator).Trim() -eq $Key) {
            return $line.Substring($separator + 1).Trim()
        }
    }
    return ""
}

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: docker $($Arguments -join ' ')"
    }
}

if ([string]::IsNullOrWhiteSpace($DbContainer)) {
    if ($Target -eq "local") {
        $localRunning = (& docker inspect -f "{{.State.Running}}" "pacs-db" 2>$null | Out-String).Trim()
        if ($LASTEXITCODE -eq 0 -and $localRunning -eq "true") {
            $DbContainer = "pacs-db"
        }
    }
}
if ([string]::IsNullOrWhiteSpace($DbContainer)) {
    $DbContainer = Get-EnvFileValue -FilePath ".env.db" -Key "PACS_DB_CONTAINER_NAME"
}
if ([string]::IsNullOrWhiteSpace($DbContainer)) {
    $DbContainer = if ($Target -eq "local") { "pacs-db" } else { "udaya_pacs_db" }
}

$running = (& docker inspect -f "{{.State.Running}}" $DbContainer 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $running -ne "true") {
    throw "PostgreSQL container '$DbContainer' is not running."
}

$dbUser = (& docker exec $DbContainer sh -lc 'printf %s "$POSTGRES_USER"' | Out-String).Trim()
$dbName = (& docker exec $DbContainer sh -lc 'printf %s "$POSTGRES_DB"' | Out-String).Trim()
if ($dbUser -notmatch '^[A-Za-z_][A-Za-z0-9_]*$' -or $dbName -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw "PostgreSQL container returned an unsafe database user or name."
}

if ([string]::IsNullOrWhiteSpace($BackupDirectory)) {
    $BackupDirectory = Join-Path $projectRoot "backups"
}

function Invoke-Backup {
    New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $fileName = "emr_pacs_backup_${Target}_${stamp}.dump"
    $hostPath = Join-Path $BackupDirectory $fileName
    $containerPath = "/tmp/$fileName"

    Invoke-Docker -Arguments @(
        "exec", $DbContainer,
        "pg_dump", "-U", $dbUser, "-d", $dbName,
        "--format=custom", "--blobs", "--no-owner", "--no-acl",
        "--file", $containerPath
    )
    try {
        Invoke-Docker -Arguments @("cp", "${DbContainer}:${containerPath}", $hostPath)
    } finally {
        & docker exec $DbContainer rm -f $containerPath 2>$null | Out-Null
    }
    Write-Host "Database backup: $hostPath" -ForegroundColor Green
    return $hostPath
}

function Invoke-SqlFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    $resolved = Resolve-Path $Path
    Get-Content $resolved -Raw |
        docker exec -i $DbContainer psql -U $dbUser -d $dbName -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) {
        throw "Database SQL failed: $resolved"
    }
}

switch ($Action) {
    "backup" {
        Invoke-Backup | Out-Null
    }
    "validate" {
        Invoke-SqlFile -Path "db/validation/validate_pacs_db.sql"
    }
    "refresh-cache" {
        Invoke-SqlFile -Path "db/scripts/refresh_week_cache.sql"
        Invoke-SqlFile -Path "db/validation/validate_pacs_db.sql"
    }
    "partition-maintenance" {
        Invoke-SqlFile -Path "db/scripts/run_partition_maintenance.sql"
        Invoke-SqlFile -Path "db/validation/validate_pacs_db.sql"
    }
    "explain" {
        Invoke-SqlFile -Path "db/validation/explain_pacs_performance.sql"
    }
    "migrate" {
        Invoke-Backup | Out-Null
        $stackArgs = @(
            "-ExecutionPolicy", "Bypass",
            "-File", (Join-Path $PSScriptRoot "stack.ps1"),
            "-Target", $Target,
            "-Action", "deploy"
        )
        if ($Build) { $stackArgs += "-Build" }
        if ($NoBuild) { $stackArgs += "-NoBuild" }
        & powershell @stackArgs
        if ($LASTEXITCODE -ne 0) {
            throw "API deployment/Flyway migration failed."
        }
        Invoke-SqlFile -Path "db/validation/validate_pacs_db.sql"
    }
}
