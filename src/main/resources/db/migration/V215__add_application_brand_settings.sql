INSERT INTO pacs_system_settings (setting_key, setting_value, modified_at)
VALUES
    ('application.brand.app_name', 'UDAYA_PACS_FRONTEND', NOW()),
    ('application.brand.logo_url', '/utemr-logo.png', NOW()),
    ('application.brand.login_background_url', '/banner-login.webp', NOW())
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/application-settings/application-brand-settings-update', 'role.edit', 'pacs.api', 1),
    ('POST', '/application-settings/application-brand-logo-upload', 'file.add', 'pacs.api', 1),
    ('POST', '/application-settings/application-login-background-upload', 'file.add', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;
