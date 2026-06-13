INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES ('GET', '/queue/*/viewer-info', 'pacs.queue.view_study', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;
