-- V192: Convert append-only log tables to monthly RANGE partitioning.
--
-- MIGRATION-SAFETY: allowed-data-loss
-- The only DROP TABLE removes the temporary <table>_premig after its rows were
-- copied into the new partitioned table within the SAME transaction, so a
-- failure rolls back atomically and no data is lost. Backup/rollback: take a
-- logical backup (pg_dump) of the four target tables before deploy; to revert,
-- restore them as plain (non-partitioned) tables. Tables above the row-count
-- guard are skipped and must use the online shadow cutover instead (see
-- docs/database/PERFORMANCE_REFACTOR_V189_PLUS.md).
--
-- Runs inside a single transaction (DDL is transactional in PostgreSQL) so a
-- failure rolls back cleanly. The conversion is guarded by a row-count ceiling:
-- environments whose table already exceeds the guard are SKIPPED (left as a
-- normal table) and must instead use the online shadow+cutover procedure in
-- docs/database/PERFORMANCE_REFACTOR_V189_PLUS.md. This keeps the migration
-- safe to promote to QA/prod while still partitioning small/DEV tables in place.
--
-- SCOPE: system_activities, user_logs, pacs_worklist_histories,
-- study_retention_delete_requests. Their only non-PK unique is public_id, which
-- folds harmlessly into the partition key (UUIDs are globally unique by
-- construction).
--
-- DEFERRED (intentionally NOT partitioned here): dicom_server_callback_log and
-- pacs_realtime_notification_events. Both enforce idempotency with a
-- (hospital_id, dedupe_key) unique consumed by an ON CONFLICT UPSERT. A
-- partitioned table requires every unique to include the partition key, which
-- would (a) silently weaken cross-month dedupe and (b) break the existing
-- "ON CONFLICT (hospital_id, dedupe_key)" inference. Partitioning them requires
-- an application change first (see the deliverable doc).
--
-- Partition keys are the reliable NOT NULL time columns actually used by the
-- mappers for ordering/filtering, so pruning aligns with real queries.

-- Shared maintenance helper (also called by the application scheduler) ---------
CREATE OR REPLACE FUNCTION pacs_ensure_monthly_partition(
    parent_table regclass,
    month_start date
) RETURNS regclass
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    parent_schema text;
    parent_name text;
    partition_name text;
    start_date date := date_trunc('month', month_start)::date;
    end_date date := (date_trunc('month', month_start) + interval '1 month')::date;
BEGIN
    SELECT ns.nspname, cls.relname
    INTO parent_schema, parent_name
    FROM pg_class cls
    JOIN pg_namespace ns ON ns.oid = cls.relnamespace
    WHERE cls.oid = parent_table
      AND cls.relkind = 'p';

    IF parent_name IS NULL THEN
        RAISE EXCEPTION '% is not a partitioned table', parent_table;
    END IF;

    partition_name := parent_name || '_' || to_char(start_date, 'YYYYMM');
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
        parent_schema, partition_name, parent_table, start_date, end_date
    );
    RETURN to_regclass(format('%I.%I', parent_schema, partition_name));
END
$fn$;

-- One-shot conversion helper (dropped at the end of this migration) -----------
CREATE OR REPLACE FUNCTION pacs__convert_to_monthly_partition(
    p_schema text,
    p_table text,
    p_key text,
    p_max_rows bigint
) RETURNS void
LANGUAGE plpgsql
AS $fn$
DECLARE
    rel regclass := to_regclass(format('%I.%I', p_schema, p_table));
    old_name text := p_table || '_premig';
    parent_qual text := format('%I.%I', p_schema, p_table);
    row_estimate bigint;
    nonuniq_idx text[] := '{}';
    check_defs text[] := '{}';
    fk_defs text[] := '{}';
    uq_defs text[] := '{}';
    seq_name text;
    m date;
    stmt text;
