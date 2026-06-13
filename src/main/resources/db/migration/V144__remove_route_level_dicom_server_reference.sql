-- Routing configuration owns the destination DICOM server.
-- Route rows are child links for modality + machine only.

UPDATE hospital_modality_server_routes route
SET is_active = 2,
    modified_at = NOW()
WHERE route.is_active = 1
  AND NOT EXISTS (
      SELECT 1
      FROM hospital_dicom_routing_configs cfg
      WHERE cfg.id = route.routing_config_id
        AND cfg.hospital_id = route.hospital_id
        AND cfg.dicom_server_id IS NOT NULL
        AND cfg.is_active = 1
  );

CREATE UNIQUE INDEX IF NOT EXISTS ux_hdrc_id_hospital
    ON hospital_dicom_routing_configs (id, hospital_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_server_routes'
          AND constraint_name = 'fk_hmsr_routing_config_hospital'
    ) THEN
        ALTER TABLE hospital_modality_server_routes
            ADD CONSTRAINT fk_hmsr_routing_config_hospital
                FOREIGN KEY (routing_config_id, hospital_id)
                REFERENCES hospital_dicom_routing_configs(id, hospital_id)
                ON UPDATE RESTRICT
                ON DELETE RESTRICT
                NOT VALID;
    END IF;
END $$;

DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.hospital_modality_server_routes'::regclass
          AND pg_get_constraintdef(oid) ILIKE '%dicom_server_id%'
    LOOP
        EXECUTE FORMAT(
            'ALTER TABLE public.hospital_modality_server_routes DROP CONSTRAINT IF EXISTS %I',
            constraint_record.conname
        );
    END LOOP;
END $$;

DO $$
DECLARE
    index_record RECORD;
BEGIN
    FOR index_record IN
        SELECT indexname
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'hospital_modality_server_routes'
          AND indexdef ILIKE '%dicom_server_id%'
    LOOP
        EXECUTE FORMAT('DROP INDEX IF EXISTS public.%I', index_record.indexname);
    END LOOP;
END $$;

ALTER TABLE hospital_modality_server_routes
    DROP COLUMN IF EXISTS dicom_server_id;

WITH ranked_routes AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY routing_config_id, machine_id
            ORDER BY modified_at DESC NULLS LAST, id DESC
        ) AS row_number
    FROM hospital_modality_server_routes
    WHERE is_active = 1
      AND routing_config_id IS NOT NULL
      AND machine_id IS NOT NULL
)
UPDATE hospital_modality_server_routes route
SET is_active = 2,
    modified_at = NOW()
FROM ranked_routes
WHERE route.id = ranked_routes.id
  AND ranked_routes.row_number > 1;

CREATE INDEX IF NOT EXISTS idx_hmsr_config_active_modality_machine
    ON hospital_modality_server_routes (routing_config_id, is_active, modality_id, machine_id, id);

CREATE INDEX IF NOT EXISTS idx_hmsr_hospital_modality_active_machine
    ON hospital_modality_server_routes (hospital_id, modality_id, is_active, machine_id, id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_hmsr_config_machine_active
    ON hospital_modality_server_routes (routing_config_id, machine_id)
    WHERE is_active = 1
      AND routing_config_id IS NOT NULL;

COMMENT ON COLUMN hospital_modality_server_routes.routing_config_id IS
    'Parent routing configuration. The parent owns the destination DICOM server.';
