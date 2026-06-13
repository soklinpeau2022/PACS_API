CREATE TABLE IF NOT EXISTS hospital_dicom_machines (
    id BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
    modality_id BIGINT NOT NULL,
    machine_code VARCHAR(80) NOT NULL,
    machine_name VARCHAR(160) NOT NULL,
    room_name VARCHAR(120),
    machine_ae_title VARCHAR(64) NOT NULL,
    machine_host VARCHAR(255) NOT NULL,
    machine_port INTEGER NOT NULL DEFAULT 104,
    is_active SMALLINT NOT NULL DEFAULT 1,
    created_by BIGINT REFERENCES users(id),
    modified_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_hdm_hospital_modality FOREIGN KEY (hospital_id, modality_id)
        REFERENCES hospital_modalities(hospital_id, modality_id),
    CONSTRAINT chk_hdm_machine_port CHECK (machine_port > 0 AND machine_port <= 65535),
    CONSTRAINT chk_hdm_is_active CHECK (is_active IN (1, 2))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_hdm_active_machine_code
    ON hospital_dicom_machines (hospital_id, LOWER(machine_code))
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_hdm_hospital_modality_active
    ON hospital_dicom_machines (hospital_id, modality_id, is_active, id DESC);

CREATE INDEX IF NOT EXISTS idx_hdm_machine_lookup
    ON hospital_dicom_machines (hospital_id, LOWER(machine_ae_title), LOWER(machine_host), machine_port)
    WHERE is_active = 1;

ALTER TABLE hospital_modality_server_routes
    ADD COLUMN IF NOT EXISTS machine_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_server_routes'
          AND constraint_name = 'fk_hmsr_machine'
    ) THEN
        ALTER TABLE hospital_modality_server_routes
            ADD CONSTRAINT fk_hmsr_machine
                FOREIGN KEY (machine_id) REFERENCES hospital_dicom_machines(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_hmsr_machine_active
    ON hospital_modality_server_routes (machine_id, is_active, hospital_id, modality_id, dicom_server_id);

WITH route_machine_seed AS (
    SELECT DISTINCT ON (r.hospital_id, LOWER(COALESCE(NULLIF(BTRIM(r.machine_code), ''), 'MACHINE_' || r.id::text)))
        r.hospital_id,
        r.modality_id,
        COALESCE(NULLIF(BTRIM(r.machine_code), ''), 'MACHINE_' || r.id::text) AS machine_code,
        COALESCE(NULLIF(BTRIM(r.machine_name), ''), NULLIF(BTRIM(r.machine_ae_title), ''), 'Machine ' || r.id::text) AS machine_name,
        NULLIF(BTRIM(r.room_name), '') AS room_name,
        COALESCE(NULLIF(BTRIM(r.machine_ae_title), ''), COALESCE(NULLIF(BTRIM(r.machine_code), ''), 'MACHINE_' || r.id::text)) AS machine_ae_title,
        COALESCE(NULLIF(BTRIM(r.machine_host), ''), '127.0.0.1') AS machine_host,
        COALESCE(r.machine_port, 104) AS machine_port,
        r.created_by,
        r.modified_by,
        r.created_at,
        r.modified_at
    FROM hospital_modality_server_routes r
    WHERE r.is_active != 2
    ORDER BY r.hospital_id,
             LOWER(COALESCE(NULLIF(BTRIM(r.machine_code), ''), 'MACHINE_' || r.id::text)),
             r.is_active ASC,
             r.id ASC
)
INSERT INTO hospital_dicom_machines (
    hospital_id,
    modality_id,
    machine_code,
    machine_name,
    room_name,
    machine_ae_title,
    machine_host,
    machine_port,
    is_active,
    created_by,
    modified_by,
    created_at,
    modified_at
)
SELECT
    seed.hospital_id,
    seed.modality_id,
    seed.machine_code,
    seed.machine_name,
    seed.room_name,
    seed.machine_ae_title,
    seed.machine_host,
    seed.machine_port,
    1,
    seed.created_by,
    seed.modified_by,
    seed.created_at,
    seed.modified_at
FROM route_machine_seed seed
WHERE NOT EXISTS (
    SELECT 1
    FROM hospital_dicom_machines machine
    WHERE machine.hospital_id = seed.hospital_id
      AND LOWER(machine.machine_code) = LOWER(seed.machine_code)
      AND machine.is_active = 1
);

UPDATE hospital_modality_server_routes route
SET machine_id = machine.id
FROM hospital_dicom_machines machine
WHERE route.machine_id IS NULL
  AND route.hospital_id = machine.hospital_id
  AND LOWER(COALESCE(NULLIF(BTRIM(route.machine_code), ''), 'MACHINE_' || route.id::text)) = LOWER(machine.machine_code)
  AND machine.is_active = 1;

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/dicom-routing/dicom-machine-list', 'pacs.dicom-routing.view', 'pacs.api', 1),
    ('POST', '/dicom-routing/dicom-machine-find/{id}', 'pacs.dicom-routing.view', 'pacs.api', 1),
    ('POST', '/dicom-routing/dicom-machine-create', 'pacs.dicom-routing.manage', 'pacs.api', 1),
    ('POST', '/dicom-routing/dicom-machine-update', 'pacs.dicom-routing.manage', 'pacs.api', 1),
    ('POST', '/dicom-routing/dicom-machine-delete/{id}', 'pacs.dicom-routing.manage', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;

COMMENT ON TABLE hospital_dicom_machines IS 'Reusable physical modality machines/rooms per hospital. Routing links to these records by machine_id.';
COMMENT ON COLUMN hospital_modality_server_routes.machine_id IS 'Source machine relation. Legacy route machine columns are retained as denormalized snapshots for compatibility.';
