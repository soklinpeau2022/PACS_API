-- Restore explicit Study and Setting groups in the permission editor.
-- This changes labels and menu grouping only; permission codes, endpoints, and
-- role grants remain unchanged.

UPDATE module_types
SET menu_group_order = 4,
    modified = NOW(),
    modified_at = NOW()
WHERE is_active = 1
  AND UPPER(COALESCE(menu_group_code, '')) = 'REPORT'
  AND COALESCE(menu_group_order, 0) <> 4;

UPDATE module_types
SET menu_group_order = 5,
    modified = NOW(),
    modified_at = NOW()
WHERE is_active = 1
  AND UPPER(COALESCE(menu_group_code, '')) = 'PACS_SETUP'
  AND COALESCE(menu_group_order, 0) <> 5;

UPDATE module_types
SET menu_group_order = 6,
    modified = NOW(),
    modified_at = NOW()
WHERE is_active = 1
  AND UPPER(COALESCE(menu_group_code, '')) = 'ACCESS'
  AND COALESCE(menu_group_order, 0) <> 6;

UPDATE module_types
SET name = 'Study',
    display_order = 31,
    menu_group_code = 'STUDY',
    menu_group_name = 'Study',
    menu_group_order = 3,
    is_active = 1,
    modified = NOW(),
    modified_at = NOW()
WHERE UPPER(COALESCE(code, '')) = 'PACS_STUDY';

UPDATE modules
SET name = 'Study',
    display_order = 1,
    is_active = 1,
    modified = NOW(),
    modified_at = NOW()
WHERE code = 'pacs-study';

UPDATE module_types
SET name = 'Setting',
    display_order = 70,
    menu_group_code = 'SETTING',
    menu_group_name = 'Setting',
    menu_group_order = 7,
    is_active = 1,
    modified = NOW(),
    modified_at = NOW()
WHERE UPPER(COALESCE(code, '')) = 'APPLICATION_SETTINGS';

UPDATE modules
SET name = 'Setting',
    display_order = 1,
    is_active = 1,
    modified = NOW(),
    modified_at = NOW()
WHERE code = 'application-settings';

UPDATE module_details
SET name = 'Setting (View)',
    type = 'VIEW',
    action_key = 'VIEW',
    display_order = 1,
    is_active = 1,
    modified = NOW(),
    modified_at = NOW()
WHERE code = 'application.settings.view';

UPDATE module_details
SET name = 'Setting (Edit)',
    type = 'EDIT',
    action_key = 'EDIT',
    display_order = 2,
    is_active = 1,
    modified = NOW(),
    modified_at = NOW()
WHERE code = 'application.settings.edit';

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW(),
    modified_at = NOW()
WHERE is_active = 1;
