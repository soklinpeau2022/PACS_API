-- Align PACS role endpoints with EMR-style controller contract:
-- /role/list, /role/find/{id}, /role/add, /role/update, /role/delete/{id}

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/role/list', 'role.view', 'user.read', 1),
    ('POST', '/role/find/*', 'role.view', 'user.read', 1),
    ('POST', '/role/add', 'role.add', 'user.write', 1),
    ('POST', '/role/update', 'role.edit', 'user.write', 1),
    ('POST', '/role/delete/*', 'role.delete', 'user.write', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

-- Disable legacy role endpoint aliases after migrating to EMR-style paths.
UPDATE endpoint_permissions
SET is_active = 0
WHERE http_method = 'POST'
  AND endpoint_pattern IN (
      '/role/role-list',
      '/role/role-find/*',
      '/role/role-create',
      '/role/role-update',
      '/role/role-delete/*'
  );
