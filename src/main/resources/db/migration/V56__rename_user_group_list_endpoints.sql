-- Rename User Group list endpoint patterns to specific endpoint paths.
-- Old: POST /user-group/system/list
-- New: POST /user-group/system-user-group-list
-- Old: POST /user-group/hospital/list
-- New: POST /user-group/hospital-user-group-list

UPDATE endpoint_permissions
SET endpoint_pattern = '/user-group/system-user-group-list',
    is_active = 1
WHERE http_method = 'POST'
  AND endpoint_pattern = '/user-group/system/list'
  AND permission_code = 'role.view';

UPDATE endpoint_permissions
SET endpoint_pattern = '/user-group/hospital-user-group-list',
    is_active = 1
WHERE http_method = 'POST'
  AND endpoint_pattern = '/user-group/hospital/list'
  AND permission_code = 'role.view';

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
SELECT 'POST', '/user-group/system-user-group-list', 'role.view', 'user.read', 1
WHERE NOT EXISTS (
    SELECT 1
    FROM endpoint_permissions
    WHERE http_method = 'POST'
      AND endpoint_pattern = '/user-group/system-user-group-list'
      AND permission_code = 'role.view'
);

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
SELECT 'POST', '/user-group/hospital-user-group-list', 'role.view', 'user.read', 1
WHERE NOT EXISTS (
    SELECT 1
    FROM endpoint_permissions
    WHERE http_method = 'POST'
      AND endpoint_pattern = '/user-group/hospital-user-group-list'
      AND permission_code = 'role.view'
);

UPDATE endpoint_permissions
SET is_active = 2
WHERE http_method = 'POST'
  AND endpoint_pattern IN ('/user-group/system/list', '/user-group/hospital/list')
  AND permission_code = 'role.view';