BEGIN
    IF rel IS NULL THEN
        RAISE NOTICE 'V192 skip % (table missing)', p_table;
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_class WHERE oid = rel AND relkind = 'p') THEN
        RAISE NOTICE 'V192 skip % (already partitioned)', p_table;
        RETURN;
    END IF;

    EXECUTE format('SELECT count(*) FROM %s', rel) INTO row_estimate;
    IF row_estimate > p_max_rows THEN
        RAISE NOTICE 'V192 skip % (% rows exceeds guard %, use online cutover)',
            p_table, row_estimate, p_max_rows;
        RETURN;
    END IF;

    -- Reject expression-based uniques (none exist on the targeted tables, but
    -- this keeps the helper safe if reused).
    IF EXISTS (
        SELECT 1 FROM pg_index i
        WHERE i.indrelid = rel AND i.indisunique AND NOT i.indisprimary
          AND i.indexprs IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'V192 cannot fold expression-based unique on %', p_table;
    END IF;

    -- Capture definitions while original names are still attached.
    SELECT array_agg(pg_get_indexdef(i.indexrelid))
      INTO nonuniq_idx
      FROM pg_index i
     WHERE i.indrelid = rel AND NOT i.indisprimary AND NOT i.indisunique;

    SELECT array_agg(format('ALTER TABLE %s ADD CONSTRAINT %I %s',
                            parent_qual, c.conname, pg_get_constraintdef(c.oid)))
      INTO check_defs
      FROM pg_constraint c
     WHERE c.conrelid = rel AND c.contype = 'c';

    SELECT array_agg(format('ALTER TABLE %s ADD CONSTRAINT %I %s',
                            parent_qual, c.conname, pg_get_constraintdef(c.oid)))
      INTO fk_defs
      FROM pg_constraint c
     WHERE c.conrelid = rel AND c.contype = 'f';

    SELECT s.relname
      INTO seq_name
      FROM pg_depend d
      JOIN pg_class s ON s.oid = d.objid AND s.relkind = 'S'
     WHERE d.refobjid = rel AND d.deptype = 'a'
     LIMIT 1;

    -- Build folded unique-index statements (append the partition key if absent).
    SELECT array_agg(
               format('CREATE UNIQUE INDEX %I ON %s (%s%s)%s',
                      c.relname,
                      parent_qual,
                      cc.cols,
                      CASE WHEN EXISTS (
                          SELECT 1
                          FROM unnest(i.indkey) AS kk(attnum)
                          JOIN pg_attribute a2 ON a2.attrelid = i.indrelid AND a2.attnum = kk.attnum
                          WHERE a2.attname = p_key
                      ) THEN '' ELSE ', ' || quote_ident(p_key) END,
                      CASE WHEN i.indpred IS NOT NULL
                           THEN ' WHERE ' || pg_get_expr(i.indpred, i.indrelid)
                           ELSE '' END))
      INTO uq_defs
      FROM pg_index i
      JOIN pg_class c ON c.oid = i.indexrelid
      CROSS JOIN LATERAL (
          SELECT string_agg(quote_ident(a.attname), ', ' ORDER BY k.ord) AS cols
          FROM unnest(i.indkey) WITH ORDINALITY AS k(attnum, ord)
          JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum
      ) cc
     WHERE i.indrelid = rel AND i.indisunique AND NOT i.indisprimary;

    -- 1. Rename the original out of the way.
    EXECUTE format('ALTER TABLE %s RENAME TO %I', rel, old_name);

    -- 2. Create the partitioned shell (columns + NOT NULL + defaults + storage).
    EXECUTE format(
        'CREATE TABLE %I.%I (LIKE %I.%I INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS) PARTITION BY RANGE (%I)',
        p_schema, p_table, p_schema, old_name, p_key);

    -- 3. Prior + current + 3 future monthly partitions, plus a DEFAULT catch-all
    --    that also absorbs older historical rows.
    FOR m IN
        SELECT generate_series(
            date_trunc('month', current_date) - interval '1 month',
            date_trunc('month', current_date) + interval '3 months',
            interval '1 month')::date
    LOOP
        PERFORM pacs_ensure_monthly_partition(parent_qual::regclass, m);
    END LOOP;
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %s DEFAULT',
                   p_schema, p_table || '_default', parent_qual);

    -- 4. Copy data (explicit id values are supplied, so the sequence is untouched).
    EXECUTE format('INSERT INTO %s SELECT * FROM %I.%I', parent_qual, p_schema, old_name);

    -- 5. Detach the owned sequence so dropping the old table does not remove it.
    IF seq_name IS NOT NULL THEN
        EXECUTE format('ALTER SEQUENCE %I.%I OWNED BY NONE', p_schema, seq_name);
    END IF;

    -- 6. Drop the original, freeing every constraint/index name.
    EXECUTE format('DROP TABLE %I.%I', p_schema, old_name);

    -- 7. Primary key folded with the partition key.
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = p_schema AND table_name = p_table AND column_name = 'id'
    ) THEN
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I PRIMARY KEY (id, %I)',
                       parent_qual, p_table || '_pkey', p_key);
        IF seq_name IS NOT NULL THEN
            EXECUTE format('ALTER SEQUENCE %I.%I OWNED BY %s.id', p_schema, seq_name, parent_qual);
        END IF;
    END IF;

    -- 8. Folded unique indexes.
    IF uq_defs IS NOT NULL THEN
        FOREACH stmt IN ARRAY uq_defs LOOP EXECUTE stmt; END LOOP;
    END IF;

    -- 9. CHECK constraints, then non-unique indexes, then foreign keys.
    IF check_defs IS NOT NULL THEN
        FOREACH stmt IN ARRAY check_defs LOOP EXECUTE stmt; END LOOP;
    END IF;
    IF nonuniq_idx IS NOT NULL THEN
        FOREACH stmt IN ARRAY nonuniq_idx LOOP EXECUTE stmt; END LOOP;
    END IF;
    IF fk_defs IS NOT NULL THEN
        FOREACH stmt IN ARRAY fk_defs LOOP EXECUTE stmt; END LOOP;
    END IF;

    RAISE NOTICE 'V192 partitioned % by % (% rows copied)', p_table, p_key, row_estimate;
END
$fn$;

DO $$
BEGIN
    PERFORM pacs__convert_to_monthly_partition('public', 'system_activities',             'created',    2000000);
    PERFORM pacs__convert_to_monthly_partition('public', 'user_logs',                     'created',    2000000);
    PERFORM pacs__convert_to_monthly_partition('public', 'pacs_worklist_histories',       'created',    2000000);
    PERFORM pacs__convert_to_monthly_partition('public', 'study_retention_delete_requests','created_at', 2000000);
END;
$$;

DROP FUNCTION pacs__convert_to_monthly_partition(text, text, text, bigint);

COMMENT ON FUNCTION pacs_ensure_monthly_partition(regclass, date) IS
    'Idempotently creates the monthly partition covering month_start for a RANGE-partitioned table. Called by the application partition-maintenance scheduler.';
