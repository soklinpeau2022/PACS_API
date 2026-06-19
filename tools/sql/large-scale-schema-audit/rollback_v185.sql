\set ON_ERROR_STOP on

-- WARNING: first two queries must return no rows. If they do, global uniqueness
-- cannot be restored without reconciling valid cross-hospital duplicates.
SELECT LOWER(patient_uid), count(DISTINCT hospital_id), count(*)
FROM patients
GROUP BY LOWER(patient_uid)
HAVING count(*) > 1;

SELECT LOWER(visit_code), count(DISTINCT hospital_id), count(*)
FROM pacs_worklists
WHERE visit_code IS NOT NULL
  AND BTRIM(visit_code) <> ''
GROUP BY LOWER(visit_code)
HAVING count(*) > 1;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_patients_patient_uid_global_lower
    ON patients (LOWER(patient_uid));
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_pacs_worklists_visit_code_global
    ON pacs_worklists (LOWER(visit_code))
    WHERE visit_code IS NOT NULL AND BTRIM(visit_code) <> '';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_revoked_tokens_jti ON revoked_tokens (jti);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_role_module_details_role_module ON role_module_details (role_id, module_detail_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_role_module_details_role_id ON role_module_details (role_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_role_module_details_module_detail_id ON role_module_details (module_detail_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_countries_name ON countries (name);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_oauth2_clients_client_id ON oauth2_clients (client_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_patient_sequences_hospital_year ON pacs_patient_sequences (hospital_id, sequence_year);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_hds_hospital_active_id_desc ON hospital_dicom_servers (hospital_id, is_active, id DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_patients_hospital_lower_uid_active
    ON patients (hospital_id, LOWER(patient_uid)) WHERE is_active = 1;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_worklists_hospital_lower_visit_code
    ON pacs_worklists (hospital_id, LOWER(visit_code)) WHERE visit_code IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_worklists_visit_code ON pacs_worklists (visit_code);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_studies_hospital_study_uid ON pacs_studies (hospital_id, study_instance_uid);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_studies_hospital_study_uid_active
    ON pacs_studies (hospital_id, study_instance_uid) WHERE is_active = 1;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_worklists_hospital_status_lookup ON pacs_worklists (hospital_id, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_viewer_states_accession
    ON pacs_viewer_states (hospital_id, accession_number)
    WHERE is_active = 1 AND accession_number IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_system_activities_created ON system_activities (created);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_system_activities_created_by ON system_activities (created_by);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_system_activities_module_id ON system_activities (module_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_system_activities_status ON system_activities (status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_system_activities_endpoint ON system_activities (endpoint);

DROP INDEX CONCURRENTLY IF EXISTS ux_patients_hospital_patient_uid_lower;
DROP INDEX CONCURRENTLY IF EXISTS ux_pacs_worklists_hospital_visit_code_lower;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_lower_visit_code;
