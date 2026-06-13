-- Seed stable user-group data for production-like flow:
-- 1) SYSTEM group: SUPER_ADMIN_GROUP
-- 2) HOSPITAL group: HOSPITAL_USER_GROUP (per active hospital)
-- 3) Attach admin user (id=1 / username=admin) to both groups for owned hospitals

-- 1) SYSTEM group for super-admin-only workflows
INSERT INTO roles (hospital_id, code, name, is_system_role, is_active, created, created_at)
SELECT NULL, 'SUPER_ADMIN_GROUP', 'SuperAdmin Group', TRUE, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM roles
    WHERE UPPER(COALESCE(code, '')) = 'SUPER_ADMIN_GROUP'
      AND is_active = 1
);

-- 2) Hospital-scoped user group template for each active hospital
INSERT INTO roles (hospital_id, code, name, is_system_role, is_active, created, created_at)
SELECT h.id, 'HOSPITAL_USER_GROUP', 'HospitalUserGroup', FALSE, 1, NOW(), NOW()
FROM hospitals h
WHERE h.is_active = 1
  AND NOT EXISTS (
      SELECT 1
      FROM roles r
      WHERE r.hospital_id = h.id
        AND UPPER(COALESCE(r.code, '')) = 'HOSPITAL_USER_GROUP'
        AND r.is_active = 1
  );

-- 3a) Attach admin users to SUPER_ADMIN_GROUP for each active user-hospital relation
INSERT INTO user_groups (user_id, hospital_id, role_id, is_active, created, created_at)
SELECT
    uh.user_id,
    uh.hospital_id,
    r.id,
    1,
    NOW(),
    NOW()
FROM user_hospitals uh
JOIN users u ON u.id = uh.user_id
JOIN roles r ON UPPER(COALESCE(r.code, '')) = 'SUPER_ADMIN_GROUP'
           AND r.hospital_id IS NULL
           AND r.is_active = 1
WHERE uh.is_active = 1
  AND u.is_active = 1
  AND (u.id = 1 OR LOWER(COALESCE(u.username, '')) = 'admin' OR u.user_type = 9)
ON CONFLICT (user_id, hospital_id, role_id)
DO UPDATE SET
    is_active = 1,
    modified = NOW(),
    modified_at = NOW();

-- 3b) Attach admin users to HOSPITAL_USER_GROUP in each owned active hospital
INSERT INTO user_groups (user_id, hospital_id, role_id, is_active, created, created_at)
SELECT
    uh.user_id,
    uh.hospital_id,
    r.id,
    1,
    NOW(),
    NOW()
FROM user_hospitals uh
JOIN users u ON u.id = uh.user_id
JOIN roles r ON r.hospital_id = uh.hospital_id
           AND UPPER(COALESCE(r.code, '')) = 'HOSPITAL_USER_GROUP'
           AND r.is_active = 1
WHERE uh.is_active = 1
  AND u.is_active = 1
  AND (u.id = 1 OR LOWER(COALESCE(u.username, '')) = 'admin' OR u.user_type = 9)
ON CONFLICT (user_id, hospital_id, role_id)
DO UPDATE SET
    is_active = 1,
    modified = NOW(),
    modified_at = NOW();

-- 4) Bump permission version so cache refreshes for impacted admin users
UPDATE users u
SET permission_version = COALESCE(u.permission_version, 0) + 1,
    modified = NOW(),
    modified_at = NOW()
WHERE u.is_active = 1
  AND (u.id = 1 OR LOWER(COALESCE(u.username, '')) = 'admin' OR u.user_type = 9);
