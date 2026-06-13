INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, is_active)
VALUES
    ('POST', '/modality/modality-list', 'modality.view', 1),
    ('POST', '/modality/modality-find/*', 'modality.view', 1),
    ('POST', '/modality/modality-create', 'modality.add', 1),
    ('POST', '/modality/modality-update', 'modality.edit', 1),
    ('POST', '/modality/modality-delete/*', 'modality.delete', 1),
    ('POST', '/hospital-modality', 'hospital.modality.view', 1),
    ('POST', '/dropdown/dropdown-modality', 'modality.view', 1),
    ('POST', '/dropdown/dropdown-service-by-modality', 'service.view', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code) DO UPDATE
SET is_active = 1;
