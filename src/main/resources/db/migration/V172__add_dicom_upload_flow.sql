ALTER TABLE pacs_studies
    ADD COLUMN IF NOT EXISTS reference_visit_code VARCHAR(120),
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS uploaded_by BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS image_received_at TIMESTAMPTZ;

ALTER TABLE pacs_studies
    DROP CONSTRAINT IF EXISTS pacs_studies_study_instance_uid_key;

DROP INDEX IF EXISTS pacs_studies_study_instance_uid_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_studies_hospital_study_instance_uid
    ON pacs_studies (hospital_id, study_instance_uid);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_studies_source_type') THEN
        ALTER TABLE pacs_studies
            ADD CONSTRAINT chk_pacs_studies_source_type
                CHECK (source_type IS NULL OR source_type IN ('WORKLIST', 'UPLOAD', 'MANUAL'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_study_uid_active
    ON pacs_studies (hospital_id, study_instance_uid)
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_reference_visit
    ON pacs_studies (hospital_id, reference_visit_code)
    WHERE is_active = 1 AND reference_visit_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_uploaded_source
    ON pacs_studies (hospital_id, source_type, image_received_at DESC)
    WHERE is_active = 1;

INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, 'pacs.study.upload', 'Study (DICOM Upload)', 'ADD', 'UPLOAD', 2, 1, NOW()
FROM modules m
WHERE m.code = 'pacs-study'
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
    ('POST', '/dicom-uploads', 'pacs.study.upload', 'pacs.study.read', 1),
    ('GET', '/study/*/viewer-info', 'pacs.study.view', 'pacs.study.read', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT r.id, md.id, 1, NOW()
FROM roles r
CROSS JOIN module_details md
WHERE md.code = 'pacs.study.upload'
  AND (
      UPPER(COALESCE(r.code, '')) LIKE '%ADMIN%'
      OR
      UPPER(COALESCE(r.name, '')) LIKE '%ADMIN%'
  )
ON CONFLICT (role_id, module_detail_id) DO NOTHING;
