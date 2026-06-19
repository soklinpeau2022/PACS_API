-- V196: hospital-scope DICOM callback logs and trim final duplicate indexes.
--
-- MIGRATION-SAFETY: allowed-data-loss
-- MIGRATION-SAFETY: not-null-backfilled
-- MIGRATION-SAFETY: constraint-guard-reviewed
-- Rows with unknown hospital_id are copied to dicom_server_unmatched_callback_log
-- before being removed from the hospital-scoped callback log. This keeps the
-- operational log queryable while allowing the main table to be NOT NULL.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS dicom_server_unmatched_callback_log (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid(),
    original_callback_log_id BIGINT,
    dicom_server_id BIGINT,
    dedupe_key VARCHAR(64),
    payload_sha256 CHAR(64),
    event VARCHAR(120),
    accession_number VARCHAR(255),
    dicom_server_study_id VARCHAR(255),
    dicom_server_patient_id VARCHAR(255),
    dicom_server_series_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    warning_message TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_received_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_unmatched_callback_attempt_count CHECK (attempt_count > 0),
    CONSTRAINT chk_unmatched_callback_payload_sha256
        CHECK (payload_sha256 IS NULL OR payload_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_unmatched_callback_public_id
    ON dicom_server_unmatched_callback_log (public_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_unmatched_callback_original
    ON dicom_server_unmatched_callback_log (original_callback_log_id)
    WHERE original_callback_log_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_unmatched_callback_dedupe
    ON dicom_server_unmatched_callback_log (dedupe_key)
    WHERE dedupe_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_unmatched_callback_received
    ON dicom_server_unmatched_callback_log (received_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_unmatched_callback_accession
    ON dicom_server_unmatched_callback_log (accession_number)
    WHERE accession_number IS NOT NULL;

UPDATE dicom_server_callback_log
SET last_received_at = COALESCE(last_received_at, received_at)
WHERE hospital_id IS NULL
  AND last_received_at IS NULL;

INSERT INTO dicom_server_unmatched_callback_log (
    original_callback_log_id,
    dicom_server_id,
    dedupe_key,
    payload_sha256,
    event,
    accession_number,
    dicom_server_study_id,
    dicom_server_patient_id,
    dicom_server_series_ids,
    payload,
    success,
    error_message,
    warning_message,
    received_at,
    last_received_at,
    attempt_count,
    created_at
)
SELECT
    log.id,
    log.dicom_server_id,
    COALESCE(
        log.dedupe_key,
        encode(
            digest(
                concat_ws(
                    '|',
                    'unmatched',
                    COALESCE(log.dicom_server_id::text, ''),
                    COALESCE(log.payload_sha256::text, ''),
                    log.id::text
                ),
                'sha256'
            ),
            'hex'
        )
    ),
    log.payload_sha256,
    log.event,
    log.accession_number,
    log.dicom_server_study_id,
    log.dicom_server_patient_id,
    log.dicom_server_series_ids,
    log.payload,
    log.success,
    log.error_message,
    log.warning_message,
    log.received_at,
    COALESCE(log.last_received_at, log.received_at),
    log.attempt_count,
    log.created_at
FROM dicom_server_callback_log log
WHERE log.hospital_id IS NULL
ON CONFLICT (original_callback_log_id)
WHERE original_callback_log_id IS NOT NULL
DO UPDATE SET
    last_received_at = EXCLUDED.last_received_at,
    attempt_count = GREATEST(
        dicom_server_unmatched_callback_log.attempt_count,
        EXCLUDED.attempt_count
    ),
    success = dicom_server_unmatched_callback_log.success OR EXCLUDED.success,
    error_message = EXCLUDED.error_message,
    warning_message = EXCLUDED.warning_message;

DELETE FROM dicom_server_callback_log
WHERE hospital_id IS NULL;

ALTER TABLE dicom_server_callback_log
    ALTER COLUMN hospital_id SET NOT NULL;

DROP INDEX IF EXISTS ux_callback_log_hospital_dedupe;
CREATE UNIQUE INDEX IF NOT EXISTS ux_callback_log_hospital_dedupe
    ON dicom_server_callback_log (hospital_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

DROP INDEX IF EXISTS idx_callback_log_hospital_received;
CREATE INDEX IF NOT EXISTS idx_callback_log_hospital_received
    ON dicom_server_callback_log (hospital_id, received_at DESC, id DESC);

DROP INDEX IF EXISTS idx_callback_log_hospital_server_received;
CREATE INDEX IF NOT EXISTS idx_callback_log_hospital_server_received
    ON dicom_server_callback_log (hospital_id, dicom_server_id, received_at DESC, id DESC)
    WHERE dicom_server_id IS NOT NULL;

DROP INDEX IF EXISTS idx_callback_log_hospital_accession;
CREATE INDEX IF NOT EXISTS idx_callback_log_hospital_accession
    ON dicom_server_callback_log (hospital_id, accession_number)
    WHERE accession_number IS NOT NULL;

-- Keep the (id, hospital_id) index object used by existing FKs, but expose it
-- under the canonical final name and remove the duplicate physical index.
DO $$
DECLARE
    old_idx REGCLASS := TO_REGCLASS('public.ux_worklists_id_hospital');
    new_idx REGCLASS := TO_REGCLASS('public.ux_pacs_worklists_id_hospital');
    old_has_fk BOOLEAN := FALSE;
    new_has_fk BOOLEAN := FALSE;
BEGIN
    IF old_idx IS NOT NULL THEN
        SELECT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conindid = old_idx
        ) INTO old_has_fk;
    END IF;

    IF new_idx IS NOT NULL THEN
        SELECT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conindid = new_idx
        ) INTO new_has_fk;
    END IF;

    IF old_idx IS NOT NULL AND new_idx IS NOT NULL THEN
        IF old_has_fk AND NOT new_has_fk THEN
            DROP INDEX public.ux_pacs_worklists_id_hospital;
            ALTER INDEX public.ux_worklists_id_hospital
                RENAME TO ux_pacs_worklists_id_hospital;
        ELSIF new_has_fk AND NOT old_has_fk THEN
            DROP INDEX public.ux_worklists_id_hospital;
        ELSIF NOT old_has_fk AND NOT new_has_fk THEN
            DROP INDEX public.ux_worklists_id_hospital;
        ELSE
            RAISE NOTICE
                'Both worklist hospital unique indexes are FK-backed; leaving both for manual review.';
        END IF;
    ELSIF old_idx IS NOT NULL AND new_idx IS NULL THEN
        ALTER INDEX public.ux_worklists_id_hospital
            RENAME TO ux_pacs_worklists_id_hospital;
    END IF;
END;
$$;

-- Final hot-table trims. Kept indexes match the list/query paths that still
-- exist in MyBatis; these drops remove duplicates and low-value overlap.
DROP INDEX IF EXISTS idx_pacs_worklists_dicom_route;
DROP INDEX IF EXISTS idx_pacs_worklists_hospital_study_id;
DROP INDEX IF EXISTS idx_pacs_worklists_route_hospital_modality;

DROP INDEX IF EXISTS idx_pacs_studies_hospital_notification_received;
DROP INDEX IF EXISTS idx_pacs_studies_hospital_uploaded_source;

DROP INDEX IF EXISTS idx_system_activities_action_trgm;
DROP INDEX IF EXISTS idx_system_activities_module_trgm;
DROP INDEX IF EXISTS idx_system_activities_lower_action_module_created_id;
DROP INDEX IF EXISTS idx_system_activities_lower_endpoint_created_id;

DROP INDEX IF EXISTS idx_realtime_events_study_hospital;
DROP INDEX IF EXISTS idx_realtime_events_worklist_hospital;

INSERT INTO partition_maintenance_configs
    (parent_schema, parent_table, partition_column, retention_months, future_months, is_active)
VALUES
    ('public', 'user_logs', 'created', 12, 3, 1),
    ('public', 'system_activities', 'created', 12, 3, 1),
    ('public', 'dicom_server_callback_log', 'received_at', 12, 3, 1),
    ('public', 'pacs_realtime_notification_events', 'created_at', 12, 3, 1),
    ('public', 'pacs_worklist_histories', 'created', 12, 3, 1),
    ('public', 'study_retention_delete_requests', 'created_at', 12, 3, 1),
    ('public', 'pacs_worklists', 'created_at', 12, 3, 1),
    ('public', 'pacs_studies', 'received_at', 12, 3, 1)
ON CONFLICT (parent_schema, parent_table)
DO UPDATE SET
    partition_column = EXCLUDED.partition_column,
    retention_months = EXCLUDED.retention_months,
    future_months = EXCLUDED.future_months,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

COMMENT ON TABLE dicom_server_unmatched_callback_log IS
    'Quarantine table for DICOM server callbacks that arrive before the API can resolve a hospital scope.';
COMMENT ON COLUMN dicom_server_callback_log.hospital_id IS
    'Hospital scope is mandatory; unknown callbacks are stored in dicom_server_unmatched_callback_log.';
