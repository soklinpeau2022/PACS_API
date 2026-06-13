CREATE TABLE IF NOT EXISTS pacs_system_settings (
    setting_key VARCHAR(160) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    modified_by BIGINT,
    modified_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE pacs_system_settings IS 'Small API runtime settings that can change without rebuilding containers.';
COMMENT ON COLUMN pacs_system_settings.setting_key IS 'Stable system setting key.';
COMMENT ON COLUMN pacs_system_settings.setting_value IS 'Setting value stored as text and parsed by the owning service.';

INSERT INTO pacs_system_settings (setting_key, setting_value, modified_at)
VALUES
    ('dicom.server.health.enabled', 'true', NOW()),
    ('dicom.server.health.poll_interval_seconds', '5', NOW())
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/dicom-server/dicom-server-health-summary', 'dicom.server.view', 'pacs.api', 1),
    ('POST', '/dicom-server/dicom-server-health-settings-get', 'dicom.server.view', 'pacs.api', 1),
    ('POST', '/dicom-server/dicom-server-health-settings-update', 'dicom.server.edit', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;
