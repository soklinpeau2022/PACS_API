# Complete PostgreSQL Schema Export

Do not use a generic table-DDL exporter for the PACS schema. Those exporters
often flatten PostgreSQL partitions and omit extensions, functions, triggers,
and CHECK constraints.

Use the PostgreSQL-native export:

```bash
bash ./tools/export_complete_schema.sh
```

To choose the output file:

```bash
bash ./tools/export_complete_schema.sh --output-path ./dist/emr_pacs_db_schema.sql
```

The script:

1. Runs PostgreSQL 18 `pg_dump --schema-only`.
2. Restores the SQL into a temporary validation database.
3. Compares source and restored public schema object counts.
4. Deletes the temporary database unless `--keep-temp` is passed.

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
