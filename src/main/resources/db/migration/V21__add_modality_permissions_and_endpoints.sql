INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'modality', 'modality', 2, 1, NOW()
FROM module_types mt
WHERE mt.code = 'HOSPITAL'
ON CONFLICT (code) DO NOTHING;

WITH desired_details(module_code, permission_code, permission_name, permission_type, action_key, display_order) AS (
    VALUES
        ('modality', 'modality.view', 'modality (View)', 'VIEW', 'VIEW', 1),
        ('modality', 'modality.add', 'modality (Add)', 'ADD', 'ADD', 2),
        ('modality', 'modality.edit', 'modality (Edit)', 'EDIT', 'EDIT', 3),
        ('modality', 'modality.delete', 'modality (Delete)', 'DELETE', 'DELETE', 4),
        ('hospital', 'hospital.modality.view', 'Hospital modality (View)', 'VIEW', 'VIEW', 4)
)
INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, d.permission_code, d.permission_name, d.permission_type, d.action_key, d.display_order, 1, NOW()
FROM desired_details d
JOIN modules m ON m.code = d.module_code
ON CONFLICT (code) DO NOTHING;

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, is_active)
VALUES
    ('POST', '/modality/modality-list', 'modality.view', 1),
    ('POST', '/modality/modality-find/*', 'modality.view', 1),
    ('POST', '/modality/modality-create', 'modality.add', 1),
    ('POST', '/modality/modality-update', 'modality.edit', 1),
    ('POST', '/modality/modality-delete/*', 'modality.delete', 1),
    ('POST', '/hospital-modality', 'hospital.modality.view', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET is_active = 1;

INSERT INTO role_module_details (role_id, module_detail_id, created)
SELECT r.id, md.id, NOW()
FROM roles r
JOIN module_details md ON md.is_active = 1
WHERE r.code = 'ADMIN'
  AND r.is_active = 1
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

UPDATE users u
SET permission_version = COALESCE(u.permission_version, 0) + 1,
    modified = NOW()
WHERE EXISTS (
    SELECT 1
    FROM user_groups ug
    JOIN roles r ON r.id = ug.role_id
    WHERE ug.user_id = u.id
      AND ug.is_active = 1
      AND r.code = 'ADMIN'
      AND r.is_active = 1
);
