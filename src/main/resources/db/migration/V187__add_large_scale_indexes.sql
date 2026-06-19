-- Non-transactional online index stage.
-- Replacement indexes are created before any older compatibility index is removed.

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_pacs_studies_id_hospital
    ON pacs_studies (id, hospital_id);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_pacs_results_id_hospital
    ON pacs_results (id, hospital_id);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_pacs_results_hospital_study_active
    ON pacs_results (hospital_id, study_id)
    WHERE is_active = 1
      AND study_id IS NOT NULL;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_pacs_results_hospital_worklist_active
    ON pacs_results (hospital_id, worklist_id)
    WHERE is_active = 1
      AND worklist_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_results_study_hospital
    ON pacs_results (study_id, hospital_id)
    WHERE study_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_results_worklist_hospital
    ON pacs_results (worklist_id, hospital_id)
    WHERE worklist_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_results_patient_hospital
    ON pacs_results (patient_id, hospital_id)
    WHERE patient_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_result_images_hospital_result_active
    ON pacs_result_images (hospital_id, result_id, is_active, sort_order, id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_result_images_hospital_study_active
    ON pacs_result_images (hospital_id, study_id, is_active, sort_order, id)
    WHERE study_id IS NOT NULL;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_pacs_result_versions_public_id
    ON pacs_result_versions (public_id);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_pacs_result_versions_result_version
    ON pacs_result_versions (result_id, version_no);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_result_versions_hospital_result_changed
    ON pacs_result_versions (hospital_id, result_id, changed_at DESC, id DESC);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_callback_log_hospital_dedupe
    ON dicom_server_callback_log (hospital_id, dedupe_key)
    WHERE hospital_id IS NOT NULL
      AND dedupe_key IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_callback_log_hospital_received
    ON dicom_server_callback_log (hospital_id, received_at DESC, id DESC)
    WHERE hospital_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_callback_log_server_received
    ON dicom_server_callback_log (dicom_server_id, received_at DESC, id DESC)
    WHERE dicom_server_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_realtime_events_worklist_hospital
    ON pacs_realtime_notification_events (worklist_id, hospital_id)
    WHERE worklist_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_realtime_events_study_hospital
    ON pacs_realtime_notification_events (study_id, hospital_id)
    WHERE study_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_retention_requests_study_hospital
    ON study_retention_delete_requests (study_id, hospital_id)
    WHERE study_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_retention_requests_server_hospital
    ON study_retention_delete_requests (dicom_server_id, hospital_id)
    WHERE dicom_server_id IS NOT NULL;

-- Case-insensitive scoped uniqueness supersedes these case-sensitive indexes.
DROP INDEX CONCURRENTLY IF EXISTS ux_pacs_worklists_hospital_visit_code;

