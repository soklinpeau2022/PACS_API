-- System role used to grant cross-hospital visibility in user-management endpoints.
-- Users with this role (or super admin user id=1) can list/find/update/delete users across hospitals.
INSERT INTO roles (hospital_id, code, name, is_system_role, is_active, created, created_at)
SELECT NULL, 'USER_HOSPITAL_SCOPE_ALL', 'User Hospital Scope All', TRUE, 1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM roles
    WHERE UPPER(COALESCE(code, '')) = 'USER_HOSPITAL_SCOPE_ALL'
      AND is_active = 1
);
