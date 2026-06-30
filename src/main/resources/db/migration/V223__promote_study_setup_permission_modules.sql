-- Promote study-related setup permissions into first-class PACS Setup modules.
-- Existing permission codes and role grants are preserved; this migration only
-- repairs grouping, labels, endpoint mappings, and cache versions.

WITH desired_module_types(code, name, display_order, group_code, group_name, group_order) AS (
    VALUES
        ('PACS_STUDY', 'Study Browser', 43, 'PACS_SETUP', 'PACS Setup', 4),
        ('STUDY_RETENTION', 'Study Retention', 47, 'PACS_SETUP', 'PACS Setup', 4),
        ('PACS_RESULT_TEMPLATE', 'Result Templates', 48, 'PACS_SETUP', 'PACS Setup', 4)
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
    modified = NOW(),
    modified_at = NOW();

WITH desired_modules(module_type_code, code, name, display_order) AS (
    VALUES
        ('PACS_STUDY', 'pacs-study', 'Study Browser', 1),
        ('STUDY_RETENTION', 'study-retention', 'Study Retention', 1),
        ('PACS_RESULT_TEMPLATE', 'pacs-result-template', 'Result Templates', 1)
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
    modified = NOW(),
    modified_at = NOW();

WITH desired_details(module_code, permission_code, permission_name, permission_type, action_key, display_order) AS (
    VALUES
        ('pacs-study', 'pacs.study.view', 'Study (View)', 'VIEW', 'VIEW', 1),
        ('pacs-study', 'pacs.study.upload', 'Study (DICOM Upload)', 'ADD', 'UPLOAD', 2),
        ('pacs-study', 'pacs.study.status_update', 'Reopen Study', 'ACTION', 'REOPEN_STUDY', 3),

        ('study-retention', 'study.retention.policy.view', 'Study Retention Policy (View)', 'VIEW', 'VIEW', 1),
        ('study-retention', 'study.retention.policy.add', 'Study Retention Policy (Add)', 'ADD', 'ADD', 2),
        ('study-retention', 'study.retention.policy.edit', 'Study Retention Policy (Edit)', 'EDIT', 'EDIT', 3),
        ('study-retention', 'study.retention.policy.delete', 'Study Retention Policy (Delete)', 'DELETE', 'DELETE', 4),
        ('study-retention', 'study.retention.approval.view', 'Study Retention Approval (View)', 'VIEW', 'VIEW', 5),
        ('study-retention', 'study.retention.approval.approve', 'Study Retention Approval (Approve Delete)', 'APPROVE', 'APPROVE', 6),

        ('pacs-result-template', 'pacs.result.template.view', 'Result Templates (View)', 'VIEW', 'VIEW', 1),
        ('pacs-result-template', 'pacs.result.template.add', 'Result Templates (Add)', 'ADD', 'ADD', 2),
        ('pacs-result-template', 'pacs.result.template.edit', 'Result Templates (Edit)', 'EDIT', 'EDIT', 3),
        ('pacs-result-template', 'pacs.result.template.delete', 'Result Templates (Delete)', 'DELETE', 'DELETE', 4)
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

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/study/study-list', 'pacs.study.view', 'pacs.study.read', 1),
    ('POST', '/study/study-find/*', 'pacs.study.view', 'pacs.study.read', 1),
    ('GET', '/study/*/viewer-info', 'pacs.study.view', 'pacs.study.read', 1),
    ('POST', '/dicom-uploads', 'pacs.study.upload', 'pacs.study.read', 1),
    ('POST', '/dicom-uploads/chunk/init', 'pacs.study.upload', 'pacs.study.read', 1),
    ('POST', '/dicom-uploads/chunk/*', 'pacs.study.upload', 'pacs.study.read', 1),
    ('GET', '/dicom-uploads/chunk/*/status', 'pacs.study.upload', 'pacs.study.read', 1),
    ('POST', '/dicom-uploads/chunk/*/complete', 'pacs.study.upload', 'pacs.study.read', 1),
    ('DELETE', '/dicom-uploads/chunk/*/abort', 'pacs.study.upload', 'pacs.study.read', 1),
    ('POST', '/study/study-status-update/*', 'pacs.study.status_update', 'pacs.study.read', 1),

    ('POST', '/study-retention/policy-list', 'study.retention.policy.view', 'pacs.api', 1),
    ('POST', '/study-retention/policy-find/*', 'study.retention.policy.view', 'pacs.api', 1),
    ('POST', '/study-retention/policy-save', 'study.retention.policy.add', 'pacs.api', 1),
    ('POST', '/study-retention/policy-save', 'study.retention.policy.edit', 'pacs.api', 1),
    ('POST', '/study-retention/policy-delete/*', 'study.retention.policy.delete', 'pacs.api', 1),
    ('POST', '/study-retention/review-list', 'study.retention.approval.view', 'pacs.api', 1),
    ('POST', '/study-retention/summary', 'study.retention.approval.view', 'pacs.api', 1),
    ('POST', '/study-retention/approve-delete/*', 'study.retention.approval.approve', 'pacs.api', 1),
    ('POST', '/study-retention/reject-delete/*', 'study.retention.approval.approve', 'pacs.api', 1),
    ('POST', '/study-retention/bulk-delete', 'study.retention.approval.approve', 'pacs.api', 1),
    ('POST', '/study-retention/auto-delete-run', 'study.retention.approval.approve', 'pacs.api', 1),

    ('POST', '/pacs-result-template/pacs-result-template-list', 'pacs.result.template.view', 'pacs.api', 1),
    ('POST', '/pacs-result-template/pacs-result-template-find/*', 'pacs.result.template.view', 'pacs.api', 1),
    ('POST', '/pacs-result-template/pacs-result-template-create', 'pacs.result.template.add', 'pacs.api', 1),
    ('POST', '/pacs-result-template/pacs-result-template-update', 'pacs.result.template.edit', 'pacs.api', 1),
    ('POST', '/pacs-result-template/pacs-result-template-delete/*', 'pacs.result.template.delete', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW(),
    modified_at = NOW()
WHERE is_active = 1;
