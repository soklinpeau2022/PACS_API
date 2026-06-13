ALTER TABLE pacs_result_templates
    ADD COLUMN IF NOT EXISTS created_by BIGINT,
    ADD COLUMN IF NOT EXISTS modified_by BIGINT;

CREATE INDEX IF NOT EXISTS idx_pacs_result_templates_scope_active
    ON pacs_result_templates (hospital_id, modality_id, is_active, LOWER(template_name), id DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_result_templates_created_by'
    ) THEN
        ALTER TABLE pacs_result_templates
            ADD CONSTRAINT fk_pacs_result_templates_created_by
            FOREIGN KEY (created_by) REFERENCES users(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_result_templates_modified_by'
    ) THEN
        ALTER TABLE pacs_result_templates
            ADD CONSTRAINT fk_pacs_result_templates_modified_by
            FOREIGN KEY (modified_by) REFERENCES users(id);
    END IF;
END $$;

INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'pacs-result-template', 'PACS Result Templates', 6, 1, NOW()
FROM module_types mt
WHERE mt.code = 'HOSPITAL'
ON CONFLICT (code) DO UPDATE
SET module_type_id = EXCLUDED.module_type_id,
    name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW();

WITH desired_details(module_code, permission_code, permission_name, permission_type, action_key, display_order) AS (
    VALUES
        ('pacs-result-template', 'pacs.result.template.view', 'PACS Result Template (View)', 'VIEW', 'VIEW', 1),
        ('pacs-result-template', 'pacs.result.template.add', 'PACS Result Template (Add)', 'ADD', 'ADD', 2),
        ('pacs-result-template', 'pacs.result.template.edit', 'PACS Result Template (Edit)', 'EDIT', 'EDIT', 3),
        ('pacs-result-template', 'pacs.result.template.delete', 'PACS Result Template (Delete)', 'DELETE', 'DELETE', 4)
)
INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, d.permission_code, d.permission_name, d.permission_type, d.action_key, d.display_order, 1, NOW()
FROM desired_details d
JOIN modules m ON m.code = d.module_code
ON CONFLICT (code) DO UPDATE
SET module_id = EXCLUDED.module_id,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    action_key = EXCLUDED.action_key,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW();

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/pacs-result-template/pacs-result-template-list', 'pacs.result.template.view', 'pacs.api', 1),
    ('POST', '/pacs-result-template/pacs-result-template-find/*', 'pacs.result.template.view', 'pacs.api', 1),
    ('POST', '/pacs-result-template/pacs-result-template-create', 'pacs.result.template.add', 'pacs.api', 1),
    ('POST', '/pacs-result-template/pacs-result-template-update', 'pacs.result.template.edit', 'pacs.api', 1),
    ('POST', '/pacs-result-template/pacs-result-template-delete/*', 'pacs.result.template.delete', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT r.id, md.id, 1, NOW()
FROM roles r
JOIN module_details md ON md.code IN (
    'pacs.result.template.view',
    'pacs.result.template.add',
    'pacs.result.template.edit',
    'pacs.result.template.delete'
)
WHERE r.is_active = 1
  AND (
      UPPER(COALESCE(r.code, '')) = 'ADMIN'
      OR LOWER(TRIM(COALESCE(r.name, ''))) = 'admin'
  )
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;
