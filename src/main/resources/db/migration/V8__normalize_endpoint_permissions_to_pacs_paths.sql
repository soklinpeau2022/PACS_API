-- Keep endpoint permission mapping aligned with canonical PACS endpoint paths.
-- Remove legacy aliases that are no longer exposed by controllers.
DELETE FROM endpoint_permissions
WHERE endpoint_pattern IN (
    '/user/list','/user/create','/user/update','/user/delete/*',
    '/user/user-add','/user/user-create','/user/user-update','/user/user-delete/*',
    '/role/list','/role/create','/role/update','/role/delete/*','/role/menu',
    '/role/role-list','/role/role-create','/role/role-update','/role/role-delete/*',
    '/permission/tree','/permission/save-role-permissions',
    '/permission/permission-tree','/permission/permission-save-role-permissions',
    '/hospital/list','/hospital/find/*','/hospital/create','/hospital/update',
    '/patient/list','/patient/find/*','/patient/create','/patient/update',
    '/queue/list','/queue/return','/queue/cancel','/queue/complete',
    '/study/list','/study/find/*','/study/assign',
    '/viewer/open'
);

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code)
VALUES
    ('POST', '/user/user-list', 'user.view'),
    ('POST', '/user/user-find/*', 'user.view'),
    ('POST', '/user/user-create', 'user.add'),
    ('POST', '/user/user-update', 'user.edit'),
    ('POST', '/user/user-delete/*', 'user.delete'),

    ('POST', '/role/role-list', 'role.view'),
    ('POST', '/role/role-find/*', 'role.view'),
    ('POST', '/role/role-create', 'role.add'),
    ('POST', '/role/role-update', 'role.edit'),
    ('POST', '/role/role-delete/*', 'role.delete'),

    ('POST', '/permission/permission-tree', 'role.assign_permission'),
    ('POST', '/permission/permission-save-role-permissions', 'role.assign_permission'),
    ('POST', '/module-type-list', 'role.assign_permission'),
    ('POST', '/module-list', 'role.assign_permission'),
    ('POST', '/module-detail-list', 'role.assign_permission'),

    ('POST', '/hospital/hospital-list', 'hospital.view'),
    ('POST', '/hospital/hospital-find/*', 'hospital.view'),
    ('POST', '/hospital/hospital-create', 'hospital.add'),
    ('POST', '/hospital/hospital-update', 'hospital.edit'),

    ('POST', '/patient/patient-list', 'pacs.patient.view'),
    ('POST', '/patient/patient-find/*', 'pacs.patient.view'),
    ('POST', '/patient/patient-create', 'pacs.patient.create'),
    ('POST', '/patient/patient-update', 'pacs.patient.edit'),

    ('POST', '/queue/queue-list', 'pacs.queue.view'),
    ('POST', '/queue/queue-return', 'pacs.queue.return'),
    ('POST', '/queue/queue-cancel', 'pacs.queue.cancel'),
    ('POST', '/queue/queue-complete', 'pacs.queue.complete'),

    ('POST', '/study/study-list', 'pacs.study.view'),
    ('POST', '/study/study-find/*', 'pacs.study.view'),
    ('POST', '/study/study-assign', 'pacs.study.assign'),

    ('POST', '/viewer/viewer-open', 'pacs.viewer.open')
ON CONFLICT (http_method, endpoint_pattern, permission_code) DO NOTHING;
