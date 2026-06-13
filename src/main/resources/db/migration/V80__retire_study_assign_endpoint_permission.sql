-- Retire deprecated Study Assign endpoint and permission mapping.
-- Keep historical migrations immutable; apply cleanup as a new idempotent migration.

-- 1) Deactivate endpoint permission rows for removed endpoint path/permission code.
UPDATE endpoint_permissions
SET is_active = 0
WHERE endpoint_pattern IN ('/study/study-assign', '/study/assign')
   OR permission_code = 'pacs.study.assign';

-- 2) Remove role-to-module permission links for retired PACS Study assign action.
DELETE FROM role_module_details rmd
USING module_details md
WHERE rmd.module_detail_id = md.id
  AND md.code = 'pacs.study.assign';

-- 3) Deactivate retired module detail action so it no longer appears in permission trees.
UPDATE module_details
SET is_active = 0,
    modified = NOW(),
    modified_at = NOW()
WHERE code = 'pacs.study.assign'
  AND COALESCE(is_active, 1) <> 0;

-- 4) Refresh permission cache version for active users.
UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW(),
    modified_at = NOW()
WHERE is_active = 1;
