-- Split the role-permission catalog so each permission module owns only its
-- own actions. Menu grouping still comes from module_types in the database.

WITH desired_module_types(code, name, display_order, group_code, group_name, group_order) AS (
    VALUES
        ('HOME', 'Home', 1, 'DASHBOARD', 'Dashboard', 1),

        ('PACS_PATIENT', 'Patient', 10, 'PATIENT', 'Patient', 2),
        ('PACS_QUEUE', 'Queue', 11, 'PATIENT', 'Patient', 2),
        ('PACS_QUEUE_RESULT', 'Queue Result', 12, 'PATIENT', 'Patient', 2),
        ('PACS_SERVICE', 'Service', 13, 'PATIENT', 'Patient', 2),
        ('FILE_UPLOAD', 'File Upload', 14, 'PATIENT', 'Patient', 2),
        ('PACS_STUDY', 'Study', 15, 'PATIENT', 'Patient', 2),

        ('SYSTEM_ACTIVITY', 'System Activity', 20, 'REPORT', 'Report', 3),
        ('USER_LOG', 'User Log', 21, 'REPORT', 'Report', 3),

        ('USER', 'User', 30, 'SETTING', 'Setting', 4),
        ('ROLE', 'Role', 31, 'SETTING', 'Setting', 4),
        ('HOSPITAL', 'Hospital', 32, 'SETTING', 'Setting', 4),
        ('HOSPITAL_MODALITY', 'Hospital Modality', 33, 'SETTING', 'Setting', 4),
        ('MODALITY', 'Modality', 34, 'SETTING', 'Setting', 4),

        ('PACS_VIEWER', 'Viewer', 40, 'DICOMCONFIG', 'DICOM Config', 5),
        ('DICOM_SERVER', 'DICOM Server', 41, 'DICOMCONFIG', 'DICOM Config', 5),
        ('DICOM_ROUTING', 'DICOM Routing', 42, 'DICOMCONFIG', 'DICOM Config', 5)
)
INSERT INTO module_types (code, name, display_order, menu_group_code, menu_group_name, menu_group_order, is_active, created)
SELECT code, name, display_order, group_code, group_name, group_order, 1, NOW()
FROM desired_module_types
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    menu_group_code = EXCLUDED.menu_group_code,
    menu_group_name = EXCLUDED.menu_group_name,
    menu_group_order = EXCLUDED.menu_group_order,
    is_active = 1,
    modified = NOW();

-- PACS_REPORT was a menu bucket, not a permission module. The REPORT menu group
-- now comes from SYSTEM_ACTIVITY and USER_LOG.
UPDATE module_types
SET is_active = 2,
    modified = NOW()
WHERE code = 'PACS_REPORT';

WITH desired_modules(module_type_code, code, name, display_order) AS (
    VALUES
        ('HOME', 'home', 'Home', 1),
        ('PACS_PATIENT', 'pacs-patient', 'Patient', 1),
        ('PACS_QUEUE', 'pacs-queue', 'Queue', 1),
        ('PACS_QUEUE_RESULT', 'pacs-queue-result', 'Queue Result', 1),
        ('PACS_SERVICE', 'pacs-service', 'Service', 1),
        ('FILE_UPLOAD', 'file-upload', 'File Upload', 1),
        ('PACS_STUDY', 'pacs-study', 'Study', 1),
        ('SYSTEM_ACTIVITY', 'system-activity', 'System Activity', 1),
        ('USER_LOG', 'user-log', 'User Log', 1),
        ('USER', 'user', 'User', 1),
        ('ROLE', 'role', 'Role', 1),
        ('HOSPITAL', 'hospital', 'Hospital', 1),
        ('HOSPITAL_MODALITY', 'hospital-modality', 'Hospital Modality', 1),
        ('MODALITY', 'modality', 'Modality', 1),
        ('PACS_VIEWER', 'pacs-viewer', 'Viewer', 1),
        ('DICOM_SERVER', 'dicom-server', 'DICOM Server', 1),
        ('DICOM_ROUTING', 'dicom-routing', 'DICOM Routing', 1)
)
INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, dm.code, dm.name, dm.display_order, 1, NOW()
FROM desired_modules dm
JOIN module_types mt ON mt.code = dm.module_type_code
ON CONFLICT (code) DO UPDATE
SET module_type_id = EXCLUDED.module_type_id,
    name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW();

