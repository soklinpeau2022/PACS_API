ALTER TABLE pacs_patient_queue
    ADD COLUMN IF NOT EXISTS dicom_server_series_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS sent_to_pacs_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS image_received_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reported_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;

UPDATE pacs_patient_queue
SET sent_to_pacs_at = COALESCE(sent_to_pacs_at, sent_at),
    image_received_at = COALESCE(image_received_at, received_at)
WHERE sent_at IS NOT NULL
   OR received_at IS NOT NULL;

UPDATE pacs_patient_queue
SET status = CASE status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 4
    WHEN 4 THEN 5
    WHEN 5 THEN 6
    WHEN 6 THEN 7
    ELSE status
END
WHERE status IN (1, 2, 3, 4, 5, 6);

UPDATE pacs_patient_queue
SET image_received_at = COALESCE(image_received_at, modified_at, modified)
WHERE status = 4
  AND image_received_at IS NULL;

UPDATE pacs_patient_queue
SET reported_at = COALESCE(reported_at, translated_at, modified_at, modified)
WHERE status = 5
  AND reported_at IS NULL;

UPDATE pacs_patient_queue
SET completed_at = COALESCE(completed_at, translated_at, modified_at, modified)
WHERE status = 6
  AND completed_at IS NULL;

UPDATE pacs_patient_queue
SET cancelled_at = COALESCE(cancelled_at, modified_at, modified)
WHERE status = 7
  AND cancelled_at IS NULL;

UPDATE pacs_patient_queue_histories
SET from_status = CASE from_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 4
    WHEN 4 THEN 5
    WHEN 5 THEN 6
    WHEN 6 THEN 7
    ELSE from_status
END,
    to_status = CASE to_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 4
    WHEN 4 THEN 5
    WHEN 5 THEN 6
    WHEN 6 THEN 7
    ELSE to_status
END
WHERE from_status IN (1, 2, 3, 4, 5, 6)
   OR to_status IN (1, 2, 3, 4, 5, 6);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_status_accession_active
    ON pacs_patient_queue (status, accession_number)
    WHERE status NOT IN (6, 7);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_image_received_at
    ON pacs_patient_queue (image_received_at);
