-- V195: Config-driven monthly partition maintenance.
--
-- MIGRATION-SAFETY: allowed-data-loss
-- This migration defines drop_old_monthly_partitions(), which may later drop
-- old native child partitions according to partition_maintenance_configs.
-- The migration itself does not drop data. Before enabling short retention in
-- shared environments, take a logical backup of the affected parent tables or
-- increase retention_months in partition_maintenance_configs.

CREATE TABLE IF NOT EXISTS partition_maintenance_configs (
    id BIGSERIAL PRIMARY KEY,
    parent_schema VARCHAR(80) NOT NULL DEFAULT 'public',
    parent_table VARCHAR(160) NOT NULL,
    partition_column VARCHAR(160) NOT NULL,
    retention_months INTEGER NOT NULL DEFAULT 12,
    future_months INTEGER NOT NULL DEFAULT 3,
    is_active SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_partition_maintenance_active CHECK (is_active IN (1, 2)),
    CONSTRAINT chk_partition_maintenance_retention CHECK (retention_months > 0),
    CONSTRAINT chk_partition_maintenance_future CHECK (future_months >= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_partition_maintenance_parent
    ON partition_maintenance_configs (parent_schema, parent_table);

INSERT INTO partition_maintenance_configs
    (parent_schema, parent_table, partition_column, retention_months, future_months, is_active)
VALUES
    ('public', 'user_logs', 'created', 12, 3, 1),
    ('public', 'system_activities', 'created', 12, 3, 1),
    ('public', 'dicom_server_callback_log', 'received_at', 12, 3, 1),
    ('public', 'pacs_realtime_notification_events', 'created_at', 12, 3, 1),
    ('public', 'pacs_worklist_histories', 'created', 12, 3, 1),
    ('public', 'study_retention_delete_requests', 'created_at', 12, 3, 1)
ON CONFLICT (parent_schema, parent_table)
DO UPDATE SET
    partition_column = EXCLUDED.partition_column,
    retention_months = EXCLUDED.retention_months,
    future_months = EXCLUDED.future_months,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

CREATE OR REPLACE FUNCTION create_future_monthly_partitions()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    cfg RECORD;
    parent_reg REGCLASS;
    parent_kind "char";
    parent_qual TEXT;
    partition_name TEXT;
    partition_qual TEXT;
    default_name TEXT;
    month_value DATE;
    end_value DATE;
    created_count INTEGER := 0;
BEGIN
    FOR cfg IN
        SELECT parent_schema, parent_table, partition_column, future_months
        FROM partition_maintenance_configs
        WHERE is_active = 1
        ORDER BY parent_schema, parent_table
    LOOP
        parent_qual := FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table);
        parent_reg := TO_REGCLASS(parent_qual);

        IF parent_reg IS NULL THEN
            RAISE NOTICE 'partition maintenance skip %.%: parent table missing',
                cfg.parent_schema, cfg.parent_table;
            CONTINUE;
        END IF;

        SELECT relkind INTO parent_kind
        FROM pg_class
        WHERE oid = parent_reg;

        IF parent_kind <> 'p' THEN
            RAISE NOTICE 'partition maintenance skip %.%: table is not a native partitioned parent',
                cfg.parent_schema, cfg.parent_table;
            CONTINUE;
        END IF;

        default_name := cfg.parent_table || '_default';
        IF TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, default_name)) IS NULL THEN
            EXECUTE FORMAT(
                'CREATE TABLE %I.%I PARTITION OF %s DEFAULT',
                cfg.parent_schema, default_name, parent_qual
            );
            created_count := created_count + 1;
            RAISE NOTICE 'created default partition %.%', cfg.parent_schema, default_name;
        ELSE
            RAISE NOTICE 'default partition exists %.%', cfg.parent_schema, default_name;
        END IF;

        FOR month_value IN
            SELECT GENERATE_SERIES(
                DATE_TRUNC('month', CURRENT_DATE)::DATE,
                (DATE_TRUNC('month', CURRENT_DATE) + MAKE_INTERVAL(months => cfg.future_months))::DATE,
                INTERVAL '1 month'
            )::DATE
        LOOP
            end_value := (month_value + INTERVAL '1 month')::DATE;
            partition_name := cfg.parent_table || '_' || TO_CHAR(month_value, 'YYYYMM');
            partition_qual := FORMAT('%I.%I', cfg.parent_schema, partition_name);

            IF TO_REGCLASS(partition_qual) IS NULL THEN
                EXECUTE FORMAT(
                    'CREATE TABLE %I.%I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
                    cfg.parent_schema, partition_name, parent_qual, month_value, end_value
                );
                created_count := created_count + 1;
                RAISE NOTICE 'created monthly partition %.% from % to %',
                    cfg.parent_schema, partition_name, month_value, end_value;
            ELSE
                RAISE NOTICE 'monthly partition exists %.%', cfg.parent_schema, partition_name;
            END IF;
        END LOOP;
    END LOOP;

    RETURN created_count;
