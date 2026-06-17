-- Repair Study Retention policy edit access on databases that were deployed
-- before the policy-find endpoint permission existed.

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/study-retention/policy-find/*', 'study.retention.policy.view', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT r.id, md.id, 1, NOW()
FROM roles r
JOIN module_details md ON md.code = 'study.retention.policy.view'
WHERE r.is_active = 1
  AND (
      UPPER(COALESCE(r.code, '')) IN ('ADMIN', 'SUPER_ADMIN', 'SYSTEM_ADMIN')
      OR LOWER(TRIM(COALESCE(r.name, ''))) IN ('admin', 'super admin', 'system admin')
  )
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;
