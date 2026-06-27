# EMR/PACS Docker Database Operations

The Docker PostgreSQL container is the database source of truth. Flyway files
under `src/main/resources/db/migration` are the only canonical migrations.

## Backup

```bash
bash scripts/stack.sh local db-backup
```

```bash
bash ./scripts/stack.sh local db-backup
```

Backups are custom-format `pg_dump` files under `backups/`. No database
password is printed or copied into the command line.

## Migrate

```bash
bash scripts/stack.sh local db-migrate --build
```

```bash
bash ./scripts/stack.sh local db-migrate --build
```

This performs a backup, deploys the API so Flyway applies pending migrations,
waits for health, and runs database validation. Do not execute individual
Flyway SQL files manually with `psql`.

## Validate

```bash
bash scripts/stack.sh local db-validate
```

Validation checks extensions, native parents/children through `pg_inherits`,
fake partition names, the six maintenance configs, required functions, CHECK
constraints, cache age/orphans, invalid indexes, and unvalidated constraints.

## Cache Maintenance

```bash
bash scripts/stack.sh local db-refresh-cache
```

`pacs_worklists` and `pacs_studies` remain the source of truth. Their weekly
caches contain only recent summary rows with the same IDs and can be rebuilt.

## Partition Maintenance

```bash
bash scripts/stack.sh local db-partition-maintenance
```

The four technical log/event parents use fixed 12-month monthly retention. The
two medical/audit parents use yearly policy retention and are never removed by
the fixed-month rule.

To change a technical-log retention window:

```sql
UPDATE partition_maintenance_configs
SET retention_months = 18,
    updated_at = NOW()
WHERE parent_schema = 'public'
  AND parent_table = 'system_activities'
  AND retention_mode = 'FIXED_MONTHS';
```

## Performance Plans

```bash
bash scripts/db-admin.sh explain local
```

The SQL is in `db/validation/explain_pacs_performance.sql`.

## Scheduler Logs

```bash
docker logs --since 24h udaya_pacs_local_api 2>&1 |
  grep -E "partition maintenance|PACS cache"
```

Scheduler flags:

```env
PACS_CACHE_SCHEDULER_ENABLED=true
PACS_PARTITION_SCHEDULER_ENABLED=true
```

The legacy `PACS_MAINTENANCE_*_ENABLED` names remain supported.

## Restore

Stop the API before a full restore. For a custom-format backup:

```bash
docker cp backups/emr_pacs_backup_local_YYYYMMDD_HHMMSS.dump pacs-db:/tmp/restore.dump
docker exec pacs-db sh -lc \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-acl /tmp/restore.dump'
```

Run `db-validate` after restore and restart the API. In QA/production, perform
restore only during an approved maintenance window.
