\set ON_ERROR_STOP on

-- Install only after a shadow table has been created with PARTITION BY RANGE.
-- Directly converting the current tables is intentionally not attempted:
-- their PK/FK graph and PostgreSQL partition-key uniqueness rules require a
-- staged table-copy/swap deployment.
CREATE OR REPLACE FUNCTION public.pacs_ensure_monthly_partition(
    parent_table regclass,
    month_start date
) RETURNS regclass
LANGUAGE plpgsql
SECURITY INVOKER
AS $$
DECLARE
    parent_schema text;
    parent_name text;
    partition_name text;
    start_date date := date_trunc('month', month_start)::date;
    end_date date := (date_trunc('month', month_start) + interval '1 month')::date;
    created_partition regclass;
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
        parent_schema,
        partition_name,
        parent_table,
        start_date,
        end_date
    );

    created_partition := to_regclass(format('%I.%I', parent_schema, partition_name));
    RETURN created_partition;
END
$$;

-- Example after creating public.system_activities_v2 PARTITION BY RANGE(created):
-- SELECT public.pacs_ensure_monthly_partition(
--     'public.system_activities_v2'::regclass,
--     month_value::date
-- )
-- FROM generate_series(
--     date_trunc('month', current_date) - interval '1 month',
--     date_trunc('month', current_date) + interval '12 months',
--     interval '1 month'
-- ) AS month_value;

-- Recommended shadow-table order:
-- 1. system_activities, user_logs, dicom_server_callback_log,
--    pacs_realtime_notification_events (few inbound FKs).
-- 2. pacs_worklist_histories and study_retention_delete_requests.
-- 3. pacs_studies and pacs_worklists only after their FK/PK swap design is
--    rehearsed. Use received_at/created_at, not nullable/unreliable study_date.
