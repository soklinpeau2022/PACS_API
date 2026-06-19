-- V198: Quiet repeated child-index maintenance notices.
--
-- V197 made callback and realtime event dedupe indexes child-local so those
-- partitioned parents can keep native RANGE partitions. This follow-up keeps
-- the helper idempotent without emitting CREATE INDEX "already exists" notices
-- during every scheduled maintenance run.

CREATE OR REPLACE FUNCTION ensure_partition_child_indexes(
    p_parent_schema TEXT,
    p_parent_table TEXT,
    p_child_schema TEXT,
    p_child_table TEXT
)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    created_count INTEGER := 0;
    idx_name TEXT;
BEGIN
    IF p_parent_table = 'dicom_server_callback_log' THEN
        idx_name := 'ux_' || SUBSTRING(MD5(p_child_table || '_hospital_dedupe') FROM 1 FOR 24);
        IF TO_REGCLASS(FORMAT('%I.%I', p_child_schema, idx_name)) IS NULL THEN
            EXECUTE FORMAT(
                'CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I.%I (hospital_id, dedupe_key) WHERE dedupe_key IS NOT NULL',
                idx_name, p_child_schema, p_child_table
            );
            created_count := created_count + 1;
        END IF;
    ELSIF p_parent_table = 'pacs_realtime_notification_events' THEN
        idx_name := 'ux_' || SUBSTRING(MD5(p_child_table || '_hospital_dedupe') FROM 1 FOR 24);
        IF TO_REGCLASS(FORMAT('%I.%I', p_child_schema, idx_name)) IS NULL THEN
            EXECUTE FORMAT(
                'CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I.%I (hospital_id, dedupe_key)',
                idx_name, p_child_schema, p_child_table
            );
            created_count := created_count + 1;
        END IF;
    END IF;

    RETURN created_count;
END
$fn$;

COMMENT ON FUNCTION ensure_partition_child_indexes(TEXT, TEXT, TEXT, TEXT) IS
    'Ensures child-local dedupe indexes for partitioned callback/realtime event tables without noisy repeated CREATE INDEX notices.';
