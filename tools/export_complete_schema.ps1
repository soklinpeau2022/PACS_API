param(
    [string]$DbContainer = $(if ($env:PACS_DB_CONTAINER) { $env:PACS_DB_CONTAINER } else { "pacs-db" }),
    [string]$OutputPath = (Join-Path $PSScriptRoot ".cache\database-schema\emr_pacs_db_complete.sql"),
    [switch]$SkipRestoreValidation
)

$ErrorActionPreference = "Stop"

$RequiredExtensions = @("pgcrypto", "pg_trgm")
$RequiredFunctions = @(
    "refresh_pacs_week_cache",
    "sync_pacs_worklist_week_cache",
    "sync_pacs_study_week_cache",
    "cleanup_pacs_week_cache",
    "create_future_partitions",
    "drop_expired_fixed_partitions",
    "cleanup_policy_based_retention_data",
    "drop_policy_partitions_if_fully_expired",
    "run_partition_maintenance"
)
$PartitionParents = @(
    "user_logs",
    "system_activities",
    "dicom_server_callback_log",
    "pacs_realtime_notification_events",
    "pacs_worklist_histories",
    "study_retention_delete_requests"
)

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: docker $($Arguments -join ' ')"
    }
    return $output
}

function Get-DatabaseMetrics {
    param(
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$DatabaseUser
    )

    $sql = @"
SELECT json_build_object(
    'tables', (
        SELECT count(*)
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_type = 'BASE TABLE'
    ),
    'indexes', (
        SELECT count(*)
        FROM pg_index idx
        JOIN pg_class index_class ON index_class.oid = idx.indexrelid
        JOIN pg_namespace index_namespace ON index_namespace.oid = index_class.relnamespace
        WHERE index_namespace.nspname = 'public'
    ),
    'uniqueIndexes', (
        SELECT count(*)
        FROM pg_index idx
        JOIN pg_class index_class ON index_class.oid = idx.indexrelid
        JOIN pg_namespace index_namespace ON index_namespace.oid = index_class.relnamespace
        WHERE index_namespace.nspname = 'public'
          AND idx.indisunique
    ),
    'foreignKeys', (
        SELECT count(*)
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND contype = 'f'
    ),
    'checkConstraints', (
        SELECT count(*)
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND contype = 'c'
    ),
    'functions', (
        SELECT count(*)
        FROM pg_proc procedure
        JOIN pg_namespace procedure_namespace ON procedure_namespace.oid = procedure.pronamespace
        WHERE procedure_namespace.nspname = 'public'
          AND procedure.prokind = 'f'
    ),
    'userTriggers', (
        SELECT count(*)
        FROM pg_trigger trigger_row
        JOIN pg_class trigger_table ON trigger_table.oid = trigger_row.tgrelid
        JOIN pg_namespace trigger_namespace ON trigger_namespace.oid = trigger_table.relnamespace
        WHERE trigger_namespace.nspname = 'public'
          AND NOT trigger_row.tgisinternal
    ),
    'partitionedParents', (
        SELECT count(*)
        FROM pg_class
        WHERE relnamespace = 'public'::regnamespace
          AND relkind = 'p'
    ),
    'tablePartitions', (
        SELECT count(*)
        FROM pg_inherits inheritance
        JOIN pg_class parent ON parent.oid = inheritance.inhparent
        JOIN pg_class child ON child.oid = inheritance.inhrelid
        WHERE parent.relnamespace = 'public'::regnamespace
          AND parent.relkind = 'p'
          AND child.relkind = 'r'
    )
)::text;
"@

    $json = Invoke-Docker -Arguments @(
        "exec", $DbContainer,
        "psql", "-U", $DatabaseUser, "-d", $Database,
        "-t", "-A", "-v", "ON_ERROR_STOP=1", "-c", $sql
    )
    return ($json -join "").Trim() | ConvertFrom-Json
}

