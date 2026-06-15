ALTER TABLE study_retention_policies
    ADD COLUMN IF NOT EXISTS auto_delete BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE study_retention_policies
SET auto_delete = FALSE
WHERE auto_delete IS NULL;

CREATE INDEX IF NOT EXISTS idx_study_retention_policies_auto_delete
    ON study_retention_policies (hospital_id, enabled, auto_delete, is_active);

COMMENT ON COLUMN study_retention_policies.auto_delete IS
    'When true, expired matching studies can be deleted automatically in chunked retention cleanup without Super Admin approval.';

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/study-retention/bulk-delete', 'study.retention.approval.approve', 'pacs.api', 1),
    ('POST', '/study-retention/auto-delete-run', 'study.retention.approval.approve', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;
