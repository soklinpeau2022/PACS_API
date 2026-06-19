\set ON_ERROR_STOP on
\pset pager off

-- Disposable transaction test for the partition-maintenance functions.
-- It creates a test partitioned parent/config row, runs create/drop, verifies
-- parent/default safety, and rolls everything back at the end.

BEGIN;

CREATE SCHEMA IF NOT EXISTS partition_maintenance_test;

CREATE TABLE IF NOT EXISTS partition_maintenance_test.events (
    id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    payload TEXT,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS partition_maintenance_test.events_default
    PARTITION OF partition_maintenance_test.events DEFAULT;

CREATE TABLE IF NOT EXISTS partition_maintenance_test.events_202001
    PARTITION OF partition_maintenance_test.events
    FOR VALUES FROM ('2020-01-01') TO ('2020-02-01');

INSERT INTO partition_maintenance_configs
    (
        parent_schema,
        parent_table,
        partition_column,
        partition_granularity,
        retention_mode,
        retention_months,
        future_partitions,
        allow_auto_drop,
        is_active
    )
VALUES
    ('partition_maintenance_test', 'events', 'created_at', 'MONTH', 'FIXED_MONTHS', 12, 3, TRUE, 1)
ON CONFLICT (parent_schema, parent_table)
DO UPDATE SET
    partition_column = EXCLUDED.partition_column,
    partition_granularity = EXCLUDED.partition_granularity,
    retention_mode = EXCLUDED.retention_mode,
    retention_months = EXCLUDED.retention_months,
    future_partitions = EXCLUDED.future_partitions,
    allow_auto_drop = EXCLUDED.allow_auto_drop,
    is_active = EXCLUDED.is_active,
    updated_at = now();

SELECT create_future_partitions() AS created_partitions;

WITH expected AS (
    SELECT 'events_' || to_char(month_value, 'YYYYMM') AS partition_name
    FROM generate_series(
        (date_trunc('month', current_date) - interval '12 months')::date,
        (date_trunc('month', current_date) + interval '3 months')::date,
        interval '1 month'
    ) month_value
)
SELECT 'missing_test_future_partition' AS check_name, partition_name
FROM expected
WHERE to_regclass(format('%I.%I', 'partition_maintenance_test', partition_name)) IS NULL;

SELECT
    'old_test_partition_before_drop' AS check_name,
    to_regclass('partition_maintenance_test.events_202001') IS NOT NULL AS exists_before_drop;

SELECT drop_expired_fixed_partitions() AS dropped_partitions;

SELECT
    'old_test_partition_after_drop' AS check_name,
    to_regclass('partition_maintenance_test.events_202001') IS NULL AS dropped_old_partition;

SELECT
    'parent_and_default_kept' AS check_name,
    to_regclass('partition_maintenance_test.events') IS NOT NULL AS parent_exists,
    to_regclass('partition_maintenance_test.events_default') IS NOT NULL AS default_exists;

SELECT run_partition_maintenance() AS maintenance_summary;

ROLLBACK;
