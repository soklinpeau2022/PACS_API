-- Ensure core modules exist for each module type used by the admin UI tree.
INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'user', 'User', 1, 1, NOW()
FROM module_types mt
WHERE mt.code = 'USER'
ON CONFLICT (code) DO NOTHING;

INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'role', 'Role', 1, 1, NOW()
FROM module_types mt
WHERE mt.code = 'ROLE'
ON CONFLICT (code) DO NOTHING;

INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'hospital', 'Hospital', 1, 1, NOW()
FROM module_types mt
WHERE mt.code = 'HOSPITAL'
ON CONFLICT (code) DO NOTHING;

INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'pacs-patient', 'PACS Patient', 1, 1, NOW()
FROM module_types mt
WHERE mt.code = 'PACS_PATIENT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'pacs-queue', 'PACS Queue', 1, 1, NOW()
FROM module_types mt
WHERE mt.code = 'PACS_QUEUE'
ON CONFLICT (code) DO NOTHING;

INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'pacs-study', 'PACS Study', 1, 1, NOW()
FROM module_types mt
WHERE mt.code = 'PACS_STUDY'
ON CONFLICT (code) DO NOTHING;

INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'pacs-viewer', 'PACS Viewer', 1, 1, NOW()
FROM module_types mt
WHERE mt.code = 'PACS_VIEWER'
ON CONFLICT (code) DO NOTHING;

WITH desired_details(module_code, permission_code, permission_name, permission_type, action_key, display_order) AS (
    VALUES
        ('user', 'user.view', 'User (View)', 'VIEW', 'VIEW', 1),
        ('user', 'user.add', 'User (Add)', 'ADD', 'ADD', 2),
        ('user', 'user.edit', 'User (Edit)', 'EDIT', 'EDIT', 3),
        ('user', 'user.delete', 'User (Delete)', 'DELETE', 'DELETE', 4),

        ('role', 'role.view', 'Role (View)', 'VIEW', 'VIEW', 1),
        ('role', 'role.add', 'Role (Add)', 'ADD', 'ADD', 2),
        ('role', 'role.edit', 'Role (Edit)', 'EDIT', 'EDIT', 3),
        ('role', 'role.delete', 'Role (Delete)', 'DELETE', 'DELETE', 4),
        ('role', 'role.assign_permission', 'Role (Assign Permission)', 'ASSIGN', 'ASSIGN_PERMISSION', 5),

        ('hospital', 'hospital.view', 'Hospital (View)', 'VIEW', 'VIEW', 1),
        ('hospital', 'hospital.add', 'Hospital (Add)', 'ADD', 'ADD', 2),
        ('hospital', 'hospital.edit', 'Hospital (Edit)', 'EDIT', 'EDIT', 3),

        ('pacs-patient', 'pacs.patient.view', 'PACS Patient (View)', 'VIEW', 'VIEW', 1),
        ('pacs-patient', 'pacs.patient.create', 'PACS Patient (Create)', 'ADD', 'CREATE', 2),
        ('pacs-patient', 'pacs.patient.edit', 'PACS Patient (Edit)', 'EDIT', 'EDIT', 3),

        ('pacs-queue', 'pacs.queue.view', 'PACS Queue (View)', 'VIEW', 'VIEW', 1),
        ('pacs-queue', 'pacs.queue.return', 'PACS Queue (Return)', 'ACTION', 'RETURN', 2),
        ('pacs-queue', 'pacs.queue.cancel', 'PACS Queue (Cancel)', 'ACTION', 'CANCEL', 3),
        ('pacs-queue', 'pacs.queue.complete', 'PACS Queue (Complete)', 'ACTION', 'COMPLETE', 4),

        ('pacs-study', 'pacs.study.view', 'PACS Study (View)', 'VIEW', 'VIEW', 1),
        ('pacs-study', 'pacs.study.assign', 'PACS Study (Assign)', 'ACTION', 'ASSIGN', 2),

        ('pacs-viewer', 'pacs.viewer.open', 'PACS Viewer (Open)', 'VIEW', 'OPEN', 1)
)
INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, d.permission_code, d.permission_name, d.permission_type, d.action_key, d.display_order, 1, NOW()
FROM desired_details d
JOIN modules m ON m.code = d.module_code
ON CONFLICT (code) DO NOTHING;

-- Keep endpoint permissions connected for newly added viewer integration endpoints.
INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/viewer/viewer-validate', 'pacs.viewer.open', 'pacs.viewer.open', 1),
    ('POST', '/viewer/viewer-close', 'pacs.viewer.open', 'pacs.viewer.open', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

-- Ensure ADMIN role is connected to all active module permissions.
INSERT INTO role_module_details (role_id, module_detail_id, created)
SELECT r.id, md.id, NOW()
FROM roles r
JOIN module_details md ON md.is_active = 1
WHERE r.code = 'ADMIN'
  AND r.is_active = 1
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

-- Force permission cache refresh for users currently assigned to ADMIN.
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

