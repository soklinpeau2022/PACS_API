-- Strengthen DICOM setup integrity after normalizing machines/routes/worklists.
-- These constraints prevent new rows from drifting across hospitals/modalities
-- while preserving existing production data that may need manual cleanup.

UPDATE hospital_dicom_servers
SET is_active = CASE WHEN is_active = 1 THEN 1 ELSE 2 END
WHERE is_active NOT IN (1, 2);

UPDATE hospital_modality_server_routes
SET is_active = CASE WHEN is_active = 1 THEN 1 ELSE 2 END
WHERE is_active NOT IN (1, 2);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_dicom_servers'
          AND constraint_name = 'chk_hds_is_active'
    ) THEN
        ALTER TABLE hospital_dicom_servers
            ADD CONSTRAINT chk_hds_is_active
                CHECK (is_active IN (1, 2));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_server_routes'
          AND constraint_name = 'chk_hmsr_is_active'
    ) THEN
        ALTER TABLE hospital_modality_server_routes
            ADD CONSTRAINT chk_hmsr_is_active
                CHECK (is_active IN (1, 2));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_hmsr_id_hospital_modality
    ON hospital_modality_server_routes (id, hospital_id, modality_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_hds_id_hospital
    ON hospital_dicom_servers (id, hospital_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_server_routes'
          AND constraint_name = 'fk_hmsr_server_hospital'
    ) THEN
        ALTER TABLE hospital_modality_server_routes
            ADD CONSTRAINT fk_hmsr_server_hospital
                FOREIGN KEY (dicom_server_id, hospital_id)
                REFERENCES hospital_dicom_servers(id, hospital_id)
                ON UPDATE RESTRICT
                ON DELETE RESTRICT
                NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'pacs_worklists'
          AND constraint_name = 'fk_pacs_worklists_route_hospital_modality'
    ) THEN
        ALTER TABLE pacs_worklists
            ADD CONSTRAINT fk_pacs_worklists_route_hospital_modality
                FOREIGN KEY (dicom_route_id, hospital_id, modality_id)
                REFERENCES hospital_modality_server_routes(id, hospital_id, modality_id)
                ON UPDATE RESTRICT
                ON DELETE RESTRICT
                NOT VALID;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_route_hospital_modality
    ON pacs_worklists (dicom_route_id, hospital_id, modality_id)
    WHERE dicom_route_id IS NOT NULL;

COMMENT ON CONSTRAINT fk_hmsr_server_hospital ON hospital_modality_server_routes
    IS 'New routes must use a DICOM server from the same hospital.';

COMMENT ON CONSTRAINT fk_pacs_worklists_route_hospital_modality ON pacs_worklists
    IS 'New worklists must reference a route for the same hospital and modality.';
