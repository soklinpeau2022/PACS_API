-- V203: Fix output-column ambiguity in the policy partition dry-run.
--
-- MIGRATION-SAFETY: allowed-data-loss
-- The function can drop a past native yearly partition only when explicitly
-- called with false and every row is purge eligible. This migration only
-- replaces the function definition and does not invoke the destructive path.

CREATE OR REPLACE FUNCTION drop_policy_partitions_if_fully_expired(p_dry_run BOOLEAN DEFAULT TRUE)
RETURNS TABLE(
    parent_table TEXT,
    partition_table TEXT,
    total_rows BIGINT,
    protected_rows BIGINT,
    action_taken TEXT
)
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    cfg RECORD;
    parent_reg REGCLASS;
    child RECORD;
    child_reg REGCLASS;
    archive_reg REGCLASS;
    total_count BIGINT;
    protected_count BIGINT;
    archived_count BIGINT;
    partition_year INTEGER;
BEGIN
    FOR cfg IN
        SELECT
            config.parent_schema,
            config.parent_table
        FROM partition_maintenance_configs config
        WHERE config.is_active = 1
          AND config.parent_schema = 'public'
          AND config.parent_table IN (
              'pacs_worklist_histories',
              'study_retention_delete_requests'
          )
          AND config.retention_mode = 'POLICY_BASED'
          AND config.partition_granularity = 'YEAR'
          AND config.allow_auto_drop = FALSE
        ORDER BY config.parent_table
    LOOP
        parent_reg := TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table));
        IF parent_reg IS NULL THEN
            CONTINUE;
        END IF;

        archive_reg := TO_REGCLASS(
            FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table || '_archive')
        );

        FOR child IN
            SELECT
                child_ns.nspname AS child_schema,
                child_cls.relname AS child_table
            FROM pg_inherits inheritance
            JOIN pg_class child_cls
              ON child_cls.oid = inheritance.inhrelid
            JOIN pg_namespace child_ns
              ON child_ns.oid = child_cls.relnamespace
            WHERE inheritance.inhparent = parent_reg
              AND child_cls.relispartition
              AND child_cls.relname ~ ('^' || cfg.parent_table || '_[0-9]{4}$')
            ORDER BY child_cls.relname
        LOOP
            partition_year := RIGHT(child.child_table, 4)::INTEGER;
            IF partition_year >= EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER THEN
                RETURN QUERY
                SELECT
                    cfg.parent_table::TEXT,
                    child.child_table::TEXT,
                    0::BIGINT,
                    0::BIGINT,
                    'kept_current_or_future_year'::TEXT;
                CONTINUE;
            END IF;

            child_reg := TO_REGCLASS(
                FORMAT('%I.%I', child.child_schema, child.child_table)
            );

            EXECUTE FORMAT('SELECT COUNT(*) FROM %s', child_reg)
            INTO total_count;

            EXECUTE FORMAT(
                'SELECT COUNT(*) FROM %s WHERE purge_after IS NULL OR purge_after >= CURRENT_DATE',
                child_reg
            )
            INTO protected_count;

            IF total_count = 0 OR protected_count > 0 THEN
                RETURN QUERY
                SELECT
                    cfg.parent_table::TEXT,
                    child.child_table::TEXT,
                    total_count,
                    protected_count,
                    'kept'::TEXT;
                CONTINUE;
            END IF;

            IF p_dry_run THEN
                RETURN QUERY
                SELECT
                    cfg.parent_table::TEXT,
                    child.child_table::TEXT,
                    total_count,
                    protected_count,
                    'dry_run_drop_candidate'::TEXT;
                CONTINUE;
            END IF;

            archived_count := 0;
            IF archive_reg IS NOT NULL THEN
                archived_count := pacs_archive_policy_rows(child_reg, archive_reg, 'TRUE');
            END IF;

            EXECUTE FORMAT(
                'DROP TABLE IF EXISTS %I.%I',
                child.child_schema,
                child.child_table
            );

            RETURN QUERY
            SELECT
                cfg.parent_table::TEXT,
                child.child_table::TEXT,
                total_count,
                protected_count,
                CASE
                    WHEN archive_reg IS NOT NULL
                        THEN FORMAT('archived_%s_and_dropped', archived_count)
                    ELSE 'dropped'
                END;
        END LOOP;
    END LOOP;
END
$fn$;
