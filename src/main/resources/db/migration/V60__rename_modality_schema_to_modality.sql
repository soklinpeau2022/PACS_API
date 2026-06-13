DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'modulights')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'modalities') THEN
        ALTER TABLE modulights RENAME TO modalities;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'hospital_modulights')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'hospital_modalities') THEN
        ALTER TABLE hospital_modulights RENAME TO hospital_modalities;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'hospital_modulight_services')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'hospital_modality_services') THEN
        ALTER TABLE hospital_modulight_services RENAME TO hospital_modality_services;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'hospital_modulight_server_routes')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'hospital_modality_server_routes') THEN
        ALTER TABLE hospital_modulight_server_routes RENAME TO hospital_modality_server_routes;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'hospital_modalities' AND column_name = 'modulight_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'hospital_modalities' AND column_name = 'modality_id'
    ) THEN
        ALTER TABLE hospital_modalities RENAME COLUMN modulight_id TO modality_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'hospital_modality_services' AND column_name = 'modulight_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'hospital_modality_services' AND column_name = 'modality_id'
    ) THEN
        ALTER TABLE hospital_modality_services RENAME COLUMN modulight_id TO modality_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'hospital_modality_server_routes' AND column_name = 'modulight_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'hospital_modality_server_routes' AND column_name = 'modality_id'
    ) THEN
        ALTER TABLE hospital_modality_server_routes RENAME COLUMN modulight_id TO modality_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'pacs_patient_queue' AND column_name = 'modulight_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'pacs_patient_queue' AND column_name = 'modality_id'
    ) THEN
        ALTER TABLE pacs_patient_queue RENAME COLUMN modulight_id TO modality_id;
    END IF;
END $$;

ALTER INDEX IF EXISTS ux_modulights_name_active RENAME TO ux_modalities_name_active;
ALTER INDEX IF EXISTS idx_modulights_active_id_desc RENAME TO idx_modalities_active_id_desc;
ALTER INDEX IF EXISTS idx_modulights_created_by RENAME TO idx_modalities_created_by;
ALTER INDEX IF EXISTS idx_modulights_modified_by RENAME TO idx_modalities_modified_by;
ALTER INDEX IF EXISTS ux_modulights_abbr_active RENAME TO ux_modalities_abbr_active;

ALTER INDEX IF EXISTS idx_hospital_modulights_hospital_active RENAME TO idx_hospital_modalities_hospital_active;
ALTER INDEX IF EXISTS idx_hospital_modulights_modulight_active RENAME TO idx_hospital_modalities_modality_active;

ALTER INDEX IF EXISTS idx_hms_hospital_modulight_active RENAME TO idx_hms_hospital_modality_active;

ALTER INDEX IF EXISTS idx_pacs_patient_queue_hospital_modulight_status_created RENAME TO idx_pacs_patient_queue_hospital_modality_status_created;
ALTER INDEX IF EXISTS idx_queue_hospital_modulight_id_desc RENAME TO idx_queue_hospital_modality_id_desc;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_services'
          AND constraint_name = 'ux_hospital_modulight_services'
    ) THEN
        ALTER TABLE hospital_modality_services RENAME CONSTRAINT ux_hospital_modulight_services TO ux_hospital_modality_services;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_server_routes'
          AND constraint_name = 'fk_hmsr_hospital_modulight'
    ) THEN
        ALTER TABLE hospital_modality_server_routes RENAME CONSTRAINT fk_hmsr_hospital_modulight TO fk_hmsr_hospital_modality;
    END IF;
END $$;