WITH desired_details(module_code, permission_code, permission_name, permission_type, action_key, display_order) AS (
    VALUES
        ('home', 'home.view', 'Home (View)', 'VIEW', 'VIEW', 1),

        ('pacs-patient', 'pacs.patient.view', 'Patient (View)', 'VIEW', 'VIEW', 1),
        ('pacs-patient', 'pacs.patient.create', 'Patient (Create)', 'ADD', 'CREATE', 2),
        ('pacs-patient', 'pacs.patient.edit', 'Patient (Edit)', 'EDIT', 'EDIT', 3),

        ('pacs-queue', 'pacs.queue.view', 'Queue (View)', 'VIEW', 'VIEW', 1),
        ('pacs-queue', 'pacs.queue.assign', 'Queue (Assign)', 'ACTION', 'ASSIGN', 2),
        ('pacs-queue', 'pacs.queue.send', 'Queue (Send To DICOM Server)', 'ACTION', 'SEND_TO_PACS', 3),
        ('pacs-queue', 'pacs.queue.receive', 'Queue (Receive Study)', 'ACTION', 'RECEIVE_STUDY', 4),
        ('pacs-queue', 'pacs.queue.view_study', 'Queue (View Study)', 'VIEW', 'VIEW_STUDY', 5),
        ('pacs-queue', 'pacs.queue.translate', 'Queue (Translate)', 'ACTION', 'TRANSLATE', 6),
        ('pacs-queue', 'pacs.queue.return', 'Queue (Return)', 'ACTION', 'RETURN', 7),
        ('pacs-queue', 'pacs.queue.cancel', 'Queue (Cancel)', 'ACTION', 'CANCEL', 8),
        ('pacs-queue', 'pacs.queue.complete', 'Queue (Complete)', 'ACTION', 'COMPLETE', 9),

        ('pacs-queue-result', 'pacs.queue.result.view', 'Queue Result (View)', 'VIEW', 'VIEW', 1),
        ('pacs-queue-result', 'pacs.queue.result.create', 'Queue Result (Create)', 'ADD', 'CREATE', 2),
        ('pacs-queue-result', 'pacs.queue.result.edit', 'Queue Result (Edit)', 'EDIT', 'EDIT', 3),
        ('pacs-queue-result', 'pacs.queue.result.delete', 'Queue Result (Delete)', 'DELETE', 'DELETE', 4),

        ('pacs-service', 'service.view', 'Service (View)', 'VIEW', 'VIEW', 1),
        ('pacs-service', 'service.add', 'Service (Add)', 'ADD', 'ADD', 2),
        ('pacs-service', 'service.edit', 'Service (Edit)', 'EDIT', 'EDIT', 3),
        ('pacs-service', 'service.delete', 'Service (Delete)', 'DELETE', 'DELETE', 4),

        ('file-upload', 'file.view', 'File Upload (View)', 'VIEW', 'VIEW', 1),
        ('file-upload', 'file.add', 'File Upload (Add)', 'ADD', 'ADD', 2),
        ('file-upload', 'file.delete', 'File Upload (Delete)', 'DELETE', 'DELETE', 3),

        ('pacs-study', 'pacs.study.view', 'Study (View)', 'VIEW', 'VIEW', 1),

        ('system-activity', 'system.activity.view', 'System Activity (View)', 'VIEW', 'VIEW', 1),
        ('user-log', 'report.user_log.view', 'User Log (View)', 'VIEW', 'VIEW', 1),

        ('user', 'user.view', 'User (View)', 'VIEW', 'VIEW', 1),
        ('user', 'user.add', 'User (Add)', 'ADD', 'ADD', 2),
        ('user', 'user.edit', 'User (Edit)', 'EDIT', 'EDIT', 3),
        ('user', 'user.delete', 'User (Delete)', 'DELETE', 'DELETE', 4),

        ('role', 'role.view', 'Role (View)', 'VIEW', 'VIEW', 1),
        ('role', 'role.add', 'Role (Add)', 'ADD', 'ADD', 2),
        ('role', 'role.edit', 'Role (Edit)', 'EDIT', 'EDIT', 3),
        ('role', 'role.delete', 'Role (Delete)', 'DELETE', 'DELETE', 4),
        ('role', 'role.assign_permission', 'Role Permission (Assign)', 'ASSIGN', 'ASSIGN_PERMISSION', 5),

        ('hospital', 'hospital.view', 'Hospital (View)', 'VIEW', 'VIEW', 1),
        ('hospital', 'hospital.add', 'Hospital (Add)', 'ADD', 'ADD', 2),
        ('hospital', 'hospital.edit', 'Hospital (Edit)', 'EDIT', 'EDIT', 3),

        ('hospital-modality', 'hospital.modality.view', 'Hospital Modality (View)', 'VIEW', 'VIEW', 1),

        ('modality', 'modality.view', 'Modality (View)', 'VIEW', 'VIEW', 1),
        ('modality', 'modality.add', 'Modality (Add)', 'ADD', 'ADD', 2),
        ('modality', 'modality.edit', 'Modality (Edit)', 'EDIT', 'EDIT', 3),
        ('modality', 'modality.delete', 'Modality (Delete)', 'DELETE', 'DELETE', 4),

        ('pacs-viewer', 'pacs.viewer.open', 'Viewer (Open)', 'VIEW', 'OPEN', 1),

        ('dicom-server', 'dicom.server.view', 'DICOM Server (View)', 'VIEW', 'VIEW', 1),
        ('dicom-server', 'dicom.server.add', 'DICOM Server (Add)', 'ADD', 'ADD', 2),
        ('dicom-server', 'dicom.server.edit', 'DICOM Server (Edit)', 'EDIT', 'EDIT', 3),
        ('dicom-server', 'dicom.server.delete', 'DICOM Server (Delete)', 'DELETE', 'DELETE', 4),

        ('dicom-routing', 'dicom.routing.view', 'DICOM Routing (View)', 'VIEW', 'VIEW', 1),
        ('dicom-routing', 'dicom.routing.add', 'DICOM Routing (Add)', 'ADD', 'ADD', 2),
        ('dicom-routing', 'dicom.routing.edit', 'DICOM Routing (Edit)', 'EDIT', 'EDIT', 3),
        ('dicom-routing', 'dicom.routing.delete', 'DICOM Routing (Delete)', 'DELETE', 'DELETE', 4)
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
    modified = NOW();

-- Preserve existing role grants when repairing legacy Modulight permission codes.
WITH replacements(old_code, new_code) AS (
    VALUES
        ('hospital.modulight.view', 'hospital.modality.view'),
        ('modulight.view', 'modality.view'),
        ('modulight.add', 'modality.add'),
        ('modulight.edit', 'modality.edit'),
        ('modulight.delete', 'modality.delete')
)
INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT DISTINCT rmd.role_id, new_md.id, COALESCE(rmd.created_by, 1), NOW()
FROM role_module_details rmd
JOIN module_details old_md ON old_md.id = rmd.module_detail_id
JOIN replacements replacement ON replacement.old_code = old_md.code
JOIN module_details new_md ON new_md.code = replacement.new_code
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

-- Hide old/retired permission details from the role UI after replacement.
UPDATE module_details
SET is_active = 2,
    modified = NOW()
WHERE code IN (
    'hospital.modulight.view',
    'modulight.view',
    'modulight.add',
    'modulight.edit',
    'modulight.delete',
    'pacs.study.assign'
);

UPDATE modules
SET is_active = 2,
    modified = NOW()
WHERE code IN ('modulight');

-- Admin roles should continue to own every active permission after the split.
INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT r.id, md.id, 1, NOW()
FROM roles r
JOIN module_details md ON md.is_active = 1
WHERE r.is_active = 1
  AND (
      UPPER(COALESCE(r.code, '')) = 'ADMIN'
      OR LOWER(TRIM(COALESCE(r.name, ''))) = 'admin'
  )
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW(),
    modified_at = NOW()
WHERE id IN (
    SELECT DISTINCT ug.user_id
    FROM user_groups ug
    JOIN roles r ON r.id = ug.role_id
    WHERE ug.is_active = 1
      AND r.is_active = 1
);
