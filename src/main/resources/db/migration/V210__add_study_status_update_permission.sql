INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, 'pacs.study.status_update', 'Study (Status Update)', 'ACTION', 'STATUS_UPDATE', 2, 1, NOW()
FROM modules m
WHERE m.code = 'pacs-study'
ON CONFLICT (code) DO UPDATE
SET module_id = EXCLUDED.module_id,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    action_key = EXCLUDED.action_key,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW();

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES ('POST', '/study/study-status-update/*', 'pacs.study.status_update', 'pacs.study.read', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT r.id, md.id, 1, NOW()
FROM roles r
JOIN module_details md ON md.code = 'pacs.study.status_update'
WHERE r.is_active = 1
  AND (
      UPPER(COALESCE(r.code, '')) IN ('ADMIN', 'SUPER_ADMIN', 'SYSTEM_ADMIN')
      OR UPPER(REGEXP_REPLACE(TRIM(COALESCE(r.name, '')), '[^A-Za-z0-9]+', '_', 'g')) IN ('ADMIN', 'SUPER_ADMIN', 'SYSTEM_ADMIN')
  )
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;
