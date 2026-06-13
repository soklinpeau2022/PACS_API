-- Queue list performance hardening for very large datasets (10M+ rows per hospital).
-- 1) Ensure created_at is always available for sargable date range filters.
UPDATE pacs_patient_queue
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;

ALTER TABLE pacs_patient_queue
    ALTER COLUMN created_at SET DEFAULT NOW();

ALTER TABLE pacs_patient_queue
    ALTER COLUMN created_at SET NOT NULL;

-- 2) Core list/index paths (hospital scoped + id desc windowing + common filters).
CREATE INDEX IF NOT EXISTS idx_queue_hospital_id_desc_cover
    ON pacs_patient_queue (hospital_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_queue_hospital_status_id_desc
    ON pacs_patient_queue (hospital_id, status, id DESC);

CREATE INDEX IF NOT EXISTS idx_queue_hospital_service_id_desc
    ON pacs_patient_queue (hospital_id, service_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_queue_hospital_modality_id_desc
    ON pacs_patient_queue (hospital_id, modality_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_queue_hospital_created_at_desc
    ON pacs_patient_queue (hospital_id, created_at DESC);

-- 3) Search acceleration for queue visit code ILIKE.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_queue_visit_code_trgm
    ON pacs_patient_queue USING gin (visit_code gin_trgm_ops);

-- 4) Search acceleration for patient phone ILIKE used by queue-list searchText.
CREATE INDEX IF NOT EXISTS idx_patients_phone_trgm
    ON patients USING gin (phone_number gin_trgm_ops);
