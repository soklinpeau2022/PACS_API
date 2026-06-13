-- Add EMR-style endpoint aliases so PACS supports both route conventions.
-- This keeps permission checks consistent across old and new endpoint paths.

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/user/list', 'user.view', 'user.read', 1),
    ('POST', '/user/find/*', 'user.view', 'user.read', 1),
    ('POST', '/user/add', 'user.add', 'user.write', 1),
    ('POST', '/user/update', 'user.edit', 'user.write', 1),
    ('POST', '/user/delete/*', 'user.delete', 'user.write', 1),

    ('POST', '/role/list', 'role.view', 'user.read', 1),
    ('POST', '/role/find/*', 'role.view', 'user.read', 1),
    ('POST', '/role/add', 'role.add', 'user.write', 1),
    ('POST', '/role/update', 'role.edit', 'user.write', 1),
    ('POST', '/role/delete/*', 'role.delete', 'user.write', 1),
    ('POST', '/role/menu', 'role.view', 'user.read', 1),

    ('POST', '/module-type/list', 'role.assign_permission', 'user.write', 1),
    ('POST', '/module-type/find/*', 'role.assign_permission', 'user.write', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;
