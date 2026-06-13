-- Role-permission menu grouping must come from module_types, not Java fallback code.
-- Repair any existing rows that still miss group metadata and set DB defaults for
-- future module types.

ALTER TABLE module_types
    ALTER COLUMN menu_group_code SET DEFAULT 'SETTING',
    ALTER COLUMN menu_group_name SET DEFAULT 'Setting',
    ALTER COLUMN menu_group_order SET DEFAULT 99;

UPDATE module_types
SET menu_group_code = 'DASHBOARD',
    menu_group_name = 'Dashboard',
    menu_group_order = 1,
    modified = NOW()
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
    modified = NOW()
WHERE UPPER(COALESCE(code, '')) IN ('PACS_PATIENT', 'PACS_QUEUE', 'PACS_STUDY')
  AND (
      COALESCE(menu_group_code, '') <> 'PATIENT'
      OR COALESCE(menu_group_name, '') <> 'Patient'
      OR COALESCE(menu_group_order, 0) <> 2
  );

UPDATE module_types
SET menu_group_code = 'REPORT',
    menu_group_name = 'Report',
    menu_group_order = 3,
    modified = NOW()
WHERE UPPER(COALESCE(code, '')) = 'PACS_REPORT'
  AND (
      COALESCE(menu_group_code, '') <> 'REPORT'
      OR COALESCE(menu_group_name, '') <> 'Report'
      OR COALESCE(menu_group_order, 0) <> 3
  );

UPDATE module_types
SET menu_group_code = 'SETTING',
    menu_group_name = 'Setting',
    menu_group_order = 4,
    modified = NOW()
WHERE UPPER(COALESCE(code, '')) IN ('USER', 'ROLE', 'HOSPITAL')
  AND (
      COALESCE(menu_group_code, '') <> 'SETTING'
      OR COALESCE(menu_group_name, '') <> 'Setting'
      OR COALESCE(menu_group_order, 0) <> 4
  );

UPDATE module_types
SET menu_group_code = 'DICOMCONFIG',
    menu_group_name = 'DICOM Config',
    menu_group_order = 5,
    modified = NOW()
WHERE UPPER(COALESCE(code, '')) = 'PACS_VIEWER'
  AND (
      COALESCE(menu_group_code, '') <> 'DICOMCONFIG'
      OR COALESCE(menu_group_name, '') <> 'DICOM Config'
      OR COALESCE(menu_group_order, 0) <> 5
  );

UPDATE module_types
SET menu_group_code = COALESCE(NULLIF(TRIM(menu_group_code), ''), 'SETTING'),
    menu_group_name = COALESCE(NULLIF(TRIM(menu_group_name), ''), 'Setting'),
    menu_group_order = COALESCE(menu_group_order, 99),
    modified = NOW()
WHERE is_active = 1
  AND (
      menu_group_code IS NULL
      OR TRIM(menu_group_code) = ''
      OR menu_group_name IS NULL
      OR TRIM(menu_group_name) = ''
      OR menu_group_order IS NULL
  );
