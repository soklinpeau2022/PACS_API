INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, is_active)
VALUES
    ('POST', '/dropdown/dropdown-service-by-modality', 'service.view', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code) DO UPDATE
SET is_active = EXCLUDED.is_active;

