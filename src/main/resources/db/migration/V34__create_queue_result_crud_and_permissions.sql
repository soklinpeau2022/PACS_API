CREATE TABLE IF NOT EXISTS pacs_queue_results (
    id               BIGSERIAL PRIMARY KEY,
    hospital_id      BIGINT NOT NULL REFERENCES hospitals(id),
    queue_id         BIGINT NOT NULL REFERENCES pacs_patient_queue(id),
    description      TEXT NOT NULL,
    image_paths_json TEXT,
    is_active        SMALLINT NOT NULL DEFAULT 1,
    created_by       BIGINT REFERENCES users(id),
    modified_by      BIGINT REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_queue_results_hospital_queue_active
    ON pacs_queue_results (hospital_id, queue_id)
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_pacs_queue_results_hospital_active_created
    ON pacs_queue_results (hospital_id, is_active, created_at DESC);

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, is_active)
VALUES
    ('POST', '/queue/queue-result-list', 'pacs.queue.translate', 1),
    ('POST', '/queue/queue-result-find/*', 'pacs.queue.translate', 1),
    ('POST', '/queue/queue-result-create', 'pacs.queue.translate', 1),
    ('POST', '/queue/queue-result-update', 'pacs.queue.translate', 1),
    ('POST', '/queue/queue-result-delete/*', 'pacs.queue.translate', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET is_active = 1;
