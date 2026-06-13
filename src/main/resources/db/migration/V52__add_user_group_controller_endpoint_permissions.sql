-- User Group Controller endpoint permissions
INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/user-group/system/list', 'role.view', 'user.read', 1),
    ('POST', '/user-group/hospital/list', 'role.view', 'user.read', 1),
    ('POST', '/user-group/find/*', 'role.view', 'user.read', 1),
    ('POST', '/user-group/create', 'role.add', 'user.write', 1),
    ('POST', '/user-group/update', 'role.edit', 'user.write', 1),
    ('POST', '/user-group/assign-users', 'role.edit', 'user.write', 1),
    ('POST', '/user-group/delete/*', 'role.delete', 'user.write', 1),
    ('POST', '/user-group/member-dropdown', 'user.view', 'user.read', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = EXCLUDED.is_active;
