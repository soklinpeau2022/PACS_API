DO $$
DECLARE
    route_table_name text;
    route_mod_col text;
    queue_mod_col text;
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS pg_trgm;
    EXCEPTION
        WHEN insufficient_privilege THEN
            RAISE NOTICE 'Skipping pg_trgm extension creation due to insufficient privilege.';
    END;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'hospital_modality_server_routes'
    ) THEN
        route_table_name := 'hospital_modality_server_routes';
        route_mod_col := 'modality_id';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'hospital_modulight_server_routes'
    ) THEN
        route_table_name := 'hospital_modulight_server_routes';
        route_mod_col := 'modulight_id';
    ELSE
        route_table_name := NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_patient_queue'
          AND column_name = 'modality_id'
    ) THEN
        queue_mod_col := 'modality_id';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_patient_queue'
          AND column_name = 'modulight_id'
    ) THEN
        queue_mod_col := 'modulight_id';
    ELSE
        queue_mod_col := NULL;
    END IF;

    -- Fast list/pagination path for DICOM server list
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'hospital_dicom_servers'
    ) THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_hds_hospital_active_id_desc
                 ON hospital_dicom_servers (hospital_id, is_active, id DESC)';
    END IF;

    -- Fast route list and lookup paths
    IF route_table_name IS NOT NULL THEN
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_hmsr_hospital_active_id_desc
             ON %I (hospital_id, is_active, id DESC)',
            route_table_name
        );

        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_hmsr_hospital_modality_default_priority
             ON %I (hospital_id, %I, is_active, is_default, priority, id)',
            route_table_name, route_mod_col
        );

        -- Keep only one active default route per hospital+modality/modulight.
        EXECUTE format(
            'CREATE UNIQUE INDEX IF NOT EXISTS ux_hmsr_one_default_active
             ON %I (hospital_id, %I)
             WHERE is_active = 1 AND is_default = 1',
            route_table_name, route_mod_col
        );
    END IF;

    -- Queue list/flow paths with modality/modulight and dicom server
    IF queue_mod_col IS NOT NULL THEN
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_queue_hospital_modality_status_id_desc
             ON pacs_patient_queue (hospital_id, %I, status, id DESC)',
            queue_mod_col
        );
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_patient_queue'
          AND column_name = 'dicom_server_id'
    ) THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_queue_hospital_dicom_server_status_id_desc
                 ON pacs_patient_queue (hospital_id, dicom_server_id, status, id DESC)';
    END IF;

    -- Optional trigram indexes for large text-search workloads.
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm')
       AND EXISTS (
           SELECT 1 FROM information_schema.tables
           WHERE table_schema = 'public' AND table_name = 'hospital_dicom_servers'
       ) THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_hds_name_trgm ON hospital_dicom_servers USING gin (LOWER(name) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_hds_ip_trgm ON hospital_dicom_servers USING gin (LOWER(ip_address) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_hds_ae_trgm ON hospital_dicom_servers USING gin (LOWER(COALESCE(ae_title, '''')) gin_trgm_ops)';
    END IF;
END $$;
