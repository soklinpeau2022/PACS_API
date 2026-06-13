-- Module controller removed; keep ModuleType list/find endpoints only.

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/module-type-list', 'role.assign_permission', 'user.write', 1),
    ('POST', '/module-type/find/*', 'role.assign_permission', 'user.write', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

UPDATE endpoint_permissions
SET is_active = 2
WHERE http_method = 'POST'
  AND endpoint_pattern IN (
      '/module-list',
      '/module-detail-list',
      '/module-type/module-type-list',
      '/module-type/list'
  );
