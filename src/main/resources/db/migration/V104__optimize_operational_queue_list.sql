CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_operational_hospital_id_desc
    ON pacs_patient_queue (hospital_id, id DESC)
    WHERE status IN (1, 2, 3, 4);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_operational_hospital_schedule
    ON pacs_patient_queue (hospital_id, scheduled_date, id DESC)
    WHERE status IN (1, 2, 3, 4);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_hospital_status_id_desc
    ON pacs_patient_queue (hospital_id, status, id DESC);
