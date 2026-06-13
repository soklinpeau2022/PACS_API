ALTER TABLE endpoint_permissions
    ADD COLUMN IF NOT EXISTS required_scope VARCHAR(150);

-- PACS permissions
UPDATE endpoint_permissions
SET required_scope = 'pacs.patient.read'
WHERE permission_code IN ('pacs.patient.view')
  AND (required_scope IS NULL OR required_scope = '');

UPDATE endpoint_permissions
SET required_scope = 'pacs.patient.write'
WHERE permission_code IN ('pacs.patient.create', 'pacs.patient.edit')
  AND (required_scope IS NULL OR required_scope = '');

UPDATE endpoint_permissions
SET required_scope = 'pacs.study.read'
WHERE permission_code IN ('pacs.study.view', 'pacs.study.assign')
  AND (required_scope IS NULL OR required_scope = '');

UPDATE endpoint_permissions
SET required_scope = 'pacs.viewer.open'
WHERE permission_code = 'pacs.viewer.open'
  AND (required_scope IS NULL OR required_scope = '');

UPDATE endpoint_permissions
SET required_scope = 'pacs.api'
WHERE permission_code IN ('pacs.queue.view', 'pacs.queue.return', 'pacs.queue.cancel', 'pacs.queue.complete', 'hospital.view')
  AND (required_scope IS NULL OR required_scope = '');

-- User/role administration permissions
UPDATE endpoint_permissions
SET required_scope = 'user.read'
WHERE permission_code IN ('user.view', 'role.view')
  AND (required_scope IS NULL OR required_scope = '');

UPDATE endpoint_permissions
SET required_scope = 'user.write'
WHERE permission_code IN ('user.add', 'user.edit', 'user.delete', 'role.add', 'role.edit', 'role.delete', 'role.assign_permission', 'hospital.add', 'hospital.edit')
  AND (required_scope IS NULL OR required_scope = '');

-- Safe default for any unmapped active endpoint permission
UPDATE endpoint_permissions
SET required_scope = 'pacs.api'
WHERE is_active = 1
  AND (required_scope IS NULL OR required_scope = '');

CREATE INDEX IF NOT EXISTS idx_endpoint_permissions_required_scope
    ON endpoint_permissions(required_scope);
