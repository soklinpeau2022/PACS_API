-- Align permission menu groups with the PACS frontend navigation.
-- This remaps only module_type menu metadata; permission codes, modules,
-- endpoint permissions, and role grants stay unchanged.

ALTER TABLE module_types
    ALTER COLUMN menu_group_code SET DEFAULT 'ACCESS',
    ALTER COLUMN menu_group_name SET DEFAULT 'Access',
    ALTER COLUMN menu_group_order SET DEFAULT 5;

UPDATE module_types
SET menu_group_code = 'DASHBOARD',
    menu_group_name = 'Dashboard',
    menu_group_order = 1,
    modified = NOW(),
    modified_at = NOW()
WHERE UPPER(COALESCE(code, '')) = 'HOME'
  AND (
      COALESCE(menu_group_code, '') <> 'DASHBOARD'
      OR COALESCE(menu_group_name, '') <> 'Dashboard'
      OR COALESCE(menu_group_order, 0) <> 1
  );

UPDATE module_types
SET menu_group_code = 'PATIENT',
    menu_group_name = 'Patient',
    menu_group_order = 2,
    modified = NOW(),
    modified_at = NOW()
WHERE UPPER(COALESCE(code, '')) IN (
    'PACS_PATIENT',
    'PACS_QUEUE',
    'PACS_QUEUE_RESULT',
    'PACS_WORKLIST',
    'PACS_WORKLIST_RESULT',
    'FILE_UPLOAD',
    'PACS_STUDY'
)
  AND (
      COALESCE(menu_group_code, '') <> 'PATIENT'
      OR COALESCE(menu_group_name, '') <> 'Patient'
      OR COALESCE(menu_group_order, 0) <> 2
  );

UPDATE module_types
SET menu_group_code = 'REPORT',
    menu_group_name = 'Report',
    menu_group_order = 3,
    modified = NOW(),
    modified_at = NOW()
WHERE UPPER(COALESCE(code, '')) IN ('SYSTEM_ACTIVITY', 'USER_LOG', 'PACS_REPORT')
  AND (
      COALESCE(menu_group_code, '') <> 'REPORT'
      OR COALESCE(menu_group_name, '') <> 'Report'
      OR COALESCE(menu_group_order, 0) <> 3
  );

UPDATE module_types
SET menu_group_code = 'PACS_SETUP',
    menu_group_name = 'PACS Setup',
    menu_group_order = 4,
    modified = NOW(),
    modified_at = NOW()
WHERE (
      UPPER(COALESCE(code, '')) IN (
          'HOSPITAL',
          'HOSPITAL_MODALITY',
          'MODALITY',
          'PACS_VIEWER',
          'DICOM_SERVER',
          'DICOM_ROUTING',
          'DICOM_MACHINE'
      )
      OR UPPER(COALESCE(menu_group_code, '')) = 'DICOMCONFIG'
  )
  AND (
      COALESCE(menu_group_code, '') <> 'PACS_SETUP'
      OR COALESCE(menu_group_name, '') <> 'PACS Setup'
      OR COALESCE(menu_group_order, 0) <> 4
  );

UPDATE module_types
SET menu_group_code = 'ACCESS',
    menu_group_name = 'Access',
    menu_group_order = 5,
    modified = NOW(),
    modified_at = NOW()
WHERE (
      UPPER(COALESCE(code, '')) IN ('USER', 'ROLE')
      OR UPPER(COALESCE(menu_group_code, '')) = 'SETTING'
      OR menu_group_code IS NULL
      OR TRIM(menu_group_code) = ''
      OR menu_group_name IS NULL
      OR TRIM(menu_group_name) = ''
      OR menu_group_order IS NULL
  )
  AND (
      COALESCE(menu_group_code, '') <> 'ACCESS'
      OR COALESCE(menu_group_name, '') <> 'Access'
      OR COALESCE(menu_group_order, 0) <> 5
  );

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW(),
    modified_at = NOW()
WHERE is_active = 1;
