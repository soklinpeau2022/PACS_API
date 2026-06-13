-- Add clear UserGroup alias endpoints for frontend readability.
-- These are aliases of existing user-group endpoints and use the same permissions.

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/user-group/user-group-role-list', 'role.view', 'user.read', 1),
    ('POST', '/user-group/user-group-role-find/*', 'role.view', 'user.read', 1),
    ('POST', '/user-group/user-group-role-add', 'role.add', 'user.write', 1),
    ('POST', '/user-group/user-group-role-update', 'role.edit', 'user.write', 1),
    ('POST', '/user-group/user-group-role-delete/*', 'role.delete', 'user.write', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

