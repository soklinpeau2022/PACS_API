UPDATE module_types
SET menu_group_code = 'DASHBOARD'
WHERE UPPER(COALESCE(menu_group_code, '')) = 'DASKBOARD';

UPDATE module_types
SET menu_group_code = 'PATIENT'
WHERE UPPER(COALESCE(menu_group_code, '')) = 'PATIEN';
