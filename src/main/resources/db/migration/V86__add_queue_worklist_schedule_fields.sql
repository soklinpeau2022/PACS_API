ALTER TABLE pacs_patient_queue
    ADD COLUMN IF NOT EXISTS study_description TEXT,
    ADD COLUMN IF NOT EXISTS scheduled_date DATE,
    ADD COLUMN IF NOT EXISTS scheduled_time TIME;

UPDATE pacs_patient_queue q
SET study_description = COALESCE(
        NULLIF(TRIM(q.study_description), ''),
        NULLIF(TRIM(
            CONCAT_WS(
                ' - ',
                NULLIF(TRIM((SELECT s.name FROM services s WHERE s.id = q.service_id)), ''),
                NULLIF(TRIM((SELECT m.name FROM modalities m WHERE m.id = q.modality_id)), '')
            )
        ), ''),
        'PACS Study'
    )
WHERE q.study_description IS NULL OR TRIM(q.study_description) = '';

UPDATE pacs_patient_queue
SET scheduled_date = COALESCE(scheduled_date, CAST(COALESCE(sent_at, created_at, created) AS DATE))
WHERE scheduled_date IS NULL;

UPDATE pacs_patient_queue
SET scheduled_time = COALESCE(scheduled_time, CAST(COALESCE(sent_at, created_at, created) AS TIME))
WHERE scheduled_time IS NULL;
