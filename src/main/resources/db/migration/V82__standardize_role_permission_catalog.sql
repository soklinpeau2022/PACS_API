-- Standardize the role permission catalog and endpoint permission mapping.
-- This migration is intentionally idempotent: it repairs existing installs without
-- rewriting older Flyway history.

WITH desired_module_types(code, name, display_order, group_code, group_name, group_order) AS (
    VALUES
        ('HOME', 'Dashboard', 1, 'DASHBOARD', 'Dashboard', 1),
        ('PACS_PATIENT', 'Patient', 10, 'PATIENT', 'Patient', 2),
        ('PACS_QUEUE', 'Queue', 11, 'PATIENT', 'Patient', 2),
        ('PACS_STUDY', 'Study', 12, 'PATIENT', 'Patient', 2),
        ('PACS_REPORT', 'Reports', 14, 'REPORT', 'Report', 3),
        ('USER', 'Users', 20, 'SETTING', 'Setting', 4),
        ('ROLE', 'Roles & Permissions', 21, 'SETTING', 'Setting', 4),
        ('HOSPITAL', 'Hospital Setup', 22, 'SETTING', 'Setting', 4),
        ('PACS_VIEWER', 'DICOM Config', 30, 'DICOMCONFIG', 'DICOM Config', 5)
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

WITH desired_modules(module_type_code, code, name, display_order) AS (
    VALUES
        ('HOME', 'home', 'Home', 1),
        ('USER', 'user', 'User', 1),
        ('ROLE', 'role', 'Role', 1),
        ('HOSPITAL', 'hospital', 'Hospital', 1),
        ('HOSPITAL', 'hospital-modality', 'Hospital Modality', 2),
        ('HOSPITAL', 'modality', 'Modality', 3),
        ('PACS_PATIENT', 'pacs-patient', 'Patient', 1),
        ('PACS_QUEUE', 'pacs-queue', 'Queue', 1),
        ('PACS_QUEUE', 'pacs-queue-result', 'Queue Result', 2),
        ('PACS_QUEUE', 'pacs-service', 'Service', 3),
        ('PACS_QUEUE', 'file-upload', 'File Upload', 4),
        ('PACS_STUDY', 'pacs-study', 'Study', 1),
        ('PACS_VIEWER', 'pacs-viewer', 'Viewer', 1),
        ('PACS_VIEWER', 'dicom-server', 'DICOM Server', 2),
        ('PACS_VIEWER', 'dicom-routing', 'DICOM Routing', 3),
        ('PACS_REPORT', 'system-activity', 'System Activity', 1),
        ('PACS_REPORT', 'user-log', 'User Log', 2)
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

        ('pacs-service', 'service.view', 'Service (View)', 'VIEW', 'VIEW', 1),
        ('pacs-service', 'service.add', 'Service (Add)', 'ADD', 'ADD', 2),
        ('pacs-service', 'service.edit', 'Service (Edit)', 'EDIT', 'EDIT', 3),
        ('pacs-service', 'service.delete', 'Service (Delete)', 'DELETE', 'DELETE', 4),

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

        ('file-upload', 'file.view', 'File Upload (View)', 'VIEW', 'VIEW', 1),
        ('file-upload', 'file.add', 'File Upload (Add)', 'ADD', 'ADD', 2),
        ('file-upload', 'file.delete', 'File Upload (Delete)', 'DELETE', 'DELETE', 3),

        ('pacs-study', 'pacs.study.view', 'Study (View)', 'VIEW', 'VIEW', 1),
        ('pacs-viewer', 'pacs.viewer.open', 'Viewer (Open)', 'VIEW', 'OPEN', 1),

        ('dicom-server', 'dicom.server.view', 'DICOM Server (View)', 'VIEW', 'VIEW', 1),
        ('dicom-server', 'dicom.server.add', 'DICOM Server (Add)', 'ADD', 'ADD', 2),
        ('dicom-server', 'dicom.server.edit', 'DICOM Server (Edit)', 'EDIT', 'EDIT', 3),
        ('dicom-server', 'dicom.server.delete', 'DICOM Server (Delete)', 'DELETE', 'DELETE', 4),

        ('dicom-routing', 'dicom.routing.view', 'DICOM Routing (View)', 'VIEW', 'VIEW', 1),
        ('dicom-routing', 'dicom.routing.add', 'DICOM Routing (Add)', 'ADD', 'ADD', 2),
        ('dicom-routing', 'dicom.routing.edit', 'DICOM Routing (Edit)', 'EDIT', 'EDIT', 3),
        ('dicom-routing', 'dicom.routing.delete', 'DICOM Routing (Delete)', 'DELETE', 'DELETE', 4),

        ('system-activity', 'system.activity.view', 'System Activity (View)', 'VIEW', 'VIEW', 1),
        ('user-log', 'report.user_log.view', 'User Log (View)', 'VIEW', 'VIEW', 1)
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

-- Retired endpoints should not show as assignable role permissions.
UPDATE module_details
SET is_active = 2,
    modified = NOW()
WHERE code IN ('pacs.study.assign');

WITH desired_endpoint_permissions(http_method, endpoint_pattern, permission_code, required_scope) AS (
    VALUES
        ('POST', '/dropdown/dropdown-nationality', 'pacs.patient.view', 'pacs.patient.read'),
        ('POST', '/dropdown/dropdown-hospital', 'hospital.view', 'pacs.api'),
        ('POST', '/dropdown/dropdown-service', 'service.view', 'pacs.api'),
        ('POST', '/dropdown/dropdown-service-by-modality', 'service.view', 'pacs.api'),
        ('POST', '/dropdown/dropdown-modality', 'modality.view', 'pacs.api'),
        ('POST', '/dropdown/dropdown-user-group-member', 'user.view', 'user.read'),
        ('POST', '/dropdown/dropdown-user', 'user.view', 'user.read'),
        ('POST', '/dropdown/dropdown-patient', 'pacs.patient.view', 'pacs.patient.read'),
        ('POST', '/dropdown/dropdown-user-group', 'role.view', 'user.read'),

        ('POST', '/hospital/hospital-list', 'hospital.view', 'pacs.api'),
        ('POST', '/hospital/hospital-find/*', 'hospital.view', 'pacs.api'),
        ('POST', '/hospital/hospital-create', 'hospital.add', 'user.write'),
        ('POST', '/hospital/hospital-update', 'hospital.edit', 'user.write'),
        ('POST', '/hospital-modality', 'hospital.modality.view', 'pacs.api'),

        ('POST', '/modality/modality-list', 'modality.view', 'pacs.api'),
        ('POST', '/modality/modality-find/*', 'modality.view', 'pacs.api'),
        ('POST', '/modality/modality-create', 'modality.add', 'user.write'),
        ('POST', '/modality/modality-update', 'modality.edit', 'user.write'),
        ('POST', '/modality/modality-delete/*', 'modality.delete', 'user.write'),

        ('POST', '/module-type/module-type-list', 'role.assign_permission', 'user.write'),
        ('POST', '/module-type/find/*', 'role.assign_permission', 'user.write'),
        ('POST', '/permission/permission-tree', 'role.assign_permission', 'user.write'),
        ('POST', '/permission/permission-save-role-permissions', 'role.assign_permission', 'user.write'),

        ('POST', '/patient/patient-list', 'pacs.patient.view', 'pacs.patient.read'),
        ('POST', '/patient/patient-find/*', 'pacs.patient.view', 'pacs.patient.read'),
        ('POST', '/patient/patient-create', 'pacs.patient.create', 'pacs.patient.write'),
        ('POST', '/patient/patient-update', 'pacs.patient.edit', 'pacs.patient.write'),

        ('POST', '/queue/queue-list', 'pacs.queue.view', 'pacs.api'),
        ('POST', '/queue/queue-assign', 'pacs.queue.assign', 'pacs.api'),
        ('POST', '/queue/queue-send-to-pacs', 'pacs.queue.send', 'pacs.api'),
        ('POST', '/queue/queue-received-study', 'pacs.queue.receive', 'pacs.api'),
        ('POST', '/queue/queue-view-study', 'pacs.queue.view_study', 'pacs.api'),
        ('POST', '/queue/queue-translate', 'pacs.queue.translate', 'pacs.api'),
        ('POST', '/queue/queue-return', 'pacs.queue.return', 'pacs.api'),
        ('POST', '/queue/queue-cancel', 'pacs.queue.cancel', 'pacs.api'),
        ('POST', '/queue/queue-complete', 'pacs.queue.complete', 'pacs.api'),
        ('POST', '/queue/queue-result-list', 'pacs.queue.result.view', 'pacs.api'),
        ('POST', '/queue/queue-result-find/*', 'pacs.queue.result.view', 'pacs.api'),
        ('POST', '/queue/queue-result-create', 'pacs.queue.result.create', 'pacs.api'),
        ('POST', '/queue/queue-result-update', 'pacs.queue.result.edit', 'pacs.api'),
        ('POST', '/queue/queue-result-delete/*', 'pacs.queue.result.delete', 'pacs.api'),

        ('POST', '/service/service-list', 'service.view', 'pacs.api'),
        ('POST', '/service/service-find/*', 'service.view', 'pacs.api'),
        ('POST', '/service/service-create', 'service.add', 'user.write'),
        ('POST', '/service/service-update', 'service.edit', 'user.write'),
        ('POST', '/service/service-delete/*', 'service.delete', 'user.write'),

        ('POST', '/study/study-list', 'pacs.study.view', 'pacs.study.read'),
        ('POST', '/study/study-find/*', 'pacs.study.view', 'pacs.study.read'),

        ('POST', '/viewer/viewer-open', 'pacs.viewer.open', 'pacs.viewer.open'),
        ('POST', '/viewer/viewer-validate', 'pacs.viewer.open', 'pacs.viewer.open'),
        ('POST', '/viewer/viewer-close', 'pacs.viewer.open', 'pacs.viewer.open'),

        ('POST', '/dicom-server/dicom-server-list', 'dicom.server.view', 'pacs.api'),
        ('POST', '/dicom-server/dicom-server-find/*', 'dicom.server.view', 'pacs.api'),
        ('POST', '/dicom-server/dicom-server-create', 'dicom.server.add', 'pacs.api'),
        ('POST', '/dicom-server/dicom-server-update', 'dicom.server.edit', 'pacs.api'),
        ('POST', '/dicom-server/dicom-server-delete/*', 'dicom.server.delete', 'pacs.api'),
        ('POST', '/dicom-routing/dicom-routing-list', 'dicom.routing.view', 'pacs.api'),
        ('POST', '/dicom-routing/dicom-routing-find/*', 'dicom.routing.view', 'pacs.api'),
        ('POST', '/dicom-routing/dicom-routing-create', 'dicom.routing.add', 'pacs.api'),
        ('POST', '/dicom-routing/dicom-routing-update', 'dicom.routing.edit', 'pacs.api'),
        ('POST', '/dicom-routing/dicom-routing-delete/*', 'dicom.routing.delete', 'pacs.api'),

        ('GET', '/file/file-upload/*', 'file.view', 'pacs.api'),
        ('POST', '/file/file-upload', 'file.add', 'pacs.api'),
        ('DELETE', '/file/file-delete', 'file.delete', 'pacs.api'),

        ('POST', '/system-activity/system-activity-list', 'system.activity.view', 'user.read'),
        ('POST', '/system-activity/system-activity-find/*', 'system.activity.view', 'user.read'),
        ('POST', '/report/user-log/user-log-list', 'report.user_log.view', 'user.read'),
        ('POST', '/report/user-log/user-log-find/*', 'report.user_log.view', 'user.read'),

        ('POST', '/user/user-list', 'user.view', 'user.read'),
        ('POST', '/user/user-find/*', 'user.view', 'user.read'),
        ('POST', '/user/user-create', 'user.add', 'user.write'),
        ('POST', '/user/user-update', 'user.edit', 'user.write'),
        ('POST', '/user/user-delete/*', 'user.delete', 'user.write'),
        ('POST', '/user/user-change-password', 'user.edit', 'user.write'),

        ('POST', '/role/role-list', 'role.view', 'user.read'),
        ('POST', '/role/user-group-list', 'role.view', 'user.read'),
        ('POST', '/role/role-find/*', 'role.view', 'user.read'),
        ('POST', '/role/role-add', 'role.add', 'user.write'),
        ('POST', '/role/role-update', 'role.edit', 'user.write'),
        ('POST', '/role/role-delete/*', 'role.delete', 'user.write')
)
UPDATE endpoint_permissions ep
SET is_active = 2
WHERE EXISTS (
    SELECT 1
    FROM desired_endpoint_permissions desired
    WHERE desired.http_method = ep.http_method
      AND desired.endpoint_pattern = ep.endpoint_pattern
)
  AND NOT EXISTS (
    SELECT 1
    FROM desired_endpoint_permissions desired
    WHERE desired.http_method = ep.http_method
      AND desired.endpoint_pattern = ep.endpoint_pattern
      AND desired.permission_code = ep.permission_code
);

WITH desired_endpoint_permissions(http_method, endpoint_pattern, permission_code, required_scope) AS (
    VALUES
        ('POST', '/dropdown/dropdown-nationality', 'pacs.patient.view', 'pacs.patient.read'),
        ('POST', '/dropdown/dropdown-hospital', 'hospital.view', 'pacs.api'),
        ('POST', '/dropdown/dropdown-service', 'service.view', 'pacs.api'),
        ('POST', '/dropdown/dropdown-service-by-modality', 'service.view', 'pacs.api'),
        ('POST', '/dropdown/dropdown-modality', 'modality.view', 'pacs.api'),
        ('POST', '/dropdown/dropdown-user-group-member', 'user.view', 'user.read'),
        ('POST', '/dropdown/dropdown-user', 'user.view', 'user.read'),
        ('POST', '/dropdown/dropdown-patient', 'pacs.patient.view', 'pacs.patient.read'),
        ('POST', '/dropdown/dropdown-user-group', 'role.view', 'user.read'),
        ('POST', '/hospital/hospital-list', 'hospital.view', 'pacs.api'),
        ('POST', '/hospital/hospital-find/*', 'hospital.view', 'pacs.api'),
        ('POST', '/hospital/hospital-create', 'hospital.add', 'user.write'),
        ('POST', '/hospital/hospital-update', 'hospital.edit', 'user.write'),
        ('POST', '/hospital-modality', 'hospital.modality.view', 'pacs.api'),
        ('POST', '/modality/modality-list', 'modality.view', 'pacs.api'),
        ('POST', '/modality/modality-find/*', 'modality.view', 'pacs.api'),
        ('POST', '/modality/modality-create', 'modality.add', 'user.write'),
        ('POST', '/modality/modality-update', 'modality.edit', 'user.write'),
        ('POST', '/modality/modality-delete/*', 'modality.delete', 'user.write'),
        ('POST', '/module-type/module-type-list', 'role.assign_permission', 'user.write'),
        ('POST', '/module-type/find/*', 'role.assign_permission', 'user.write'),
        ('POST', '/permission/permission-tree', 'role.assign_permission', 'user.write'),
        ('POST', '/permission/permission-save-role-permissions', 'role.assign_permission', 'user.write'),
        ('POST', '/patient/patient-list', 'pacs.patient.view', 'pacs.patient.read'),
        ('POST', '/patient/patient-find/*', 'pacs.patient.view', 'pacs.patient.read'),
        ('POST', '/patient/patient-create', 'pacs.patient.create', 'pacs.patient.write'),
        ('POST', '/patient/patient-update', 'pacs.patient.edit', 'pacs.patient.write'),
        ('POST', '/queue/queue-list', 'pacs.queue.view', 'pacs.api'),
        ('POST', '/queue/queue-assign', 'pacs.queue.assign', 'pacs.api'),
        ('POST', '/queue/queue-send-to-pacs', 'pacs.queue.send', 'pacs.api'),
        ('POST', '/queue/queue-received-study', 'pacs.queue.receive', 'pacs.api'),
        ('POST', '/queue/queue-view-study', 'pacs.queue.view_study', 'pacs.api'),
        ('POST', '/queue/queue-translate', 'pacs.queue.translate', 'pacs.api'),
        ('POST', '/queue/queue-return', 'pacs.queue.return', 'pacs.api'),
        ('POST', '/queue/queue-cancel', 'pacs.queue.cancel', 'pacs.api'),
        ('POST', '/queue/queue-complete', 'pacs.queue.complete', 'pacs.api'),
        ('POST', '/queue/queue-result-list', 'pacs.queue.result.view', 'pacs.api'),
        ('POST', '/queue/queue-result-find/*', 'pacs.queue.result.view', 'pacs.api'),
        ('POST', '/queue/queue-result-create', 'pacs.queue.result.create', 'pacs.api'),
        ('POST', '/queue/queue-result-update', 'pacs.queue.result.edit', 'pacs.api'),
        ('POST', '/queue/queue-result-delete/*', 'pacs.queue.result.delete', 'pacs.api'),
        ('POST', '/service/service-list', 'service.view', 'pacs.api'),
        ('POST', '/service/service-find/*', 'service.view', 'pacs.api'),
        ('POST', '/service/service-create', 'service.add', 'user.write'),
        ('POST', '/service/service-update', 'service.edit', 'user.write'),
        ('POST', '/service/service-delete/*', 'service.delete', 'user.write'),
        ('POST', '/study/study-list', 'pacs.study.view', 'pacs.study.read'),
        ('POST', '/study/study-find/*', 'pacs.study.view', 'pacs.study.read'),
        ('POST', '/viewer/viewer-open', 'pacs.viewer.open', 'pacs.viewer.open'),
        ('POST', '/viewer/viewer-validate', 'pacs.viewer.open', 'pacs.viewer.open'),
        ('POST', '/viewer/viewer-close', 'pacs.viewer.open', 'pacs.viewer.open'),
        ('POST', '/dicom-server/dicom-server-list', 'dicom.server.view', 'pacs.api'),
        ('POST', '/dicom-server/dicom-server-find/*', 'dicom.server.view', 'pacs.api'),
        ('POST', '/dicom-server/dicom-server-create', 'dicom.server.add', 'pacs.api'),
        ('POST', '/dicom-server/dicom-server-update', 'dicom.server.edit', 'pacs.api'),
        ('POST', '/dicom-server/dicom-server-delete/*', 'dicom.server.delete', 'pacs.api'),
        ('POST', '/dicom-routing/dicom-routing-list', 'dicom.routing.view', 'pacs.api'),
        ('POST', '/dicom-routing/dicom-routing-find/*', 'dicom.routing.view', 'pacs.api'),
        ('POST', '/dicom-routing/dicom-routing-create', 'dicom.routing.add', 'pacs.api'),
        ('POST', '/dicom-routing/dicom-routing-update', 'dicom.routing.edit', 'pacs.api'),
        ('POST', '/dicom-routing/dicom-routing-delete/*', 'dicom.routing.delete', 'pacs.api'),
        ('GET', '/file/file-upload/*', 'file.view', 'pacs.api'),
        ('POST', '/file/file-upload', 'file.add', 'pacs.api'),
        ('DELETE', '/file/file-delete', 'file.delete', 'pacs.api'),
        ('POST', '/system-activity/system-activity-list', 'system.activity.view', 'user.read'),
        ('POST', '/system-activity/system-activity-find/*', 'system.activity.view', 'user.read'),
        ('POST', '/report/user-log/user-log-list', 'report.user_log.view', 'user.read'),
        ('POST', '/report/user-log/user-log-find/*', 'report.user_log.view', 'user.read'),
        ('POST', '/user/user-list', 'user.view', 'user.read'),
        ('POST', '/user/user-find/*', 'user.view', 'user.read'),
        ('POST', '/user/user-create', 'user.add', 'user.write'),
        ('POST', '/user/user-update', 'user.edit', 'user.write'),
        ('POST', '/user/user-delete/*', 'user.delete', 'user.write'),
        ('POST', '/user/user-change-password', 'user.edit', 'user.write'),
        ('POST', '/role/role-list', 'role.view', 'user.read'),
        ('POST', '/role/user-group-list', 'role.view', 'user.read'),
        ('POST', '/role/role-find/*', 'role.view', 'user.read'),
        ('POST', '/role/role-add', 'role.add', 'user.write'),
        ('POST', '/role/role-update', 'role.edit', 'user.write'),
        ('POST', '/role/role-delete/*', 'role.delete', 'user.write')
)
INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
SELECT http_method, endpoint_pattern, permission_code, required_scope, 1
FROM desired_endpoint_permissions
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

-- Keep roles that could already edit queue results/file-backed reports working
-- after the queue-result permissions were split from pacs.queue.translate.
WITH roles_with_queue_result_access AS (
    SELECT DISTINCT rmd.role_id
    FROM role_module_details rmd
    JOIN module_details md ON md.id = rmd.module_detail_id
    WHERE md.code = 'pacs.queue.translate'
),
preserved_permissions AS (
    SELECT id
    FROM module_details
    WHERE code IN (
        'pacs.queue.result.view',
        'pacs.queue.result.create',
        'pacs.queue.result.edit',
        'pacs.queue.result.delete',
        'file.view',
        'file.add',
        'file.delete'
    )
      AND is_active = 1
)
INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT rwa.role_id, pp.id, 1, NOW()
FROM roles_with_queue_result_access rwa
CROSS JOIN preserved_permissions pp
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

-- Admin roles should always see every active permission in the role-permission UI.
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
