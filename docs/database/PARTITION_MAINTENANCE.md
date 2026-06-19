# Partition Maintenance

V195 introduced config-driven maintenance. V197 separates fixed-retention
technical logs from policy-based medical/audit tables.

## Maintained Tables

The config table is `partition_maintenance_configs`. It seeds these parents:

V205 removes the obsolete `future_months` column. `future_partitions` is the
single setting for both monthly and yearly partition lookahead.

| Parent table | Partition column | Granularity | Retention mode | Auto drop |
|---|---:|---:|---:|---:|
| `user_logs` | `created` | `MONTH` | `FIXED_MONTHS`, 12 months | yes |
| `system_activities` | `created` | `MONTH` | `FIXED_MONTHS`, 12 months | yes |
| `dicom_server_callback_log` | `received_at` | `MONTH` | `FIXED_MONTHS`, 12 months | yes |
| `pacs_realtime_notification_events` | `created_at` | `MONTH` | `FIXED_MONTHS`, 12 months | yes |
| `pacs_worklist_histories` | `created` | `YEAR` | `POLICY_BASED` | no |
| `study_retention_delete_requests` | `created_at` | `YEAR` | `POLICY_BASED` | no |

Fixed-retention tables keep current month plus the previous 12 months and auto-create the next 3 months. `drop_expired_fixed_partitions()` only drops native child partitions named `parent_YYYYMM`; it never drops parent/default partitions.

Policy-based tables use yearly partitions and are never dropped by fixed age. Cleanup requires `purge_after IS NOT NULL AND purge_after < current_date`. `run_partition_maintenance()` only performs a policy dry-run by default.

`drop_policy_partitions_if_fully_expired()` is also a dry-run by default. It is
restricted to the two configured policy tables, skips current/future years, and
only considers a past native `parent_YYYY` partition when every row has passed
`purge_after`. If a matching `_archive` table exists, common columns are
archived before an approved delete or partition drop.

`pacs_worklists` and `pacs_studies` are intentionally not converted by the live
Flyway chain. PostgreSQL date partitioning cannot keep the current global
`UNIQUE (public_id)`, `UNIQUE (id, hospital_id)`, and
`UNIQUE (hospital_id, study_instance_uid)` contracts unless those unique keys
also include the partition key, or unless a larger DEV reset introduces separate
key tables and rewires dependent FKs/upserts. V205 removes their obsolete
maintenance config rows, so only the six native partitioned parents are
configured.

V196 also makes `dicom_server_callback_log.hospital_id` mandatory. Unknown
callbacks are written to `dicom_server_unmatched_callback_log` until the API can
resolve a hospital-scoped DICOM server.

## Manual Run

```sql
SELECT run_partition_maintenance();
```

This runs:

- `create_future_partitions()`
- `drop_expired_fixed_partitions()`
- `cleanup_policy_based_retention_data(true)`
- `ANALYZE` on active partitioned parents

Compatibility wrappers still exist:

```sql
SELECT create_future_monthly_partitions();
SELECT drop_old_monthly_partitions();
SELECT run_monthly_partition_maintenance();
```

## Change Retention

```sql
UPDATE partition_maintenance_configs
SET retention_months = 18,
    updated_at = now()
WHERE parent_schema = 'public'
  AND parent_table = 'system_activities'
  AND retention_mode = 'FIXED_MONTHS';
```

Do not set policy/audit tables to fixed auto-drop. Use row-level retention metadata instead:

```sql
SELECT * FROM cleanup_policy_based_retention_data(true);
SELECT * FROM cleanup_policy_based_retention_data(false);
SELECT * FROM drop_policy_partitions_if_fully_expired(true);
```

## Scheduling

Spring Boot runs monthly at 02:00 on the first day of the month:

```properties
pacs.maintenance.partitions.enabled=true
pacs.maintenance.partitions.monthly-cron=0 0 2 1 * *
```

The scheduler uses a PostgreSQL advisory lock named `emr_pacs_partition_maintenance`, so only one API instance runs maintenance at a time.

If `pg_cron` is installed and you prefer DB scheduling:

```sql
SELECT schedule_monthly_partition_maintenance_pg_cron();
SELECT * FROM cron.job;
SELECT unschedule_monthly_partition_maintenance_pg_cron();
```

The migration does not install `pg_cron`; many PostgreSQL hosts disallow it.

## Validation

```bash
psql -d emr_pacs_db -f tools/sql/partition-maintenance/validate_partition_maintenance.sql
```

This lists config rows, parent/child partitions, missing current/future partitions, fixed-retention partitions that would be dropped, policy purge eligibility, and default partition row-count SQL.

## Disposable Test

```bash
psql -d emr_pacs_db -f tools/sql/partition-maintenance/test_partition_maintenance.sql
```

The test runs inside a transaction and rolls back. It creates a test partitioned parent, creates future partitions, simulates an old monthly child, verifies the old child is dropped, and verifies parent/default tables are kept.
