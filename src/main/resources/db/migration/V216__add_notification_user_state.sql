CREATE TABLE IF NOT EXISTS pacs_notification_user_states (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    notification_id VARCHAR(120) NOT NULL,
    read_at TIMESTAMPTZ,
    cleared_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, hospital_id, notification_id)
);

CREATE INDEX IF NOT EXISTS idx_pacs_notification_user_states_scope
    ON pacs_notification_user_states (user_id, hospital_id, cleared_at, notification_id);

CREATE INDEX IF NOT EXISTS idx_pacs_notification_user_states_updated
    ON pacs_notification_user_states (updated_at);

COMMENT ON TABLE pacs_notification_user_states IS
    'Per-user hospital-scoped read and clear state for virtual PACS notifications.';

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/notification/notification-read', 'pacs.worklist.view', 'pacs.api', 1),
    ('POST', '/notification/notification-clear', 'pacs.worklist.view', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;
