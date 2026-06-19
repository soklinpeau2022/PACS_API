-- V194: final DEV-oriented hot-table index cleanup.
-- This migration is intentionally non-transactional because PostgreSQL
-- requires CREATE/DROP INDEX CONCURRENTLY to run outside a transaction.

-- ---------------------------------------------------------------------------
-- Create replacement indexes first.
-- ---------------------------------------------------------------------------

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_pacs_worklists_id_hospital
    ON pacs_worklists (id, hospital_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_worklists_hospital_status_created
    ON pacs_worklists (hospital_id, status, created_at DESC, id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_worklists_hospital_status_scheduled
    ON pacs_worklists (hospital_id, status, scheduled_date DESC, id DESC)
    WHERE scheduled_date IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_worklists_hospital_patient
    ON pacs_worklists (hospital_id, patient_id, id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_worklists_hospital_modality_status
    ON pacs_worklists (hospital_id, modality_id, status, id DESC)
    WHERE modality_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_worklists_hospital_route_status
    ON pacs_worklists (hospital_id, dicom_route_id, status, id DESC)
    WHERE dicom_route_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_patients_hospital_active_id
    ON patients (hospital_id, is_active, id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_patients_hospital_name
    ON patients (hospital_id, LOWER(first_name), LOWER(last_name), id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_patients_hospital_hn
    ON patients (hospital_id, LOWER(patient_hn))
    WHERE patient_hn IS NOT NULL
      AND BTRIM(patient_hn) <> '';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_studies_hospital_study_date
    ON pacs_studies (hospital_id, study_date DESC, id DESC)
    WHERE is_active = 1
      AND study_date IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_studies_hospital_received
    ON pacs_studies (hospital_id, received_at DESC, id DESC)
    WHERE is_active = 1
      AND received_at IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_studies_hospital_patient_date
    ON pacs_studies (hospital_id, patient_id, study_date DESC, id DESC)
    WHERE is_active = 1;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_studies_hospital_accession
    ON pacs_studies (hospital_id, accession_number)
    WHERE accession_number IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_studies_hospital_dicom_server_study
    ON pacs_studies (hospital_id, dicom_server_study_id)
    WHERE dicom_server_study_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_studies_hospital_modality_date
    ON pacs_studies (hospital_id, modality_id, study_date DESC, id DESC)
    WHERE is_active = 1
      AND modality_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_results_hospital_patient_created
    ON pacs_results (hospital_id, patient_id, created_at DESC, id DESC)
    WHERE is_active = 1
      AND patient_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_result_images_hospital_worklist_active
    ON pacs_result_images (hospital_id, worklist_id, is_active, sort_order, id)
    WHERE worklist_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_callback_log_hospital_server_received
    ON dicom_server_callback_log (hospital_id, dicom_server_id, received_at DESC, id DESC)
    WHERE hospital_id IS NOT NULL
      AND dicom_server_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_callback_log_hospital_accession
    ON dicom_server_callback_log (hospital_id, accession_number)
    WHERE hospital_id IS NOT NULL
      AND accession_number IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_realtime_events_hospital_created
    ON pacs_realtime_notification_events (hospital_id, created_at DESC, id DESC);

-- Partitioned parents cannot be indexed concurrently on all supported PG
-- versions. These statements are still idempotent and create partition indexes.
CREATE INDEX IF NOT EXISTS idx_system_activities_created_id
    ON system_activities (created DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_system_activities_created_by_created
    ON system_activities (created_by, created DESC, id DESC)
    WHERE created_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_system_activities_status_created
    ON system_activities (status, created DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_user_logs_created_id
    ON user_logs (created DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_user_logs_user_created
    ON user_logs (user_id, created DESC, id DESC)
    WHERE user_id IS NOT NULL;

-- Rebuild status-created result index with id in the sort key.
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_results_hospital_status_created;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_results_hospital_status_created
    ON pacs_results (hospital_id, status, created_at DESC, id DESC)
    WHERE is_active = 1;

-- ---------------------------------------------------------------------------
-- Drop duplicate, overlapping, or legacy indexes.
-- ---------------------------------------------------------------------------

-- Keep ux_worklists_id_hospital when present: existing hospital-safe FKs may
-- depend on that unique index. Rebuilding those FKs belongs in a dedicated
-- constraint migration, not in a concurrent index cleanup migration.

DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_active_hospital_status_schedule_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_active_patient_modality;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_hospital_status_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_operational_hospital_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_operational_hospital_schedule;
DROP INDEX CONCURRENTLY IF EXISTS idx_worklist_hospital_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_hospital_patient_status_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_hospital_status_scheduled_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_hospital_modality_status_created;
DROP INDEX CONCURRENTLY IF EXISTS idx_worklists_hospital_modality_status_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_image_received_at;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_study_description_trgm;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_visit_code_trgm;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_lower_visit_code;

DROP INDEX CONCURRENTLY IF EXISTS idx_patients_hospital_active_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_patients_hospital_first_last_name;
DROP INDEX CONCURRENTLY IF EXISTS idx_patients_hospital_lower_patient_hn_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_patients_hospital_lower_phone_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_patients_hospital_gender_id_desc_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_patients_phone_trgm;
DROP INDEX CONCURRENTLY IF EXISTS idx_patients_uid_trgm;

DROP INDEX CONCURRENTLY IF EXISTS idx_studies_hospital_active_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_studies_hospital_status_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_status_study_date_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_study_date_id_desc;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_lower_accession_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_patient;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_status;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_study_uid;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_instance_count_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_lower_institution_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_dicom_server;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_dicom_server_study_id;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_modality_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_accession_trgm;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_description_trgm;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_modality_trgm;

DROP INDEX CONCURRENTLY IF EXISTS ux_pacs_results_hospital_modality_study_active;
DROP INDEX CONCURRENTLY IF EXISTS ux_pacs_results_hospital_modality_worklist_active;
DROP INDEX CONCURRENTLY IF EXISTS ux_pacs_results_hospital_modality_queue_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_results_patient;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_results_patient_code;

DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_result_images_result_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_result_images_hospital_modality;

DROP INDEX CONCURRENTLY IF EXISTS idx_callback_log_server_received;
DROP INDEX CONCURRENTLY IF EXISTS idx_dicom_server_callback_log_accession;
DROP INDEX CONCURRENTLY IF EXISTS idx_dicom_server_callback_log_study;
DROP INDEX CONCURRENTLY IF EXISTS idx_dicom_server_callback_log_received_at;

DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_realtime_events_created;

DROP INDEX IF EXISTS idx_system_activities_created_desc_id_desc;
DROP INDEX IF EXISTS idx_system_activities_user_created_desc;
DROP INDEX IF EXISTS idx_system_activities_status_created_desc;
DROP INDEX IF EXISTS idx_system_activities_module_created_desc;
DROP INDEX IF EXISTS idx_system_activities_module_status_created_desc;
DROP INDEX IF EXISTS idx_system_activities_description_trgm;

DROP INDEX IF EXISTS idx_user_logs_user_id;
DROP INDEX IF EXISTS idx_user_logs_created;
