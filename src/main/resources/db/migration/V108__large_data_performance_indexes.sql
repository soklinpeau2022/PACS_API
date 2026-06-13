-- Large-data performance indexes for operational PACS APIs.
-- Keep these focused on high-volume list/search/filter paths so write overhead stays reasonable.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Queue list/search: hospital-scoped cursor paging, status/date filters, and flexible search.
CREATE INDEX IF NOT EXISTS idx_pacs_queue_hospital_status_scheduled_id_desc
    ON pacs_patient_queue (hospital_id, status, scheduled_date, id DESC)
    WHERE status IN (1, 2, 3, 4) AND scheduled_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_queue_hospital_patient_status_id_desc
    ON pacs_patient_queue (hospital_id, patient_id, status, id DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_queue_visit_code_trgm
    ON pacs_patient_queue USING gin (visit_code gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_pacs_queue_accession_trgm
    ON pacs_patient_queue USING gin (accession_number gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_pacs_queue_study_description_trgm
    ON pacs_patient_queue USING gin (study_description gin_trgm_ops);

-- Patient list/search: phone search was the missing high-volume predicate.
CREATE INDEX IF NOT EXISTS idx_patients_phone_trgm
    ON patients USING gin (phone_number gin_trgm_ops);

-- Study list/search: status/date archive paging and text search.
CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_status_study_date_id_desc
    ON pacs_studies (hospital_id, status, study_date DESC, id DESC)
    WHERE is_active = 1 AND study_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_study_date_id_desc
    ON pacs_studies (hospital_id, study_date DESC, id DESC)
    WHERE is_active = 1 AND study_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_accession_trgm
    ON pacs_studies USING gin (accession_number gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_pacs_studies_description_trgm
    ON pacs_studies USING gin (study_description gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_pacs_studies_modality_trgm
    ON pacs_studies USING gin (modality gin_trgm_ops);

-- Activity log list/search: common filters with newest-first pagination.
CREATE INDEX IF NOT EXISTS idx_system_activities_created_desc_id_desc
    ON system_activities (created DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_system_activities_module_created_desc
    ON system_activities (module_id, created DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_system_activities_user_created_desc
    ON system_activities (created_by, created DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_system_activities_status_created_desc
    ON system_activities (status, created DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_system_activities_module_status_created_desc
    ON system_activities (module_id, status, created DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_system_activities_action_trgm
    ON system_activities USING gin (action gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_system_activities_module_trgm
    ON system_activities USING gin (module gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_system_activities_description_trgm
    ON system_activities USING gin (description gin_trgm_ops);
