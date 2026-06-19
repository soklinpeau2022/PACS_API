INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/dicom-uploads/chunk/init', 'pacs.study.upload', 'pacs.study.read', 1),
    ('POST', '/dicom-uploads/chunk/*', 'pacs.study.upload', 'pacs.study.read', 1),
    ('GET', '/dicom-uploads/chunk/*/status', 'pacs.study.upload', 'pacs.study.read', 1),
    ('POST', '/dicom-uploads/chunk/*/complete', 'pacs.study.upload', 'pacs.study.read', 1),
    ('DELETE', '/dicom-uploads/chunk/*/abort', 'pacs.study.upload', 'pacs.study.read', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;
