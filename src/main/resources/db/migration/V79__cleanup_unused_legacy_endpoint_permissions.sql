-- Consolidated cleanup for legacy/unused endpoint permission paths.
-- Important: We do NOT delete old Flyway files. We only normalize data in endpoint_permissions.

UPDATE endpoint_permissions
SET is_active = 0
WHERE endpoint_pattern IN (
    -- Legacy auth aliases
    '/auth/login',
    '/auth/logout',
    '/auth/refresh',
    '/auth/client-credentials',

    -- Legacy user aliases
    '/user/list',
    '/user/add',
    '/user/create',
    '/user/update',
    '/user/delete/*',
    '/user/me',

    -- Legacy role aliases and typo
    '/role/list',
    '/role/add',
    '/role/create',
    '/role/update',
    '/role/delete/*',
    '/role/menu',
    '/role/role-create',
    '/role/user-groupl-list',

    -- Legacy permission aliases
    '/permission/tree',
    '/permission/save-role-permissions',

    -- Legacy module/module-type aliases
    '/module-type-list',
    '/module-type/list',
    '/module-list',
    '/module-detail-list',

    -- Legacy hospital aliases
    '/hospital/list',
    '/hospital/find/*',
    '/hospital/create',
    '/hospital/update',

    -- Legacy patient aliases
    '/patient/list',
    '/patient/find/*',
    '/patient/create',
    '/patient/update',

    -- Legacy queue aliases
    '/queue/list',
    '/queue/return',
    '/queue/cancel',
    '/queue/complete',

    -- Legacy study aliases
    '/study/list',
    '/study/find/*',
    '/study/assign',

    -- Legacy viewer aliases
    '/viewer/open',
    '/viewer/validate',
    '/viewer/close',

    -- Legacy system activity aliases
    '/system-activity/list',
    '/system-activity/find/{id}',

    -- Legacy user-log aliases
    '/report/user-log/list',
    '/report/user-log/find/{id}',

    -- Removed User Group controller legacy paths
    '/user-group/system/list',
    '/user-group/hospital/list',
    '/user-group/list',
    '/user-group/add',
    '/user-group/create',
    '/user-group/update',
    '/user-group/assign-users',
    '/user-group/delete/{id}',
    '/user-group/member-dropdown',
    '/user-group/user-group-role-list',
    '/user-group/user-group-role-find/{id}',
    '/user-group/user-group-role-add',
    '/user-group/user-group-role-update',
    '/user-group/user-group-role-delete/{id}'
);

