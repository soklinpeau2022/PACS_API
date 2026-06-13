DO $$
DECLARE
    old_callback_log_sequence TEXT := 'ort' || 'hanc_callback_log_id_seq';
    old_callback_log_pkey TEXT := 'ort' || 'hanc_callback_log_pkey';
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relkind = 'S'
          AND relname = old_callback_log_sequence
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relkind = 'S'
          AND relname = 'dicom_server_callback_log_id_seq'
    ) THEN
        EXECUTE FORMAT('ALTER SEQUENCE %I RENAME TO %I', old_callback_log_sequence, 'dicom_server_callback_log_id_seq');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relkind = 'i'
          AND relname = old_callback_log_pkey
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relkind = 'i'
          AND relname = 'dicom_server_callback_log_pkey'
    ) THEN
        EXECUTE FORMAT('ALTER INDEX %I RENAME TO %I', old_callback_log_pkey, 'dicom_server_callback_log_pkey');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relkind = 'i'
          AND relname = 'idx_pacs_studies_hospital_udaya_dicom_server_study_id'
    ) THEN
        IF EXISTS (
            SELECT 1
            FROM pg_class
            WHERE relkind = 'i'
              AND relname = 'idx_pacs_studies_hospital_dicom_server_study_id'
        ) THEN
            DROP INDEX idx_pacs_studies_hospital_udaya_dicom_server_study_id;
        ELSE
            ALTER INDEX idx_pacs_studies_hospital_udaya_dicom_server_study_id
                RENAME TO idx_pacs_studies_hospital_dicom_server_study_id;
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relkind = 'i'
          AND relname = 'idx_udaya_dicom_server_callback_log_received_at'
    ) THEN
        DROP INDEX idx_udaya_dicom_server_callback_log_received_at;
    END IF;
END;
$$;
