-- QA/PROD deny unknown endpoints. Register the dashboard overview endpoint so
-- the first screen loads after login without tripping the permission filter.

INSERT INTO module_types (code, name, display_order, menu_group_code, menu_group_name, menu_group_order, is_active, created)
VALUES ('HOME', 'Home', 1, 'DASHBOARD', 'Dashboard', 1, 1, NOW())
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    menu_group_code = EXCLUDED.menu_group_code,
    menu_group_name = EXCLUDED.menu_group_name,
    menu_group_order = EXCLUDED.menu_group_order,
    is_active = 1,
    modified = NOW();

INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'home', 'Home', 1, 1, NOW()
FROM module_types mt
WHERE mt.code = 'HOME'
ON CONFLICT (code) DO UPDATE
SET module_type_id = EXCLUDED.module_type_id,
    name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW();

INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, 'home.view', 'Home (View)', 'VIEW', 'VIEW', 1, 1, NOW()
FROM modules m
WHERE m.code = 'home'
ON CONFLICT (code) DO UPDATE
SET module_id = EXCLUDED.module_id,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    action_key = EXCLUDED.action_key,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW();

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES ('POST', '/dashboard/dashboard-overview', 'home.view', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT r.id, md.id, 1, NOW()
FROM roles r
JOIN module_details md ON md.code = 'home.view'
WHERE r.is_active = 1
  AND (
      UPPER(COALESCE(r.code, '')) = 'ADMIN'
      OR LOWER(TRIM(COALESCE(r.name, ''))) = 'admin'
  )
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;
