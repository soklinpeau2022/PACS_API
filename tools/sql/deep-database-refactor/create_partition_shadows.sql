\set ON_ERROR_STOP on

-- Staging-only template. Do not run against production until the application
-- dual-write/cutover procedure has been rehearsed. Shadow tables intentionally
-- use (id, partition_key) primary keys because PostgreSQL requires every unique
-- key on a partitioned table to include the partition key.

CREATE SCHEMA IF NOT EXISTS pacs_partition_stage;

CREATE TABLE IF NOT EXISTS pacs_partition_stage.system_activities_v2
    (LIKE public.system_activities INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS)
    PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS pacs_partition_stage.user_logs_v2
    (LIKE public.user_logs INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS)
    PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS pacs_partition_stage.dicom_server_callback_log_v2
    (LIKE public.dicom_server_callback_log INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS)
    PARTITION BY RANGE (received_at);

CREATE TABLE IF NOT EXISTS pacs_partition_stage.pacs_realtime_notification_events_v2
    (LIKE public.pacs_realtime_notification_events INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS)
    PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS pacs_partition_stage.pacs_worklist_histories_v2
    (LIKE public.pacs_worklist_histories INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS)
    PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS pacs_partition_stage.study_retention_delete_requests_v2
    (LIKE public.study_retention_delete_requests INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS)
    PARTITION BY RANGE (created_at);

DO $$
DECLARE
    item RECORD;
    month_value DATE;
    partition_name TEXT;
    end_value DATE;
BEGIN
    FOR item IN
        SELECT *
        FROM (VALUES
            ('system_activities_v2', 'created_at'),
            ('user_logs_v2', 'created_at'),
            ('dicom_server_callback_log_v2', 'received_at'),
            ('pacs_realtime_notification_events_v2', 'created_at'),
            ('pacs_worklist_histories_v2', 'created_at'),
            ('study_retention_delete_requests_v2', 'created_at')
        ) AS definitions(parent_name, partition_column)
    LOOP
        FOR month_value IN
            SELECT generate_series(
                date_trunc('month', CURRENT_DATE) - INTERVAL '1 month',
                date_trunc('month', CURRENT_DATE) + INTERVAL '12 months',
                INTERVAL '1 month'
            )::date
        LOOP
            partition_name := item.parent_name || '_' || to_char(month_value, 'YYYYMM');
            end_value := (month_value + INTERVAL '1 month')::date;
            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS pacs_partition_stage.%I '
                || 'PARTITION OF pacs_partition_stage.%I '
                || 'FOR VALUES FROM (%L) TO (%L)',
                partition_name,
                item.parent_name,
                month_value,
                end_value
            );
        END LOOP;
    END LOOP;
END;
$$;

CREATE TABLE IF NOT EXISTS pacs_partition_stage.system_activities_v2_default
    PARTITION OF pacs_partition_stage.system_activities_v2 DEFAULT;
CREATE TABLE IF NOT EXISTS pacs_partition_stage.user_logs_v2_default
    PARTITION OF pacs_partition_stage.user_logs_v2 DEFAULT;
CREATE TABLE IF NOT EXISTS pacs_partition_stage.dicom_server_callback_log_v2_default
    PARTITION OF pacs_partition_stage.dicom_server_callback_log_v2 DEFAULT;
CREATE TABLE IF NOT EXISTS pacs_partition_stage.pacs_realtime_notification_events_v2_default
    PARTITION OF pacs_partition_stage.pacs_realtime_notification_events_v2 DEFAULT;
CREATE TABLE IF NOT EXISTS pacs_partition_stage.pacs_worklist_histories_v2_default
    PARTITION OF pacs_partition_stage.pacs_worklist_histories_v2 DEFAULT;
CREATE TABLE IF NOT EXISTS pacs_partition_stage.study_retention_delete_requests_v2_default
    PARTITION OF pacs_partition_stage.study_retention_delete_requests_v2 DEFAULT;

CREATE INDEX IF NOT EXISTS idx_system_activities_v2_created_id
    ON pacs_partition_stage.system_activities_v2 (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_user_logs_v2_user_created_id
    ON pacs_partition_stage.user_logs_v2 (user_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_callback_log_v2_hospital_received_id
    ON pacs_partition_stage.dicom_server_callback_log_v2 (hospital_id, received_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_realtime_events_v2_hospital_created_id
    ON pacs_partition_stage.pacs_realtime_notification_events_v2 (hospital_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_worklist_histories_v2_scope_created_id
    ON pacs_partition_stage.pacs_worklist_histories_v2 (hospital_id, worklist_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_retention_requests_v2_scope_created_id
    ON pacs_partition_stage.study_retention_delete_requests_v2 (hospital_id, status, created_at DESC, id DESC);

