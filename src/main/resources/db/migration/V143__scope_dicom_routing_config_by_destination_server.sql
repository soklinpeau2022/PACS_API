ALTER TABLE hospital_dicom_routing_configs
    ADD COLUMN IF NOT EXISTS dicom_server_id BIGINT;

DROP INDEX IF EXISTS ux_hdrc_hospital_active;

WITH first_server AS (
    SELECT
        cfg.id AS routing_config_id,
        MIN(route.dicom_server_id) AS dicom_server_id
    FROM hospital_dicom_routing_configs cfg
    INNER JOIN hospital_modality_server_routes route
            ON route.routing_config_id = cfg.id
           AND route.hospital_id = cfg.hospital_id
           AND route.is_active = 1
           AND route.dicom_server_id IS NOT NULL
    WHERE cfg.dicom_server_id IS NULL
    GROUP BY cfg.id
)
UPDATE hospital_dicom_routing_configs cfg
SET dicom_server_id = first_server.dicom_server_id,
    modified_at = NOW()
FROM first_server
WHERE cfg.id = first_server.routing_config_id
  AND cfg.dicom_server_id IS NULL;

WITH route_servers AS (
    SELECT
        route.hospital_id,
        route.dicom_server_id,
        COALESCE(MIN(cfg.created_by), MIN(route.created_by)) AS created_by,
        COALESCE(MAX(cfg.modified_by), MAX(route.modified_by)) AS modified_by,
        COALESCE(MIN(cfg.created_at), MIN(route.created_at), NOW()) AS created_at,
        COALESCE(MAX(cfg.modified_at), MAX(route.modified_at), NOW()) AS modified_at
    FROM hospital_modality_server_routes route
    LEFT JOIN hospital_dicom_routing_configs cfg ON cfg.id = route.routing_config_id
    WHERE route.is_active = 1
      AND route.dicom_server_id IS NOT NULL
    GROUP BY route.hospital_id, route.dicom_server_id
)
INSERT INTO hospital_dicom_routing_configs (
    hospital_id,
    dicom_server_id,
    is_active,
    created_by,
    modified_by,
    created_at,
    modified_at
)
SELECT
    route_servers.hospital_id,
    route_servers.dicom_server_id,
    1,
    route_servers.created_by,
    route_servers.modified_by,
    route_servers.created_at,
    route_servers.modified_at
FROM route_servers
WHERE NOT EXISTS (
    SELECT 1
    FROM hospital_dicom_routing_configs cfg
    WHERE cfg.hospital_id = route_servers.hospital_id
      AND cfg.dicom_server_id = route_servers.dicom_server_id
      AND cfg.is_active = 1
);

UPDATE hospital_modality_server_routes route
SET routing_config_id = cfg.id,
    modified_at = NOW()
FROM hospital_dicom_routing_configs cfg
WHERE route.is_active = 1
  AND cfg.is_active = 1
  AND cfg.hospital_id = route.hospital_id
  AND cfg.dicom_server_id = route.dicom_server_id
  AND route.routing_config_id IS DISTINCT FROM cfg.id;

WITH ranked AS (
    SELECT
        id,
        hospital_id,
        dicom_server_id,
        ROW_NUMBER() OVER (
            PARTITION BY hospital_id, dicom_server_id
            ORDER BY modified_at DESC NULLS LAST, id DESC
        ) AS row_number
    FROM hospital_dicom_routing_configs
    WHERE is_active = 1
      AND dicom_server_id IS NOT NULL
),
kept AS (
    SELECT hospital_id, dicom_server_id, id
    FROM ranked
    WHERE row_number = 1
),
duplicate_configs AS (
    SELECT ranked.id, ranked.hospital_id, ranked.dicom_server_id, kept.id AS kept_id
    FROM ranked
    INNER JOIN kept
            ON kept.hospital_id = ranked.hospital_id
           AND kept.dicom_server_id = ranked.dicom_server_id
    WHERE ranked.row_number > 1
)
UPDATE hospital_modality_server_routes route
SET routing_config_id = duplicate_configs.kept_id,
    modified_at = NOW()
FROM duplicate_configs
WHERE route.routing_config_id = duplicate_configs.id;

WITH ranked AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY hospital_id, dicom_server_id
            ORDER BY modified_at DESC NULLS LAST, id DESC
        ) AS row_number
    FROM hospital_dicom_routing_configs
    WHERE is_active = 1
      AND dicom_server_id IS NOT NULL
)
UPDATE hospital_dicom_routing_configs cfg
SET is_active = 2,
    modified_at = NOW()
FROM ranked
WHERE cfg.id = ranked.id
  AND ranked.row_number > 1;

UPDATE hospital_dicom_routing_configs
SET is_active = 2,
    modified_at = NOW()
WHERE is_active = 1
  AND dicom_server_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_hds_id_hospital
    ON hospital_dicom_servers (id, hospital_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_dicom_routing_configs'
          AND constraint_name = 'fk_hdrc_dicom_server_hospital'
    ) THEN
        ALTER TABLE hospital_dicom_routing_configs
            ADD CONSTRAINT fk_hdrc_dicom_server_hospital
                FOREIGN KEY (dicom_server_id, hospital_id)
                REFERENCES hospital_dicom_servers(id, hospital_id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_hdrc_hospital_server_active
    ON hospital_dicom_routing_configs (hospital_id, dicom_server_id)
    WHERE is_active = 1
      AND dicom_server_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_hdrc_hospital_server_active
    ON hospital_dicom_routing_configs (hospital_id, dicom_server_id, is_active, id DESC);

COMMENT ON COLUMN hospital_dicom_routing_configs.dicom_server_id IS
    'Destination DICOM server for this routing configuration. Route rows inherit this server.';
