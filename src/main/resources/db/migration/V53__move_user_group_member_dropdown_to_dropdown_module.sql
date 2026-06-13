-- Move user-group member dropdown endpoint permission to dropdown module endpoint.
UPDATE endpoint_permissions
SET is_active = 2
WHERE http_method = 'POST'
  AND endpoint_pattern = '/user-group/member-dropdown'
  AND permission_code = 'user.view';

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES ('POST', '/dropdown/dropdown-user-group-member', 'user.view', 'user.read', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = EXCLUDED.is_active;
