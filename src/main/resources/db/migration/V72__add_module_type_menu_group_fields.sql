ALTER TABLE module_types
    ADD COLUMN IF NOT EXISTS menu_group_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS menu_group_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS menu_group_order SMALLINT;

UPDATE module_types
SET menu_group_code = 'DASHBOARD',
    menu_group_name = 'Dashboard',
    menu_group_order = 1
WHERE UPPER(COALESCE(code, '')) = 'HOME';

UPDATE module_types
SET menu_group_code = 'PATIENT',
    menu_group_name = 'Patient',
    menu_group_order = 2
WHERE UPPER(COALESCE(code, '')) IN ('PACS_PATIENT', 'PACS_QUEUE', 'PACS_STUDY');

UPDATE module_types
SET menu_group_code = 'REPORT',
    menu_group_name = 'Report',
    menu_group_order = 3
WHERE UPPER(COALESCE(code, '')) = 'PACS_REPORT';

UPDATE module_types
SET menu_group_code = 'SETTING',
    menu_group_name = 'Setting',
    menu_group_order = 4
WHERE UPPER(COALESCE(code, '')) IN ('USER', 'ROLE', 'HOSPITAL');

UPDATE module_types
SET menu_group_code = 'DICOMCONFIG',
    menu_group_name = 'DICOM Config',
    menu_group_order = 5
WHERE UPPER(COALESCE(code, '')) = 'PACS_VIEWER';
