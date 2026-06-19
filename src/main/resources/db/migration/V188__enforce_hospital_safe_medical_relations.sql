-- New rows are checked immediately. Existing rows remain available while
-- operators validate each constraint online with the supplied finalization SQL.
-- The pre-migration validation must report zero cross-hospital relationships.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_results_reference') THEN
        ALTER TABLE pacs_results
            ADD CONSTRAINT chk_pacs_results_reference
            CHECK (study_id IS NOT NULL OR worklist_id IS NOT NULL) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_result_images_file_size') THEN
        ALTER TABLE pacs_result_images
            ADD CONSTRAINT chk_pacs_result_images_file_size
            CHECK (file_size IS NULL OR file_size >= 0) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_result_images_sort_order') THEN
        ALTER TABLE pacs_result_images
            ADD CONSTRAINT chk_pacs_result_images_sort_order
            CHECK (sort_order >= 0) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_result_images_sha256') THEN
        ALTER TABLE pacs_result_images
            ADD CONSTRAINT chk_pacs_result_images_sha256
            CHECK (file_sha256 IS NULL OR file_sha256 ~ '^[0-9a-f]{64}$') NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_callback_log_attempt_count') THEN
        ALTER TABLE dicom_server_callback_log
            ADD CONSTRAINT chk_callback_log_attempt_count
            CHECK (attempt_count > 0) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_callback_log_payload_sha256') THEN
        ALTER TABLE dicom_server_callback_log
            ADD CONSTRAINT chk_callback_log_payload_sha256
            CHECK (payload_sha256 IS NULL OR payload_sha256 ~ '^[0-9a-f]{64}$') NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_result_versions_active') THEN
        ALTER TABLE pacs_result_versions
            ADD CONSTRAINT chk_pacs_result_versions_active
            CHECK (is_active IN (1, 2)) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_result_versions_version') THEN
        ALTER TABLE pacs_result_versions
            ADD CONSTRAINT chk_pacs_result_versions_version
            CHECK (version_no > 0) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_daily_stats_nonnegative') THEN
        ALTER TABLE pacs_daily_stats
            ADD CONSTRAINT chk_pacs_daily_stats_nonnegative
            CHECK (
                waiting_count >= 0
                AND in_progress_count >= 0
                AND cancelled_count >= 0
                AND failed_count >= 0
                AND received_study_count >= 0
                AND completed_result_count >= 0
            ) NOT VALID;
    END IF;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_worklists_study_hospital') THEN
        ALTER TABLE pacs_worklists
            ADD CONSTRAINT fk_worklists_study_hospital
            FOREIGN KEY (study_id, hospital_id)
            REFERENCES pacs_studies (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_worklist_study_links_worklist_hospital') THEN
        ALTER TABLE pacs_worklist_study_links
            ADD CONSTRAINT fk_worklist_study_links_worklist_hospital
            FOREIGN KEY (worklist_id, hospital_id)
            REFERENCES pacs_worklists (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_worklist_study_links_study_hospital') THEN
        ALTER TABLE pacs_worklist_study_links
            ADD CONSTRAINT fk_worklist_study_links_study_hospital
            FOREIGN KEY (study_id, hospital_id)
            REFERENCES pacs_studies (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_results_study_hospital') THEN
        ALTER TABLE pacs_results
            ADD CONSTRAINT fk_results_study_hospital
            FOREIGN KEY (study_id, hospital_id)
            REFERENCES pacs_studies (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_results_worklist_hospital') THEN
        ALTER TABLE pacs_results
            ADD CONSTRAINT fk_results_worklist_hospital
            FOREIGN KEY (worklist_id, hospital_id)
            REFERENCES pacs_worklists (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_results_patient_hospital') THEN
        ALTER TABLE pacs_results
            ADD CONSTRAINT fk_results_patient_hospital
            FOREIGN KEY (patient_id, hospital_id)
            REFERENCES patients (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_result_images_result_hospital') THEN
        ALTER TABLE pacs_result_images
            ADD CONSTRAINT fk_result_images_result_hospital
            FOREIGN KEY (result_id, hospital_id)
            REFERENCES pacs_results (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_result_images_result_restrict') THEN
        ALTER TABLE pacs_result_images
            ADD CONSTRAINT fk_result_images_result_restrict
            FOREIGN KEY (result_id)
            REFERENCES pacs_results (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_viewer_states_study_hospital') THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_viewer_states_study_hospital
            FOREIGN KEY (study_id, hospital_id)
            REFERENCES pacs_studies (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_viewer_states_worklist_hospital') THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_viewer_states_worklist_hospital
            FOREIGN KEY (worklist_id, hospital_id)
            REFERENCES pacs_worklists (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_viewer_states_patient_hospital') THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_viewer_states_patient_hospital
            FOREIGN KEY (patient_id, hospital_id)
            REFERENCES patients (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_realtime_events_hospital_restrict') THEN
        ALTER TABLE pacs_realtime_notification_events
            ADD CONSTRAINT fk_realtime_events_hospital_restrict
            FOREIGN KEY (hospital_id)
            REFERENCES hospitals (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_realtime_events_worklist_hospital') THEN
        ALTER TABLE pacs_realtime_notification_events
            ADD CONSTRAINT fk_realtime_events_worklist_hospital
            FOREIGN KEY (worklist_id, hospital_id)
            REFERENCES pacs_worklists (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_realtime_events_study_hospital') THEN
        ALTER TABLE pacs_realtime_notification_events
            ADD CONSTRAINT fk_realtime_events_study_hospital
            FOREIGN KEY (study_id, hospital_id)
            REFERENCES pacs_studies (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_retention_requests_study_hospital') THEN
        ALTER TABLE study_retention_delete_requests
            ADD CONSTRAINT fk_retention_requests_study_hospital
            FOREIGN KEY (study_id, hospital_id)
            REFERENCES pacs_studies (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_retention_requests_server_hospital') THEN
        ALTER TABLE study_retention_delete_requests
            ADD CONSTRAINT fk_retention_requests_server_hospital
            FOREIGN KEY (dicom_server_id, hospital_id)
            REFERENCES hospital_dicom_servers (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_callback_log_hospital') THEN
        ALTER TABLE dicom_server_callback_log
            ADD CONSTRAINT fk_callback_log_hospital
            FOREIGN KEY (hospital_id)
            REFERENCES hospitals (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_callback_log_server_hospital') THEN
        ALTER TABLE dicom_server_callback_log
            ADD CONSTRAINT fk_callback_log_server_hospital
            FOREIGN KEY (dicom_server_id, hospital_id)
            REFERENCES hospital_dicom_servers (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_result_versions_result_hospital') THEN
        ALTER TABLE pacs_result_versions
            ADD CONSTRAINT fk_result_versions_result_hospital
            FOREIGN KEY (result_id, hospital_id)
            REFERENCES pacs_results (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_daily_stats_hospital') THEN
        ALTER TABLE pacs_daily_stats
            ADD CONSTRAINT fk_pacs_daily_stats_hospital
            FOREIGN KEY (hospital_id)
            REFERENCES hospitals (id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;
END;
$$;

-- Remove duplicate or unsafe single-column relationships only after the
-- hospital-safe replacements above exist. NOT VALID FKs still enforce all new
-- writes and parent deletes immediately.
ALTER TABLE pacs_worklists
    DROP CONSTRAINT IF EXISTS fk_pacs_worklists_study_id;

ALTER TABLE pacs_worklist_study_links
    DROP CONSTRAINT IF EXISTS pacs_worklist_study_links_worklist_id_fkey,
    DROP CONSTRAINT IF EXISTS pacs_worklist_study_links_study_id_fkey;

ALTER TABLE pacs_results
    DROP CONSTRAINT IF EXISTS pacs_results_study_id_fkey,
    DROP CONSTRAINT IF EXISTS pacs_results_worklist_id_fkey,
    DROP CONSTRAINT IF EXISTS pacs_results_patient_id_fkey;

ALTER TABLE pacs_result_images
    DROP CONSTRAINT IF EXISTS pacs_result_images_result_id_fkey;

ALTER TABLE pacs_viewer_states
    DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_study,
    DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_worklist,
    DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_patient;

ALTER TABLE pacs_realtime_notification_events
    DROP CONSTRAINT IF EXISTS pacs_realtime_notification_events_hospital_id_fkey,
    DROP CONSTRAINT IF EXISTS pacs_realtime_notification_events_worklist_id_fkey,
    DROP CONSTRAINT IF EXISTS pacs_realtime_notification_events_study_id_fkey;

ALTER TABLE patients
    DROP CONSTRAINT IF EXISTS patients_hospital_id_patient_uid_key;

