# Complete PostgreSQL Schema Export

Do not use a generic table-DDL exporter for the PACS schema. Those exporters
often flatten PostgreSQL partitions and omit extensions, functions, triggers,
and CHECK constraints.

Use the PostgreSQL-native export:

```powershell
.\tools\export_complete_schema.ps1
```

To choose the output file:

```powershell
.\tools\export_complete_schema.ps1 `
  -OutputPath "C:\Users\MSI\Desktop\emr_pacs_db(9).sql"
```

The script:

1. Runs PostgreSQL 18 `pg_dump --schema-only`.
2. Requires `pgcrypto`, `pg_trgm`, all cache/partition functions, CHECK
   constraints, triggers, and all six partitioned parents.
3. Verifies every table partition is represented by native attachment DDL.
4. Restores the SQL into a temporary database.
5. Compares source and restored table, index, constraint, function, trigger,
   and partition counts.
6. Deletes the temporary database and writes a companion `.audit.json` file.

`pg_dump` represents native children with:

```sql
CREATE TABLE public.user_logs (...) PARTITION BY RANGE (created);
CREATE TABLE public.user_logs_202606 (...);
ALTER TABLE ONLY public.user_logs
    ATTACH PARTITION public.user_logs_202606
    FOR VALUES FROM (...) TO (...);
```

`ATTACH PARTITION` is native PostgreSQL partition DDL and restores the same
catalog relationship as `CREATE TABLE ... PARTITION OF`. Validate the restored
database through `pg_inherits`; do not classify a child as fake merely because
the dump uses the canonical attach form.
