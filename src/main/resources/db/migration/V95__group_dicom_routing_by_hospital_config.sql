CREATE TABLE IF NOT EXISTS hospital_dicom_routing_configs (
    id BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
    is_active SMALLINT NOT NULL DEFAULT 1,
    created_by BIGINT REFERENCES users(id),
    modified_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_hdrc_hospital_active
    ON hospital_dicom_routing_configs (hospital_id)
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_hdrc_hospital_active
    ON hospital_dicom_routing_configs (hospital_id, is_active, id DESC);

ALTER TABLE hospital_modality_server_routes
    ADD COLUMN IF NOT EXISTS routing_config_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_server_routes'
          AND constraint_name = 'fk_hmsr_routing_config'
    ) THEN
        ALTER TABLE hospital_modality_server_routes
            ADD CONSTRAINT fk_hmsr_routing_config
                FOREIGN KEY (routing_config_id) REFERENCES hospital_dicom_routing_configs(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_hmsr_routing_config_active
    ON hospital_modality_server_routes (routing_config_id, is_active, hospital_id, modality_id, dicom_server_id);

INSERT INTO hospital_dicom_routing_configs (
    hospital_id,
    is_active,
    created_by,
    modified_by,
    created_at,
    modified_at
)
SELECT
    route_seed.hospital_id,
    1,
    route_seed.created_by,
    route_seed.modified_by,
    route_seed.created_at,
    route_seed.modified_at
FROM (
    SELECT
        r.hospital_id,
        MIN(r.created_by) AS created_by,
        MAX(r.modified_by) AS modified_by,
        MIN(r.created_at) AS created_at,
        MAX(r.modified_at) AS modified_at
    FROM hospital_modality_server_routes r
    WHERE r.is_active != 2
    GROUP BY r.hospital_id
) route_seed
WHERE NOT EXISTS (
    SELECT 1
    FROM hospital_dicom_routing_configs cfg
    WHERE cfg.hospital_id = route_seed.hospital_id
      AND cfg.is_active = 1
);

UPDATE hospital_modality_server_routes r
SET routing_config_id = cfg.id
FROM hospital_dicom_routing_configs cfg
WHERE r.routing_config_id IS NULL
  AND r.hospital_id = cfg.hospital_id
  AND cfg.is_active = 1;
