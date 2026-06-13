-- Migrate endpoint permission from legacy typo path to the canonical path.
-- Legacy path:  /role/user-groupl-list
-- Canonical path: /role/user-group-list

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
SELECT
    ep.http_method,
    '/role/user-group-list',
    ep.permission_code,
    ep.required_scope,
    1
FROM endpoint_permissions ep
WHERE ep.http_method = 'POST'
  AND ep.endpoint_pattern = '/role/user-groupl-list'
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

-- Safety net in case the legacy row does not exist in target database.
INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES ('POST', '/role/user-group-list', 'role.view', 'user.read', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

-- Deactivate legacy typo path to avoid ambiguity in permission data.
UPDATE endpoint_permissions
SET is_active = 0
WHERE http_method = 'POST'
  AND endpoint_pattern = '/role/user-groupl-list';

