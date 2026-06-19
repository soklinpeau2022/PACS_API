\set ON_ERROR_STOP on
\pset pager off

-- Partition-maintenance validation.
-- Run after migrations:
--   psql -d emr_pacs_db -f tools/sql/partition-maintenance/validate_partition_maintenance.sql

SELECT
    'obsolete_future_months_column' AS check_name,
    column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'partition_maintenance_configs'
  AND column_name = 'future_months';

SELECT
    parent_schema,
    parent_table,
    partition_column,
    partition_granularity,
    retention_mode,
    retention_months,
    future_partitions,
    allow_auto_drop,
    is_active,
    updated_at
FROM partition_maintenance_configs
ORDER BY parent_schema, parent_table;

SELECT
    'unexpected_partition_config' AS check_name,
    parent_schema,
    parent_table
FROM partition_maintenance_configs
WHERE (parent_schema, parent_table) NOT IN (
    ('public', 'user_logs'),
    ('public', 'system_activities'),
    ('public', 'dicom_server_callback_log'),
    ('public', 'pacs_realtime_notification_events'),
    ('public', 'pacs_worklist_histories'),
    ('public', 'study_retention_delete_requests')
)
ORDER BY parent_schema, parent_table;

SELECT
    cfg.parent_schema,
    cfg.parent_table,
    cfg.partition_column,
    cfg.partition_granularity,
    cfg.retention_mode,
    cfg.allow_auto_drop,
    cls.relkind = 'p' AS is_partitioned,
    pg_get_partkeydef(cls.oid) AS partition_key
FROM partition_maintenance_configs cfg
LEFT JOIN pg_class cls
    ON cls.oid = to_regclass(format('%I.%I', cfg.parent_schema, cfg.parent_table))
WHERE cfg.is_active = 1
ORDER BY cfg.parent_schema, cfg.parent_table;

SELECT
    'active_config_not_native_partitioned' AS check_name,
    cfg.parent_schema,
    cfg.parent_table,
    cfg.partition_column
FROM partition_maintenance_configs cfg
LEFT JOIN pg_class cls
    ON cls.oid = to_regclass(format('%I.%I', cfg.parent_schema, cfg.parent_table))
WHERE cfg.is_active = 1
  AND COALESCE(cls.relkind <> 'p', TRUE)
ORDER BY cfg.parent_schema, cfg.parent_table;

SELECT
    'policy_table_marked_fixed_auto_drop' AS check_name,
    *
FROM partition_maintenance_configs
WHERE parent_table IN ('pacs_worklist_histories', 'study_retention_delete_requests')
  AND (retention_mode <> 'POLICY_BASED'
       OR partition_granularity <> 'YEAR'
       OR allow_auto_drop = TRUE);

SELECT
    'fake_partition_table' AS check_name,
    child_ns.nspname AS child_schema,
    child.relname AS child_table
FROM pg_class child
JOIN pg_namespace child_ns
  ON child_ns.oid = child.relnamespace
WHERE child_ns.nspname = 'public'
  AND child.relkind = 'r'
  AND (
      child.relname ~ '^(user_logs|system_activities|dicom_server_callback_log|pacs_realtime_notification_events)_[0-9]{6}$'
      OR child.relname ~ '^(pacs_worklist_histories|study_retention_delete_requests)_[0-9]{4}$'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM pg_inherits inheritance
      WHERE inheritance.inhrelid = child.oid
  )
ORDER BY child.relname;

SELECT
    parent_ns.nspname AS parent_schema,
    parent.relname AS parent_table,
    child_ns.nspname AS partition_schema,
    child.relname AS partition_table
FROM pg_inherits
JOIN pg_class parent ON parent.oid = pg_inherits.inhparent
JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
JOIN pg_class child ON child.oid = pg_inherits.inhrelid
JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace
WHERE parent.relkind = 'p'
ORDER BY parent_ns.nspname, parent.relname, child.relname;

