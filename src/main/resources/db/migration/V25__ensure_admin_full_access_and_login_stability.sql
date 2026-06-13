-- Ensure admin user id=1 remains login-capable and has full authorization coverage.
-- Scope:
-- 1) Keep admin accounts active/unlocked and non-expired.
-- 2) Ensure ADMIN roles include all active module_detail permissions.
-- 3) Ensure admin users are linked to all active roles in their active hospitals.

-- 1) Login stability for admin user id=1.
UPDATE users u
SET is_active = 1,
    account_locked = FALSE,
    failed_login_count = 0,
    expire_date = COALESCE(u.expire_date, DATE '2099-12-31'),
    modified = NOW()
WHERE u.id = 1;

-- 2) ADMIN role gets all active module permissions.
INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT
    r.id,
    md.id,
    NULL,
    NOW()
FROM roles r
JOIN module_details md
  ON md.is_active = 1
WHERE r.is_active = 1
  AND r.code = 'ADMIN'
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

INSERT INTO user_groups (user_id, hospital_id, role_id, is_active, created_by, created)
SELECT
    1 AS user_id,
    uh.hospital_id,
    r.id,
    1,
    1 AS created_by,
    NOW()
FROM user_hospitals uh
JOIN users u
  ON u.id = uh.user_id
 AND u.id = 1
 AND u.is_active = 1
 AND LOWER(u.username) = 'admin'
JOIN roles r
  ON r.is_active = 1
 AND (r.hospital_id = uh.hospital_id OR r.hospital_id IS NULL)
WHERE uh.user_id = 1
 AND uh.is_active = 1
ON CONFLICT (user_id, hospital_id, role_id)
DO UPDATE SET
    is_active = 1,
    modified_by = EXCLUDED.created_by,
    modified = NOW();

-- Refresh permission cache version for admin user id=1.
UPDATE users u
SET permission_version = COALESCE(u.permission_version, 0) + 1,
    modified = NOW()
WHERE u.id = 1;
