-- Ensure queue.dicom_server_id always belongs to the same hospital as queue.hospital_id.
-- This protects relationship integrity under high concurrency and prevents cross-hospital routing mistakes.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'hospital_dicom_servers'
    ) THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS ux_hds_hospital_id_id
                 ON hospital_dicom_servers (hospital_id, id)';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'pacs_patient_queue'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_patient_queue'
          AND column_name = 'dicom_server_id'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'hospital_dicom_servers'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'pacs_patient_queue'
          AND constraint_name = 'fk_queue_hospital_dicom_server'
    ) THEN
        ALTER TABLE pacs_patient_queue
            ADD CONSTRAINT fk_queue_hospital_dicom_server
            FOREIGN KEY (hospital_id, dicom_server_id)
            REFERENCES hospital_dicom_servers (hospital_id, id)
            NOT VALID;
    END IF;
END $$;

-- Validate separately to avoid long lock during add-constraint phase.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'pacs_patient_queue'
          AND constraint_name = 'fk_queue_hospital_dicom_server'
    ) THEN
        ALTER TABLE pacs_patient_queue
            VALIDATE CONSTRAINT fk_queue_hospital_dicom_server;
    END IF;
END $$;