WITH expected AS (
    SELECT
        cfg.parent_schema,
        cfg.parent_table,
        cfg.partition_granularity,
        period_start::date AS period_start,
        CASE cfg.partition_granularity
            WHEN 'MONTH' THEN cfg.parent_table || '_' || to_char(period_start, 'YYYYMM')
            WHEN 'YEAR' THEN cfg.parent_table || '_' || to_char(period_start, 'YYYY')
        END AS expected_partition
    FROM partition_maintenance_configs cfg
    CROSS JOIN LATERAL generate_series(
        CASE cfg.partition_granularity
            WHEN 'MONTH' THEN
                CASE
                    WHEN cfg.retention_mode = 'FIXED_MONTHS' THEN
                        (date_trunc('month', current_date) - make_interval(months => cfg.retention_months))::date
                    ELSE
                        date_trunc('month', current_date)::date
                END
            ELSE date_trunc('year', current_date)::date
        END,
        CASE cfg.partition_granularity
            WHEN 'MONTH' THEN (date_trunc('month', current_date) + make_interval(months => cfg.future_partitions))::date
            ELSE (date_trunc('year', current_date) + make_interval(years => cfg.future_partitions))::date
        END,
        CASE cfg.partition_granularity
            WHEN 'MONTH' THEN interval '1 month'
            ELSE interval '1 year'
        END
    ) AS period_start
    WHERE cfg.is_active = 1
      AND EXISTS (
          SELECT 1
          FROM pg_class cls
          WHERE cls.oid = to_regclass(format('%I.%I', cfg.parent_schema, cfg.parent_table))
            AND cls.relkind = 'p'
      )
)
SELECT
    'missing_future_partition' AS check_name,
    parent_schema,
    parent_table,
    partition_granularity,
    period_start,
    expected_partition
FROM expected
WHERE to_regclass(format('%I.%I', parent_schema, expected_partition)) IS NULL
ORDER BY parent_schema, parent_table, period_start;

WITH children AS (
    SELECT
        cfg.parent_schema,
        cfg.parent_table,
        cfg.retention_months,
        child_ns.nspname AS child_schema,
        child.relname AS child_table,
        right(child.relname, 6) AS yyyymm
    FROM partition_maintenance_configs cfg
    JOIN pg_class parent ON parent.oid = to_regclass(format('%I.%I', cfg.parent_schema, cfg.parent_table))
    JOIN pg_inherits inh ON inh.inhparent = parent.oid
    JOIN pg_class child ON child.oid = inh.inhrelid
    JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace
    WHERE cfg.is_active = 1
      AND cfg.retention_mode = 'FIXED_MONTHS'
      AND cfg.partition_granularity = 'MONTH'
      AND cfg.allow_auto_drop = TRUE
      AND parent.relkind = 'p'
      AND child.relname ~ ('^' || cfg.parent_table || '_[0-9]{4}(0[1-9]|1[0-2])$')
)
SELECT
    'old_fixed_partition_candidate' AS check_name,
    parent_schema,
    parent_table,
    child_schema,
    child_table,
    to_date(yyyymm, 'YYYYMM') AS partition_month,
    (date_trunc('month', current_date) - make_interval(months => retention_months))::date AS cutoff_month
FROM children
WHERE to_date(yyyymm, 'YYYYMM')
      < (date_trunc('month', current_date) - make_interval(months => retention_months))::date
ORDER BY parent_schema, parent_table, child_table;

SELECT
    'worklist_history_purge_eligible_rows' AS check_name,
    count(*) AS total
FROM pacs_worklist_histories
WHERE purge_after IS NOT NULL
  AND purge_after < current_date;

SELECT
    'retention_request_purge_eligible_rows' AS check_name,
    count(*) AS total
FROM study_retention_delete_requests
WHERE purge_after IS NOT NULL
  AND purge_after < current_date;

SELECT
    cfg.parent_schema,
    cfg.parent_table,
    format(
        'SELECT %L AS parent_table, count(*) AS default_rows FROM %I.%I;',
        cfg.parent_schema || '.' || cfg.parent_table,
        cfg.parent_schema,
        cfg.parent_table || '_default'
    ) AS default_partition_count_sql
FROM partition_maintenance_configs cfg
WHERE cfg.is_active = 1
  AND to_regclass(format('%I.%I', cfg.parent_schema, cfg.parent_table || '_default')) IS NOT NULL
ORDER BY cfg.parent_schema, cfg.parent_table;

DO $$
DECLARE
    cfg RECORD;
    default_rows BIGINT;
BEGIN
    FOR cfg IN
        SELECT parent_schema, parent_table
        FROM partition_maintenance_configs
        WHERE is_active = 1
          AND to_regclass(format('%I.%I', parent_schema, parent_table || '_default')) IS NOT NULL
        ORDER BY parent_schema, parent_table
    LOOP
        EXECUTE format('SELECT count(*) FROM %I.%I', cfg.parent_schema, cfg.parent_table || '_default')
        INTO default_rows;
        RAISE NOTICE 'default partition %.% rows=%',
            cfg.parent_schema, cfg.parent_table || '_default', default_rows;
    END LOOP;
END;
$$;

SELECT
    CASE WHEN to_regnamespace('cron') IS NULL
         THEN 'pg_cron not installed; Spring Boot scheduler should run partition maintenance'
         ELSE 'pg_cron installed; inspect cron.job for optional DB schedule'
    END AS pg_cron_status;
