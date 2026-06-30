-- Add an explicit Access permission module for the header Settings page.
-- Branding mutations stay super-admin-only in the controller, but the endpoint
-- permission now maps to a settings permission instead of reusing role/file
-- permissions.

WITH desired_module_type(code, name, display_order, group_code, group_name, group_order) AS (
    VALUES
        ('APPLICATION_SETTINGS', 'Application Settings', 52, 'ACCESS', 'Access', 5)
)
INSERT INTO module_types (code, name, display_order, menu_group_code, menu_group_name, menu_group_order, is_active, created)
SELECT code, name, display_order, group_code, group_name, group_order, 1, NOW()
FROM desired_module_type
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    menu_group_code = EXCLUDED.menu_group_code,
    menu_group_name = EXCLUDED.menu_group_name,
    menu_group_order = EXCLUDED.menu_group_order,
    is_active = 1,
    modified = NOW(),
    modified_at = NOW();

WITH desired_module(module_type_code, code, name, display_order) AS (
    VALUES
        ('APPLICATION_SETTINGS', 'application-settings', 'Application Settings', 1)
)
INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, dm.code, dm.name, dm.display_order, 1, NOW()
FROM desired_module dm
JOIN module_types mt ON mt.code = dm.module_type_code
ON CONFLICT (code) DO UPDATE
SET module_type_id = EXCLUDED.module_type_id,
    name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW(),
    modified_at = NOW();

WITH desired_details(module_code, permission_code, permission_name, permission_type, action_key, display_order) AS (
    VALUES
        ('application-settings', 'application.settings.view', 'Application Settings (View)', 'VIEW', 'VIEW', 1),
        ('application-settings', 'application.settings.edit', 'Application Settings (Edit)', 'EDIT', 'EDIT', 2)
)
INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, dd.permission_code, dd.permission_name, dd.permission_type, dd.action_key, dd.display_order, 1, NOW()
FROM desired_details dd
JOIN modules m ON m.code = dd.module_code
ON CONFLICT (code) DO UPDATE
SET module_id = EXCLUDED.module_id,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    action_key = EXCLUDED.action_key,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW(),
    modified_at = NOW();

UPDATE endpoint_permissions
SET is_active = 2
WHERE endpoint_pattern IN (
        '/application-settings/application-brand-settings-update',
        '/application-settings/application-brand-logo-upload',
        '/application-settings/application-login-background-upload'
    )
  AND permission_code IN ('role.edit', 'file.add');

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/application-settings/application-brand-settings-update', 'application.settings.edit', 'pacs.api', 1),
    ('POST', '/application-settings/application-brand-logo-upload', 'application.settings.edit', 'pacs.api', 1),
    ('POST', '/application-settings/application-login-background-upload', 'application.settings.edit', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

WITH new_permissions AS (
    SELECT id
    FROM module_details
    WHERE code IN ('application.settings.view', 'application.settings.edit')
),
existing_permission_count AS (
    SELECT COUNT(*) AS total
    FROM module_details
    WHERE is_active = 1
      AND code NOT IN ('application.settings.view', 'application.settings.edit')
),
full_permission_roles AS (
    SELECT r.id
    FROM roles r
    JOIN role_module_details rmd ON rmd.role_id = r.id
    JOIN module_details md ON md.id = rmd.module_detail_id
    CROSS JOIN existing_permission_count epc
    WHERE r.is_active = 1
      AND md.is_active = 1
      AND md.code NOT IN ('application.settings.view', 'application.settings.edit')
    GROUP BY r.id, epc.total
    HAVING COUNT(DISTINCT md.id) >= epc.total
),
admin_named_roles AS (
    SELECT r.id
    FROM roles r
    WHERE r.is_active = 1
      AND (
          UPPER(COALESCE(r.code, '')) IN ('ADMIN', 'SYSTEM_ADMIN', 'SUPER_ADMIN', 'SUPERADMIN', 'ADMIN_GROUP', 'SUPERADMIN_GROUP')
          OR UPPER(REGEXP_REPLACE(TRIM(COALESCE(r.name, '')), '[^A-Z0-9]+', '_', 'g')) IN (
              'ADMIN',
              'SYSTEM_ADMIN',
              'SUPER_ADMIN',
              'SUPERADMIN',
              'ADMIN_GROUP',
              'SUPERADMIN_GROUP'
          )
      )
),
target_roles AS (
    SELECT id FROM full_permission_roles
    UNION
    SELECT id FROM admin_named_roles
)
INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT tr.id, np.id, 1, NOW()
FROM target_roles tr
CROSS JOIN new_permissions np
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW(),
    modified_at = NOW()
WHERE is_active = 1;
