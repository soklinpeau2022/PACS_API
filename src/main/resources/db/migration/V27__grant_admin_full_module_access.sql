-- Comprehensive grant: ensure the ADMIN role owns every active module permission
-- and the admin user (id=1) is linked to ADMIN in every active hospital.
-- Safe to re-run: all statements use ON CONFLICT / DO UPDATE.

-- 1) Ensure ADMIN role exists (global, no hospital scope).
INSERT INTO roles (hospital_id, code, name, is_system_role, is_active, created)
VALUES (NULL, 'ADMIN', 'System Admin', TRUE, 1, NOW())
ON CONFLICT (hospital_id, name) DO UPDATE
    SET is_active = 1,
        is_system_role = TRUE,
        modified = NOW();

-- 2) Ensure home module exists under HOME module type.
INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'home', 'Home', 1, 1, NOW()
FROM module_types mt
WHERE mt.code = 'HOME'
ON CONFLICT (code) DO UPDATE SET is_active = 1;

-- 3) Ensure home.view permission exists.
INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, 'home.view', 'Home (View)', 'VIEW', 'VIEW', 1, 1, NOW()
FROM modules m
WHERE m.code = 'home'
ON CONFLICT (code) DO UPDATE SET is_active = 1;

-- 4) Grant every active module_detail to the ADMIN role.
INSERT INTO role_module_details (role_id, module_detail_id, created)
SELECT r.id, md.id, NOW()
FROM roles r
JOIN module_details md ON md.is_active = 1
WHERE r.code    = 'ADMIN'
  AND r.is_active = 1
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

-- 5) Ensure admin user is active and unlocked.
UPDATE users
SET is_active          = 1,
    account_locked     = FALSE,
    failed_login_count = 0,
    expire_date        = COALESCE(expire_date, DATE '2099-12-31'),
    modified           = NOW()
WHERE id = 1;

-- 6) Ensure admin is registered in every active hospital (user_hospitals).
INSERT INTO user_hospitals (user_id, hospital_id, is_default, is_active, created)
SELECT 1, h.id, FALSE, 1, NOW()
FROM hospitals h
WHERE h.is_active = 1
ON CONFLICT (user_id, hospital_id) DO UPDATE
    SET is_active = 1,
        modified  = NOW();

-- Mark the first hospital as default if none is set yet.
UPDATE user_hospitals
SET is_default = TRUE,
    modified   = NOW()
WHERE user_id    = 1
  AND hospital_id = (
        SELECT hospital_id
        FROM user_hospitals
        WHERE user_id  = 1
          AND is_active = 1
        ORDER BY hospital_id
        LIMIT 1
      )
  AND NOT EXISTS (
        SELECT 1
        FROM user_hospitals
        WHERE user_id   = 1
          AND is_default = TRUE
          AND is_active  = 1
      );

-- 7) Assign admin to the ADMIN role in every active hospital.
INSERT INTO user_groups (user_id, hospital_id, role_id, is_active, created_by, created)
SELECT
    1,
    uh.hospital_id,
    r.id,
    1,
    1,
    NOW()
FROM user_hospitals uh
JOIN roles r
  ON r.code = 'ADMIN'
 AND r.is_active = 1
 AND (r.hospital_id = uh.hospital_id OR r.hospital_id IS NULL)
WHERE uh.user_id  = 1
  AND uh.is_active = 1
ON CONFLICT (user_id, hospital_id, role_id) DO UPDATE
    SET is_active   = 1,
        modified_by = 1,
        modified    = NOW();

-- 8) Bump permission_version so the next token refresh picks up the new grants.
UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified           = NOW()
WHERE id = 1;
