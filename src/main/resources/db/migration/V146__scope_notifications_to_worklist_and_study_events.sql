INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES ('POST', '/notification/notification-list', 'pacs.worklist.view', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

UPDATE endpoint_permissions
SET is_active = 2
WHERE http_method = 'POST'
  AND endpoint_pattern = '/notification/notification-list'
  AND permission_code = 'system.activity.view';

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_hospital_notification_created
    ON pacs_worklists (hospital_id, created_at DESC, created DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_notification_received
    ON pacs_studies (hospital_id, received_at DESC, created DESC, id DESC)
    WHERE is_active = 1;
