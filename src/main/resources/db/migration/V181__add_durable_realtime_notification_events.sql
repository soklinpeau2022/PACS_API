CREATE TABLE IF NOT EXISTS pacs_realtime_notification_events (
    id                   BIGSERIAL PRIMARY KEY,
    hospital_id          BIGINT NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    source               VARCHAR(40) NOT NULL,
    event_type           VARCHAR(80) NOT NULL,
    severity             VARCHAR(20) NOT NULL DEFAULT 'info',
    title                VARCHAR(255) NOT NULL,
    message              TEXT,
    worklist_id          BIGINT REFERENCES pacs_worklists(id) ON DELETE SET NULL,
    study_id             BIGINT REFERENCES pacs_studies(id) ON DELETE SET NULL,
    worklist_public_key  VARCHAR(100),
    study_public_key     VARCHAR(100),
    patient_name         VARCHAR(255),
    visit_code           VARCHAR(100),
    accession_number     VARCHAR(100),
    dedupe_key           VARCHAR(255) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (hospital_id, dedupe_key)
);

CREATE INDEX IF NOT EXISTS idx_pacs_realtime_events_hospital_cursor
    ON pacs_realtime_notification_events (hospital_id, id);

CREATE INDEX IF NOT EXISTS idx_pacs_realtime_events_created
    ON pacs_realtime_notification_events (created_at);

COMMENT ON TABLE pacs_realtime_notification_events IS
    'Durable hospital-scoped outbox used to replay callback and upload events over authenticated SSE.';

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES ('GET', '/notification/notification-stream', 'pacs.worklist.view', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;