END
$fn$;

CREATE OR REPLACE FUNCTION drop_old_monthly_partitions()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    cfg RECORD;
    parent_reg REGCLASS;
    parent_kind "char";
    child RECORD;
    suffix TEXT;
    partition_month DATE;
    cutoff_month DATE;
    dropped_count INTEGER := 0;
BEGIN
    FOR cfg IN
        SELECT parent_schema, parent_table, retention_months
        FROM partition_maintenance_configs
        WHERE is_active = 1
        ORDER BY parent_schema, parent_table
    LOOP
        parent_reg := TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table));
        IF parent_reg IS NULL THEN
            RAISE NOTICE 'old partition drop skip %.%: parent table missing',
                cfg.parent_schema, cfg.parent_table;
            CONTINUE;
        END IF;

        SELECT relkind INTO parent_kind
        FROM pg_class
        WHERE oid = parent_reg;

        IF parent_kind <> 'p' THEN
            RAISE NOTICE 'old partition drop skip %.%: table is not a native partitioned parent',
                cfg.parent_schema, cfg.parent_table;
            CONTINUE;
        END IF;

        cutoff_month := (DATE_TRUNC('month', CURRENT_DATE) - MAKE_INTERVAL(months => cfg.retention_months))::DATE;

        FOR child IN
            SELECT child_ns.nspname AS child_schema, child_cls.relname AS child_table
            FROM pg_inherits inh
            JOIN pg_class child_cls ON child_cls.oid = inh.inhrelid
            JOIN pg_namespace child_ns ON child_ns.oid = child_cls.relnamespace
            WHERE inh.inhparent = parent_reg
            ORDER BY child_cls.relname
        LOOP
            IF child.child_table = cfg.parent_table || '_default' THEN
                RAISE NOTICE 'old partition drop keep default %.%',
                    child.child_schema, child.child_table;
                CONTINUE;
            END IF;

            IF child.child_table !~ ('^' || cfg.parent_table || '_[0-9]{4}(0[1-9]|1[0-2])$') THEN
                RAISE NOTICE 'old partition drop skip %.%: name does not match monthly partition format',
                    child.child_schema, child.child_table;
                CONTINUE;
            END IF;

            suffix := RIGHT(child.child_table, 6);
            partition_month := TO_DATE(suffix, 'YYYYMM');

            IF partition_month < cutoff_month THEN
                RAISE NOTICE 'dropping old monthly partition %.% (% older than cutoff %)',
                    child.child_schema, child.child_table, partition_month, cutoff_month;
                EXECUTE FORMAT('DROP TABLE IF EXISTS %I.%I', child.child_schema, child.child_table);
                dropped_count := dropped_count + 1;
            ELSE
                RAISE NOTICE 'old partition drop keep %.% (% >= cutoff %)',
                    child.child_schema, child.child_table, partition_month, cutoff_month;
            END IF;
        END LOOP;
    END LOOP;

    RETURN dropped_count;
END
$fn$;

CREATE OR REPLACE FUNCTION run_monthly_partition_maintenance()
RETURNS TEXT
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    cfg RECORD;
    parent_reg REGCLASS;
    parent_kind "char";
    created_count INTEGER := 0;
    dropped_count INTEGER := 0;
    analyzed_count INTEGER := 0;
    summary TEXT;
