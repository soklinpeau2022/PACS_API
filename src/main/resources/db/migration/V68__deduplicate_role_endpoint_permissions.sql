-- Keep Role endpoint permissions on a single canonical set: /role/role-*
-- and deactivate legacy/obsolete patterns to avoid duplicated endpoint rules.

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/role/role-list', 'role.view', 'user.read', 1),
    ('POST', '/role/role-find/*', 'role.view', 'user.read', 1),
    ('POST', '/role/role-add', 'role.add', 'user.write', 1),
    ('POST', '/role/role-update', 'role.edit', 'user.write', 1),
    ('POST', '/role/role-delete/*', 'role.delete', 'user.write', 1),
    ('POST', '/role/role-menu', 'role.view', 'user.read', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

UPDATE endpoint_permissions
SET is_active = 2
WHERE http_method = 'POST'
  AND endpoint_pattern IN (
      '/role/list',
      '/role/find/*',
      '/role/add',
      '/role/create',
      '/role/update',
      '/role/delete/*',
      '/role/menu',
      '/role/role-create'
  );
