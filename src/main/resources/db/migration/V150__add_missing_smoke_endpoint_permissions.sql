-- Keep QA/PROD deny-unknown endpoint security enabled while covering active UI/API endpoints.

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/user/user-group-list', 'role.view', 'user.read', 1),
    ('POST', '/dropdown/dropdown-dicom-server', 'dicom.server.view', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;
