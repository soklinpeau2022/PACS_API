-- Rename Module Type list endpoint pattern to the new specific path.
-- Old: POST /module-type/list
-- New: POST /module-type/module-type-list

UPDATE endpoint_permissions
SET endpoint_pattern = '/module-type/module-type-list',
    is_active = 1
WHERE http_method = 'POST'
  AND endpoint_pattern = '/module-type/list'
  AND permission_code = 'role.assign_permission';

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
SELECT 'POST', '/module-type/module-type-list', 'role.assign_permission', 'user.write', 1
WHERE NOT EXISTS (
    SELECT 1
    FROM endpoint_permissions
    WHERE http_method = 'POST'
      AND endpoint_pattern = '/module-type/module-type-list'
      AND permission_code = 'role.assign_permission'
);

UPDATE endpoint_permissions
SET is_active = 2
WHERE http_method = 'POST'
  AND endpoint_pattern = '/module-type/list'
  AND permission_code = 'role.assign_permission';
