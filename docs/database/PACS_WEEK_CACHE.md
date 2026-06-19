# PACS Week Cache

V199 adds two rebuildable cache tables for the default Worklist and Study list
screens:

| Cache table | Source table | Window | ID rule |
|---|---|---:|---|
| `pacs_worklists_week_cache` | `pacs_worklists` | last 7 days | same `id` |
| `pacs_studies_week_cache` | `pacs_studies` | last 7 days | same `id` |

The source-of-truth tables are still `pacs_worklists` and `pacs_studies`.
Detail APIs, exact accession/visit searches, patient-history searches, exports,
retention, and audit flows read the main tables.

The Worklist cache deliberately excludes `error_message`. Workflow diagnostics
remain available from Worklist detail reads against `pacs_worklists`; default
list responses stay summary-only.

## Maintenance

Manual refresh:

```sql
SELECT refresh_pacs_week_cache();
```

Manual cleanup:

```sql
SELECT cleanup_pacs_week_cache();
```

The migration installs triggers on `pacs_worklists` and `pacs_studies` so recent
insert/update/delete activity stays synchronized immediately. Trigger writes use
the `NEW` row directly; historical bulk inserts older than seven days are a
fast no-op instead of re-reading the source table. The Spring Boot
maintenance scheduler also runs:

```properties
pacs.maintenance.week-cache.enabled=true
pacs.maintenance.week-cache.weekly-cron=0 0 2 * * MON
pacs.maintenance.week-cache.cleanup-cron=0 30 2 * * *
```

Both jobs use the advisory lock `pacs_week_cache_refresh`.
The refresh function is transactional. If either cache refill fails, PostgreSQL
rolls back the truncation and keeps the previous cache contents.

## Validation

```bash
psql -d emr_pacs_db -f tools/sql/week-cache/validate_pacs_week_cache.sql
psql -d emr_pacs_db -f tools/sql/week-cache/explain_pacs_week_cache.sql
psql -d emr_pacs_db -f tools/sql/week-cache/test_pacs_week_cache.sql
```

The validation script checks orphan cache rows, rows older than 7 days, cache vs
main status/accession mismatches, and recent main rows missing from cache.
