-- V205: Finalize the native-partition maintenance contract.
--
-- MIGRATION-SAFETY: allowed-data-loss
-- MIGRATION-SAFETY: constraint-guard-reviewed
-- The removed future_months column duplicates future_partitions and contains
-- no independent business data. Rollback is to add future_months back and copy
-- future_partitions into it. Removed CHECK constraints are exact duplicates or
-- weaker predecessors of constraints retained below.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Main Worklist/Study tables are source-of-truth tables, not partition
-- maintenance targets. Remove their legacy disabled config rows entirely.
DELETE FROM public.partition_maintenance_configs
WHERE parent_schema = 'public'
  AND parent_table IN ('pacs_worklists', 'pacs_studies');

ALTER TABLE public.partition_maintenance_configs
    DROP CONSTRAINT IF EXISTS chk_partition_maintenance_active,
    DROP CONSTRAINT IF EXISTS chk_partition_config_retention;

ALTER TABLE public.partition_maintenance_configs
    DROP COLUMN IF EXISTS future_months;

ALTER TABLE public.partition_maintenance_configs
    ADD CONSTRAINT chk_partition_config_retention
    CHECK (
        (
            retention_mode = 'FIXED_MONTHS'
            AND retention_months IS NOT NULL
            AND retention_months > 0
            AND partition_granularity = 'MONTH'
            AND allow_auto_drop = TRUE
        )
        OR
        (
            retention_mode = 'POLICY_BASED'
            AND partition_granularity = 'YEAR'
            AND allow_auto_drop = FALSE
        )
    );

INSERT INTO public.partition_maintenance_configs (
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
    ('public', 'user_logs', 'created', 'MONTH', 'FIXED_MONTHS', 12, 3, TRUE, 1),
    ('public', 'system_activities', 'created', 'MONTH', 'FIXED_MONTHS', 12, 3, TRUE, 1),
    ('public', 'dicom_server_callback_log', 'received_at', 'MONTH', 'FIXED_MONTHS', 12, 3, TRUE, 1),
    ('public', 'pacs_realtime_notification_events', 'created_at', 'MONTH', 'FIXED_MONTHS', 12, 3, TRUE, 1),
    ('public', 'pacs_worklist_histories', 'created', 'YEAR', 'POLICY_BASED', NULL, 2, FALSE, 1),
    ('public', 'study_retention_delete_requests', 'created_at', 'YEAR', 'POLICY_BASED', NULL, 2, FALSE, 1)
ON CONFLICT (parent_schema, parent_table)
DO UPDATE SET
    partition_column = EXCLUDED.partition_column,
    partition_granularity = EXCLUDED.partition_granularity,
    retention_mode = EXCLUDED.retention_mode,
    retention_months = EXCLUDED.retention_months,
    future_partitions = EXCLUDED.future_partitions,
    allow_auto_drop = EXCLUDED.allow_auto_drop,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- Remove exact duplicate constraints while retaining their canonical or
-- stricter equivalents. This avoids duplicate validation work on writes.
ALTER TABLE public.hospital_dicom_machines
    DROP CONSTRAINT IF EXISTS chk_hdm_machine_port;

ALTER TABLE public.oauth2_clients
    DROP CONSTRAINT IF EXISTS chk_oauth2_clients_access_lifetime,
    DROP CONSTRAINT IF EXISTS chk_oauth2_clients_refresh_lifetime;

ALTER TABLE public.pacs_results
    DROP CONSTRAINT IF EXISTS chk_pacs_results_active,
    DROP CONSTRAINT IF EXISTS chk_pacs_results_reference;

ALTER TABLE public.pacs_studies
    DROP CONSTRAINT IF EXISTS chk_pacs_studies_is_active,
    DROP CONSTRAINT IF EXISTS chk_pacs_studies_status;

ALTER TABLE public.study_retention_policies
    DROP CONSTRAINT IF EXISTS chk_retention_unit_final;

COMMENT ON TABLE public.partition_maintenance_configs IS
    'Configures six native partitioned parents using one future_partitions setting: fixed monthly technical logs and policy-based yearly medical/audit tables.';

SELECT public.create_future_partitions();
SELECT public.refresh_pacs_week_cache();
