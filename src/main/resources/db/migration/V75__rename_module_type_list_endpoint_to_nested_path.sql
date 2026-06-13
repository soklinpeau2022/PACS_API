-- Rename ModuleType list endpoint back to nested route and keep only one active endpoint.

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES ('POST', '/module-type/module-type-list', 'role.assign_permission', 'user.write', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

UPDATE endpoint_permissions
SET is_active = 2
WHERE http_method = 'POST'
  AND permission_code = 'role.assign_permission'
  AND endpoint_pattern IN (
      '/module-type-list',
      '/module-type/list'
  );
