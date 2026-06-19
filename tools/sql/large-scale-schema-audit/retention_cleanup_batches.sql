\set ON_ERROR_STOP on
\pset pager off

-- Override these with psql -v name=value. One invocation moves/deletes one
-- bounded batch, making the script scheduler-friendly and write-safe.
\if :{?batch_size}
\else
\set batch_size 5000
\endif
\if :{?activity_hot_months}
\else
\set activity_hot_months 6
\endif
\if :{?user_log_hot_months}
\else
\set user_log_hot_months 6
\endif
\if :{?callback_success_days}
\else
\set callback_success_days 90
\endif
\if :{?notification_days}
\else
\set notification_days 14
\endif

CREATE SCHEMA IF NOT EXISTS pacs_archive;

CREATE TABLE IF NOT EXISTS pacs_archive.system_activities
    (LIKE public.system_activities INCLUDING DEFAULTS INCLUDING CONSTRAINTS);
ALTER TABLE pacs_archive.system_activities
    ADD COLUMN IF NOT EXISTS created_at timestamptz,
    ADD COLUMN IF NOT EXISTS archived_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS pacs_archive.user_logs
    (LIKE public.user_logs INCLUDING DEFAULTS INCLUDING CONSTRAINTS);
ALTER TABLE pacs_archive.user_logs
    ADD COLUMN IF NOT EXISTS created_at timestamptz,
    ADD COLUMN IF NOT EXISTS archived_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS pacs_archive.dicom_server_callback_log
    (LIKE public.dicom_server_callback_log INCLUDING DEFAULTS INCLUDING CONSTRAINTS);
ALTER TABLE pacs_archive.dicom_server_callback_log
    ADD COLUMN IF NOT EXISTS hospital_id bigint,
    ADD COLUMN IF NOT EXISTS dicom_server_id bigint,
    ADD COLUMN IF NOT EXISTS dedupe_key varchar(64),
    ADD COLUMN IF NOT EXISTS payload_sha256 char(64),
    ADD COLUMN IF NOT EXISTS attempt_count integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS last_received_at timestamptz,
    ADD COLUMN IF NOT EXISTS archived_at timestamptz NOT NULL DEFAULT now();

WITH batch AS (
    SELECT id
    FROM public.system_activities
    WHERE created < now() - (:'activity_hot_months' || ' months')::interval
    ORDER BY id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
),
moved AS (
    DELETE FROM public.system_activities source
    USING batch
    WHERE source.id = batch.id
    RETURNING source.*
)
INSERT INTO pacs_archive.system_activities (
    id, endpoint, module, module_id, description, bug, line_code, browser,
    operating_system, ip, host_name, duration, created_by, created, status,
    action, public_id, created_at
)
SELECT
    id, endpoint, module, module_id, description, bug, line_code, browser,
    operating_system, ip, host_name, duration, created_by, created, status,
    action, public_id, created_at
FROM moved
ON CONFLICT DO NOTHING;

WITH batch AS (
    SELECT id
    FROM public.user_logs
    WHERE created < now() - (:'user_log_hot_months' || ' months')::interval
    ORDER BY id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
),
moved AS (
    DELETE FROM public.user_logs source
    USING batch
    WHERE source.id = batch.id
    RETURNING source.*
)
INSERT INTO pacs_archive.user_logs (
    id, user_id, type, http_user_agent, remote_addr, created, public_id, created_at
)
SELECT id, user_id, type, http_user_agent, remote_addr, created, public_id, created_at
FROM moved
ON CONFLICT DO NOTHING;

WITH batch AS (
    SELECT id
    FROM public.dicom_server_callback_log
    WHERE success = TRUE
      AND received_at < now() - (:'callback_success_days' || ' days')::interval
    ORDER BY id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
),
moved AS (
    DELETE FROM public.dicom_server_callback_log source
    USING batch
    WHERE source.id = batch.id
    RETURNING source.*
)
INSERT INTO pacs_archive.dicom_server_callback_log (
    id, event, payload, success, error_message, warning_message, received_at,
    created_at, accession_number, dicom_server_study_id,
    dicom_server_patient_id, dicom_server_series_ids, hospital_id,
    dicom_server_id, dedupe_key, payload_sha256, attempt_count, last_received_at
)
SELECT
    id, event, payload, success, error_message, warning_message, received_at,
    created_at, accession_number, dicom_server_study_id,
    dicom_server_patient_id, dicom_server_series_ids, hospital_id,
    dicom_server_id, dedupe_key, payload_sha256, attempt_count, last_received_at
FROM moved
ON CONFLICT DO NOTHING;

WITH batch AS (
    SELECT id
    FROM public.pacs_realtime_notification_events
    WHERE created_at < now() - (:'notification_days' || ' days')::interval
    ORDER BY id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
DELETE FROM public.pacs_realtime_notification_events event
USING batch
WHERE event.id = batch.id;
