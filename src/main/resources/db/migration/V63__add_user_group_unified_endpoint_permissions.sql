-- Ensure unified user-group endpoints are permission-mapped.
INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/user-group/list', 'role.view', 'user.read', 1),
    ('POST', '/user-group/add', 'role.add', 'user.write', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

