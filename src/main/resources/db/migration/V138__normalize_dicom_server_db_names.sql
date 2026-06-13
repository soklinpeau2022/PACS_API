DO $$
DECLARE
    old_prefix TEXT := 'udaya' || '_dicom_server_';
    old_callback_log_table TEXT := old_prefix || 'callback_log';
    old_ui_base_url_column TEXT := old_prefix || 'ui_base_url';
    old_worklist_id_column TEXT := old_prefix || 'worklist_id';
    old_worklist_path_column TEXT := old_prefix || 'worklist_path';
    old_study_id_column TEXT := old_prefix || 'study_id';
    old_patient_id_column TEXT := old_prefix || 'patient_id';
    old_series_id_column TEXT := old_prefix || 'series_id';
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = old_callback_log_table
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'dicom_server_callback_log'
    ) THEN
        EXECUTE FORMAT('ALTER TABLE %I RENAME TO %I', old_callback_log_table, 'dicom_server_callback_log');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'hospital_dicom_servers'
          AND column_name = old_ui_base_url_column
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'hospital_dicom_servers'
          AND column_name = 'dicom_server_ui_base_url'
    ) THEN
        EXECUTE FORMAT('ALTER TABLE %I RENAME COLUMN %I TO %I', 'hospital_dicom_servers', old_ui_base_url_column, 'dicom_server_ui_base_url');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_worklists'
          AND column_name = old_worklist_id_column
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_worklists'
          AND column_name = 'dicom_server_worklist_id'
    ) THEN
        EXECUTE FORMAT('ALTER TABLE %I RENAME COLUMN %I TO %I', 'pacs_worklists', old_worklist_id_column, 'dicom_server_worklist_id');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_worklists'
          AND column_name = old_worklist_path_column
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_worklists'
          AND column_name = 'dicom_server_worklist_path'
    ) THEN
        EXECUTE FORMAT('ALTER TABLE %I RENAME COLUMN %I TO %I', 'pacs_worklists', old_worklist_path_column, 'dicom_server_worklist_path');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_studies'
          AND column_name = old_study_id_column
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_studies'
          AND column_name = 'dicom_server_study_id'
    ) THEN
        EXECUTE FORMAT('ALTER TABLE %I RENAME COLUMN %I TO %I', 'pacs_studies', old_study_id_column, 'dicom_server_study_id');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_studies'
          AND column_name = old_patient_id_column
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_studies'
          AND column_name = 'dicom_server_patient_id'
    ) THEN
        EXECUTE FORMAT('ALTER TABLE %I RENAME COLUMN %I TO %I', 'pacs_studies', old_patient_id_column, 'dicom_server_patient_id');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_studies'
          AND column_name = old_series_id_column
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'pacs_studies'
          AND column_name = 'dicom_server_series_id'
    ) THEN
        EXECUTE FORMAT('ALTER TABLE %I RENAME COLUMN %I TO %I', 'pacs_studies', old_series_id_column, 'dicom_server_series_id');
    END IF;
END $$;

ALTER TABLE dicom_server_callback_log
    ADD COLUMN IF NOT EXISTS accession_number VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dicom_server_study_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dicom_server_patient_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dicom_server_series_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_dicom_server_callback_log_accession
    ON dicom_server_callback_log (accession_number);

CREATE INDEX IF NOT EXISTS idx_dicom_server_callback_log_study
    ON dicom_server_callback_log (dicom_server_study_id);

CREATE INDEX IF NOT EXISTS idx_dicom_server_callback_log_received_at
    ON dicom_server_callback_log (received_at DESC);