function Assert-EqualMetrics {
    param(
        [Parameter(Mandatory = $true)]$Source,
        [Parameter(Mandatory = $true)]$Restored
    )

    $properties = @(
        "tables",
        "indexes",
        "uniqueIndexes",
        "foreignKeys",
        "checkConstraints",
        "functions",
        "userTriggers",
        "partitionedParents",
        "tablePartitions"
    )
    foreach ($property in $properties) {
        if ([long]$Source.$property -ne [long]$Restored.$property) {
            throw "Restored schema metric '$property' differs: source=$($Source.$property), restored=$($Restored.$property)"
        }
    }
}

$containerRunning = (Invoke-Docker -Arguments @(
    "inspect", "-f", "{{.State.Running}}", $DbContainer
)).Trim()
if ($containerRunning -ne "true") {
    throw "Database container '$DbContainer' is not running."
}

$databaseUser = (Invoke-Docker -Arguments @(
    "exec", $DbContainer, "sh", "-lc", 'printf %s "$POSTGRES_USER"'
)).Trim()
$databaseName = (Invoke-Docker -Arguments @(
    "exec", $DbContainer, "sh", "-lc", 'printf %s "$POSTGRES_DB"'
)).Trim()

if ($databaseUser -notmatch '^[A-Za-z_][A-Za-z0-9_]*$' -or $databaseName -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw "Database container returned an unsafe user or database identifier."
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$token = [Guid]::NewGuid().ToString("N")
$remoteDump = "/tmp/emr-pacs-schema-$token.sql"
$remoteRestore = "/tmp/emr-pacs-schema-restore-$token.sql"
$restoreDatabase = "emr_pacs_schema_restore_$($token.Substring(0, 12))"
$sourceMetrics = Get-DatabaseMetrics -Database $databaseName -DatabaseUser $databaseUser
$restoreMetrics = $null

try {
    Invoke-Docker -Arguments @(
        "exec", $DbContainer,
        "pg_dump",
        "-U", $databaseUser,
        "-d", $databaseName,
        "--schema-only",
        "--no-owner",
        "--no-acl",
        "--file", $remoteDump
    ) | Out-Null

    Invoke-Docker -Arguments @(
        "cp", "${DbContainer}:${remoteDump}", $resolvedOutput
    ) | Out-Null

    $schemaSql = [System.IO.File]::ReadAllText($resolvedOutput)
    $partitionByCount = [regex]::Matches(
        $schemaSql,
        '(?im)^\s*PARTITION\s+BY\s+RANGE\s*\('
    ).Count
    $tableAttachCount = [regex]::Matches(
        $schemaSql,
        '(?im)^\s*ALTER\s+TABLE\s+ONLY\s+.+?\s+ATTACH\s+PARTITION\s+.+?;'
    ).Count
    $checkCount = [regex]::Matches($schemaSql, '(?im)\bCHECK\s*\(').Count
    $functionCount = [regex]::Matches(
        $schemaSql,
        '(?im)^\s*CREATE\s+FUNCTION\s+'
    ).Count
    $triggerCount = [regex]::Matches(
        $schemaSql,
        '(?im)^\s*CREATE\s+TRIGGER\s+'
    ).Count

    if ($partitionByCount -lt $PartitionParents.Count) {
        throw "Schema export contains only $partitionByCount PARTITION BY clauses; expected at least $($PartitionParents.Count)."
    }
    if ($tableAttachCount -lt [long]$sourceMetrics.tablePartitions) {
        throw "Schema export contains only $tableAttachCount table ATTACH PARTITION clauses; source has $($sourceMetrics.tablePartitions)."
    }
    if ($checkCount -eq 0 -or $functionCount -eq 0 -or $triggerCount -eq 0) {
        throw "Schema export omitted CHECK constraints, functions, or triggers."
    }

    foreach ($extension in $RequiredExtensions) {
        if ($schemaSql -notmatch "(?im)^\s*CREATE\s+EXTENSION\s+IF\s+NOT\s+EXISTS\s+`"?$extension`"?\b") {
            throw "Schema export is missing CREATE EXTENSION IF NOT EXISTS $extension."
        }
    }
    foreach ($function in $RequiredFunctions) {
        if ($schemaSql -notmatch "(?im)^\s*CREATE\s+FUNCTION\s+`"?public`"?\.`"?$function`"?\s*\(") {
            throw "Schema export is missing function $function()."
        }
    }
    foreach ($parent in $PartitionParents) {
        if ($schemaSql -notmatch "(?is)CREATE\s+TABLE\s+`"?public`"?\.`"?$parent`"?\s*\(.*?\)\s*PARTITION\s+BY\s+RANGE") {
            throw "Schema export is missing native partition parent $parent."
        }
        if ($schemaSql -notmatch "(?im)^\s*ALTER\s+TABLE\s+ONLY\s+`"?public`"?\.`"?$parent`"?\s+ATTACH\s+PARTITION\s+") {
            throw "Schema export is missing native child attachments for $parent."
        }
    }

    if (-not $SkipRestoreValidation) {
        $createSql = "CREATE DATABASE $restoreDatabase OWNER `"$databaseUser`";"
        Invoke-Docker -Arguments @(
            "exec", $DbContainer,
            "psql", "-U", $databaseUser, "-d", "postgres",
            "-v", "ON_ERROR_STOP=1", "-c", $createSql
        ) | Out-Null

        Invoke-Docker -Arguments @(
            "cp", $resolvedOutput, "${DbContainer}:${remoteRestore}"
        ) | Out-Null
        Invoke-Docker -Arguments @(
            "exec", $DbContainer,
            "psql", "-U", $databaseUser, "-d", $restoreDatabase,
            "-v", "ON_ERROR_STOP=1", "-f", $remoteRestore
        ) | Out-Null

        $restoreMetrics = Get-DatabaseMetrics -Database $restoreDatabase -DatabaseUser $databaseUser
        Assert-EqualMetrics -Source $sourceMetrics -Restored $restoreMetrics
    }

    $audit = [ordered]@{
        generatedAt = (Get-Date).ToString("o")
        sourceContainer = $DbContainer
        sourceDatabase = $databaseName
        outputPath = $resolvedOutput
        bytes = (Get-Item $resolvedOutput).Length
        nativePartitionFormat = "PARTITION BY plus ALTER TABLE ATTACH PARTITION (canonical pg_dump output)"
        partitionByClauses = $partitionByCount
        tableAttachPartitionClauses = $tableAttachCount
        checkClauses = $checkCount
        createFunctionStatements = $functionCount
        createTriggerStatements = $triggerCount
        sourceMetrics = $sourceMetrics
        restoredMetrics = $restoreMetrics
        restoreValidated = -not $SkipRestoreValidation
        status = "PASS"
    }
    $auditPath = "$resolvedOutput.audit.json"
    [System.IO.File]::WriteAllText(
        $auditPath,
        ($audit | ConvertTo-Json -Depth 5),
        [System.Text.UTF8Encoding]::new($false)
    )

    Write-Host "Complete PostgreSQL schema export passed." -ForegroundColor Green
    Write-Host "Schema: $resolvedOutput"
    Write-Host "Audit:  $auditPath"
    $sourceMetrics | Format-List
} finally {
    & docker exec $DbContainer rm -f $remoteDump $remoteRestore 2>$null | Out-Null
    if (-not $SkipRestoreValidation) {
        $terminateSql = @"
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$restoreDatabase';
"@
        & docker exec $DbContainer psql -U $databaseUser -d postgres -v ON_ERROR_STOP=1 -c $terminateSql 2>$null | Out-Null
        & docker exec $DbContainer psql -U $databaseUser -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $restoreDatabase;" 2>$null | Out-Null
    }
}