BEGIN
    SELECT create_future_monthly_partitions() INTO created_count;
    SELECT drop_old_monthly_partitions() INTO dropped_count;

    FOR cfg IN
        SELECT parent_schema, parent_table
        FROM partition_maintenance_configs
        WHERE is_active = 1
        ORDER BY parent_schema, parent_table
    LOOP
        parent_reg := TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table));
        IF parent_reg IS NULL THEN
            CONTINUE;
        END IF;

        SELECT relkind INTO parent_kind
        FROM pg_class
        WHERE oid = parent_reg;

        IF parent_kind = 'p' THEN
            EXECUTE FORMAT('ANALYZE %I.%I', cfg.parent_schema, cfg.parent_table);
            analyzed_count := analyzed_count + 1;
            RAISE NOTICE 'analyzed partitioned parent %.%', cfg.parent_schema, cfg.parent_table;
        END IF;
    END LOOP;

    summary := FORMAT(
        'partition maintenance complete: created=%s, dropped=%s, analyzed=%s',
        created_count,
        dropped_count,
        analyzed_count
    );
    RAISE NOTICE '%', summary;
    RETURN summary;
END
$fn$;

CREATE OR REPLACE FUNCTION schedule_monthly_partition_maintenance_pg_cron()
RETURNS TEXT
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    job_id BIGINT;
BEGIN
    IF TO_REGNAMESPACE('cron') IS NULL THEN
        RETURN 'pg_cron schema is not installed; use the Spring Boot scheduler.';
    END IF;

    BEGIN
        EXECUTE 'SELECT cron.unschedule($1)'
        USING 'emr-pacs-monthly-partition-maintenance';
    EXCEPTION
        WHEN OTHERS THEN
            NULL;
    END;

    BEGIN
        EXECUTE 'SELECT cron.schedule($1, $2, $3)'
        INTO job_id
        USING
            'emr-pacs-monthly-partition-maintenance',
            '0 2 1 * *',
            'SELECT run_monthly_partition_maintenance();';
    EXCEPTION
        WHEN undefined_function OR invalid_schema_name THEN
            RETURN 'pg_cron functions are not available; use the Spring Boot scheduler.';
    END;

    RETURN FORMAT('pg_cron scheduled monthly partition maintenance with job id %s', job_id);
END
$fn$;

CREATE OR REPLACE FUNCTION unschedule_monthly_partition_maintenance_pg_cron()
RETURNS TEXT
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    unscheduled BOOLEAN;
BEGIN
    IF TO_REGNAMESPACE('cron') IS NULL THEN
        RETURN 'pg_cron schema is not installed.';
    END IF;

    BEGIN
        EXECUTE 'SELECT cron.unschedule($1)'
        INTO unscheduled
        USING 'emr-pacs-monthly-partition-maintenance';
    EXCEPTION
        WHEN undefined_function OR invalid_schema_name THEN
            RETURN 'pg_cron functions are not available.';
    END;

    RETURN FORMAT('pg_cron monthly partition maintenance unscheduled=%s', COALESCE(unscheduled, FALSE));
END
$fn$;

COMMENT ON TABLE partition_maintenance_configs IS
    'Configures native monthly partition creation and retention for high-growth EMR/PACS tables.';
COMMENT ON FUNCTION create_future_monthly_partitions() IS
    'Creates current and configured future monthly partitions, plus default partitions, for active native partitioned parents.';
COMMENT ON FUNCTION drop_old_monthly_partitions() IS
    'Drops only native child partitions named parent_YYYYMM older than retention_months; never drops parent/default tables.';
COMMENT ON FUNCTION run_monthly_partition_maintenance() IS
    'Runs create_future_monthly_partitions, drop_old_monthly_partitions, and ANALYZE for active partitioned parents.';
COMMENT ON FUNCTION schedule_monthly_partition_maintenance_pg_cron() IS
    'Optionally schedules run_monthly_partition_maintenance with pg_cron when pg_cron is installed.';
