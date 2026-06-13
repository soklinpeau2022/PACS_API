-- Ensure admin users are attached to ADMIN role in user_groups for their active hospitals.
-- This keeps permission checks consistent and avoids missing menu/button permissions.

INSERT INTO user_groups (user_id, hospital_id, role_id, is_active, created, created_by)
SELECT
    uh.user_id,
    uh.hospital_id,
    r.id,
    1,
    NOW(),
    NULL
FROM user_hospitals uh
JOIN users u
    ON u.id = uh.user_id
   AND u.is_active = 1
JOIN roles r
    ON r.code = 'ADMIN'
   AND r.is_active = 1
   AND (r.hospital_id = uh.hospital_id OR r.hospital_id IS NULL)
WHERE uh.is_active = 1
  AND (u.user_type = 9 OR LOWER(u.username) = 'admin')
ON CONFLICT (user_id, hospital_id, role_id)
DO UPDATE SET
    is_active = 1,
    modified = NOW();

-- Refresh permission version for these admin users so permission cache updates immediately.
UPDATE users u
SET permission_version = COALESCE(u.permission_version, 0) + 1,
    modified = NOW()
WHERE u.is_active = 1
  AND (u.user_type = 9 OR LOWER(u.username) = 'admin');
