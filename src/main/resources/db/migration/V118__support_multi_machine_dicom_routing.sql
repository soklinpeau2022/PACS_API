ALTER TABLE hospital_modality_server_routes
    ADD COLUMN IF NOT EXISTS machine_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS machine_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS room_name VARCHAR(120);

UPDATE hospital_modality_server_routes
SET
    machine_code = COALESCE(
        NULLIF(BTRIM(machine_code), ''),
        NULLIF(REGEXP_REPLACE(UPPER(COALESCE(machine_ae_title, '')), '[^A-Z0-9]+', '_', 'g'), '') || '_' || id::text,
        'MACHINE_' || id::text
    ),
    machine_name = COALESCE(
        NULLIF(BTRIM(machine_name), ''),
        NULLIF(BTRIM(machine_ae_title), ''),
        'Machine ' || id::text
    )
WHERE is_active != 2;

ALTER TABLE hospital_modality_server_routes
    DROP CONSTRAINT IF EXISTS ux_hmsr_unique;

CREATE INDEX IF NOT EXISTS idx_hmsr_active_machine_ae
    ON hospital_modality_server_routes (hospital_id, LOWER(machine_ae_title))
    WHERE is_active = 1
      AND machine_ae_title IS NOT NULL
      AND BTRIM(machine_ae_title) != '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_hmsr_active_machine_code
    ON hospital_modality_server_routes (hospital_id, LOWER(machine_code))
    WHERE is_active = 1
      AND machine_code IS NOT NULL
      AND BTRIM(machine_code) != '';

CREATE INDEX IF NOT EXISTS idx_hmsr_hospital_modality_machine_active
    ON hospital_modality_server_routes (hospital_id, modality_id, is_active, machine_ae_title, dicom_server_id, id);

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES ('POST', '/worklist/worklist-machine-routes', 'pacs.worklist.send', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;
