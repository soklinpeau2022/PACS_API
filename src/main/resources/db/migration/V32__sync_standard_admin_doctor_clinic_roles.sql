-- Sync standard hospital role groups and baseline permissions for Admin / Doctor / Clinic.
-- Idempotent and PostgreSQL-safe.

-- 1) Normalize existing role names (case-insensitive) for active hospitals.
UPDATE roles r
SET name = 'Admin',
    code = COALESCE(NULLIF(r.code, ''), 'ADMIN'),
    is_active = 1,
    modified = NOW(),
    modified_at = NOW()
FROM hospitals h
WHERE h.id = r.hospital_id
  AND h.is_active = 1
  AND LOWER(r.name) = 'admin';

UPDATE roles r
SET name = 'Doctor',
    code = COALESCE(NULLIF(r.code, ''), 'DOCTOR'),
    is_active = 1,
    modified = NOW(),
    modified_at = NOW()
FROM hospitals h
WHERE h.id = r.hospital_id
  AND h.is_active = 1
  AND LOWER(r.name) = 'doctor';

UPDATE roles r
SET name = 'Clinic',
    code = COALESCE(NULLIF(r.code, ''), 'CLINIC'),
    is_active = 1,
    modified = NOW(),
    modified_at = NOW()
FROM hospitals h
WHERE h.id = r.hospital_id
  AND h.is_active = 1
  AND LOWER(r.name) = 'clinic';

-- 2) Ensure each active hospital has Admin / Doctor / Clinic roles.
INSERT INTO roles (hospital_id, code, name, description, is_system_role, is_active, created_by, created, created_at)
SELECT h.id, 'ADMIN', 'Admin', 'Hospital admin group', FALSE, 1, 1, NOW(), NOW()
FROM hospitals h
WHERE h.is_active = 1
  AND NOT EXISTS (
      SELECT 1
      FROM roles r
      WHERE r.hospital_id = h.id
        AND LOWER(r.name) = 'admin'
  );

INSERT INTO roles (hospital_id, code, name, description, is_system_role, is_active, created_by, created, created_at)
SELECT h.id, 'DOCTOR', 'Doctor', 'Doctor group', FALSE, 1, 1, NOW(), NOW()
FROM hospitals h
WHERE h.is_active = 1
  AND NOT EXISTS (
      SELECT 1
      FROM roles r
      WHERE r.hospital_id = h.id
        AND LOWER(r.name) = 'doctor'
  );

INSERT INTO roles (hospital_id, code, name, description, is_system_role, is_active, created_by, created, created_at)
SELECT h.id, 'CLINIC', 'Clinic', 'Clinic group', FALSE, 1, 1, NOW(), NOW()
FROM hospitals h
WHERE h.is_active = 1
  AND NOT EXISTS (
      SELECT 1
      FROM roles r
      WHERE r.hospital_id = h.id
        AND LOWER(r.name) = 'clinic'
  );

-- 3) Reset baseline permissions for these standard groups.
DELETE FROM role_module_details rmd
USING roles r
WHERE rmd.role_id = r.id
  AND r.hospital_id IS NOT NULL
  AND LOWER(r.name) IN ('admin', 'doctor', 'clinic');

-- Admin -> full access to all active module_details.
INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT r.id, md.id, 1, NOW()
FROM roles r
JOIN module_details md ON md.is_active = 1
WHERE r.hospital_id IS NOT NULL
  AND LOWER(r.name) = 'admin'
  AND r.is_active = 1
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

-- Doctor -> study/viewer focused operational access.
INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT r.id, md.id, 1, NOW()
FROM roles r
JOIN module_details md ON md.is_active = 1
WHERE r.hospital_id IS NOT NULL
  AND LOWER(r.name) = 'doctor'
  AND r.is_active = 1
  AND md.code IN (
      'home.view',
      'hospital.modality.view',
      'pacs.patient.view',
      'pacs.queue.view',
      'pacs.queue.view_study',
      'pacs.queue.receive',
      'pacs.queue.translate',
      'pacs.study.view',
      'pacs.study.assign',
      'pacs.viewer.open'
  )
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

-- Clinic -> registration/queue flow access.
INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT r.id, md.id, 1, NOW()
FROM roles r
JOIN module_details md ON md.is_active = 1
WHERE r.hospital_id IS NOT NULL
  AND LOWER(r.name) = 'clinic'
  AND r.is_active = 1
  AND md.code IN (
      'home.view',
      'hospital.modality.view',
      'service.view',
      'pacs.patient.view',
      'pacs.patient.create',
      'pacs.patient.edit',
      'pacs.queue.view',
      'pacs.queue.assign',
      'pacs.queue.send',
      'pacs.queue.return',
      'pacs.queue.cancel',
      'pacs.queue.view_study'
  )
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

-- 4) Ensure system-admin users (user_type=9) are mapped to each hospital's local Admin role.
INSERT INTO user_groups (user_id, hospital_id, role_id, is_active, created_by, created, created_at)
SELECT uh.user_id, uh.hospital_id, r.id, 1, 1, NOW(), NOW()
FROM user_hospitals uh
JOIN users u
  ON u.id = uh.user_id
 AND u.is_active = 1
JOIN roles r
  ON r.hospital_id = uh.hospital_id
 AND LOWER(r.name) = 'admin'
 AND r.is_active = 1
WHERE uh.is_active = 1
  AND COALESCE(u.user_type, 0) = 9
ON CONFLICT (user_id, hospital_id, role_id) DO UPDATE
SET is_active = 1,
    modified_by = 1,
    modified = NOW(),
    modified_at = NOW();

-- 5) Bump permission_version so new role/group grants are picked up.
UPDATE users u
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW(),
    modified_at = NOW()
WHERE u.id IN (
    SELECT DISTINCT ug.user_id
    FROM user_groups ug
    JOIN roles r ON r.id = ug.role_id
    WHERE ug.is_active = 1
      AND r.hospital_id IS NOT NULL
      AND LOWER(r.name) IN ('admin', 'doctor', 'clinic')
);
