ALTER TABLE hospital_modality_server_routes
    ADD COLUMN IF NOT EXISTS machine_ae_title VARCHAR(64),
    ADD COLUMN IF NOT EXISTS machine_host VARCHAR(255),
    ADD COLUMN IF NOT EXISTS machine_port INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_server_routes'
          AND constraint_name = 'chk_hmsr_machine_port_positive'
    ) THEN
        ALTER TABLE hospital_modality_server_routes
            ADD CONSTRAINT chk_hmsr_machine_port_positive
                CHECK (machine_port IS NULL OR (machine_port > 0 AND machine_port <= 65535));
    END IF;
END $$;

COMMENT ON COLUMN hospital_modality_server_routes.machine_ae_title IS 'Remote modality machine AE title used in DICOM server DicomModalities, e.g. PRIME.';
COMMENT ON COLUMN hospital_modality_server_routes.machine_host IS 'Remote modality machine host/IP used in DICOM server DicomModalities.';
COMMENT ON COLUMN hospital_modality_server_routes.machine_port IS 'Remote modality machine DICOM port used in DICOM server DicomModalities.';