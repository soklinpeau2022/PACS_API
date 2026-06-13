-- Exact-filter indexes for high-cardinality PACS list APIs.
-- These complement the fuzzy trigram indexes from V108 and keep common
-- patient/accession/queue lookups on narrow btree paths.

CREATE INDEX IF NOT EXISTS idx_patients_hospital_lower_uid_active
    ON patients (hospital_id, LOWER(patient_uid))
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_patients_hospital_lower_phone_active
    ON patients (hospital_id, LOWER(phone_number))
    WHERE is_active = 1 AND phone_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_patients_hospital_gender_id_desc_active
    ON patients (hospital_id, gender, id DESC)
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_patients_name_trgm
    ON patients USING gin (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_pacs_queue_hospital_lower_visit_code
    ON pacs_patient_queue (hospital_id, LOWER(visit_code))
    WHERE visit_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_queue_hospital_lower_accession
    ON pacs_patient_queue (hospital_id, LOWER(accession_number))
    WHERE accession_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_lower_accession_active
    ON pacs_studies (hospital_id, LOWER(accession_number))
    WHERE is_active = 1 AND accession_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_system_activities_lower_action_module_created_id
    ON system_activities (
        LOWER(COALESCE(action, '')),
        LOWER(COALESCE(module, '')),
        created DESC,
        id DESC
    );

CREATE INDEX IF NOT EXISTS idx_system_activities_lower_endpoint_created_id
    ON system_activities (LOWER(COALESCE(endpoint, '')), created DESC, id DESC)
    WHERE endpoint IS NOT NULL;
