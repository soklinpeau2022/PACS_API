-- Queue status model:
-- 1=WAITING, 2=IN_PROGRESS, 3=CANCELLED, 4=FAILED
--
-- Study status model:
-- 1=IMAGE_RECEIVED, 2=COMPLETED

ALTER TABLE IF EXISTS pacs_patient_queue
    DROP CONSTRAINT IF EXISTS chk_pacs_patient_queue_status;

UPDATE pacs_patient_queue
SET status = CASE status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 3
    WHEN 4 THEN 4
    ELSE 1
END
WHERE status IS DISTINCT FROM CASE status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 3
    WHEN 4 THEN 4
    ELSE 1
END;

ALTER TABLE pacs_patient_queue
    ALTER COLUMN status SET DEFAULT 1;

ALTER TABLE pacs_patient_queue
    ADD CONSTRAINT chk_pacs_patient_queue_status
        CHECK (status BETWEEN 1 AND 4);

UPDATE pacs_patient_queue_histories
SET from_status = CASE from_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 3
    WHEN 4 THEN 4
    ELSE 1
END,
to_status = CASE to_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 3
    WHEN 4 THEN 4
    ELSE 1
END
WHERE from_status IS DISTINCT FROM CASE from_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 3
    WHEN 4 THEN 4
    ELSE 1
END
OR to_status IS DISTINCT FROM CASE to_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 3
    WHEN 4 THEN 4
    ELSE 1
END;

ALTER TABLE IF EXISTS pacs_studies
    DROP CONSTRAINT IF EXISTS chk_pacs_studies_status;

UPDATE pacs_studies
SET status = 1
WHERE status IS DISTINCT FROM 1;

UPDATE pacs_studies s
SET status = 2,
    modified = NOW()
FROM pacs_patient_queue q
INNER JOIN pacs_queue_results qr
        ON qr.hospital_id = q.hospital_id
       AND qr.queue_id = q.id
       AND qr.is_active = 1
WHERE s.hospital_id = q.hospital_id
  AND s.id = q.study_id
  AND s.is_active = 1;

UPDATE pacs_studies s
SET status = 2,
    modified = NOW()
FROM pacs_queue_study_links qsl
INNER JOIN pacs_queue_results qr
        ON qr.hospital_id = qsl.hospital_id
       AND qr.queue_id = qsl.queue_id
       AND qr.is_active = 1
WHERE s.hospital_id = qsl.hospital_id
  AND s.id = qsl.study_id
  AND qsl.is_primary = 1
  AND s.is_active = 1;

ALTER TABLE pacs_studies
    ALTER COLUMN status SET DEFAULT 1;

ALTER TABLE pacs_studies
    ADD CONSTRAINT chk_pacs_studies_status
        CHECK (status IN (1, 2));

DROP INDEX IF EXISTS idx_pacs_studies_status;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_status
    ON pacs_studies (hospital_id, status)
    WHERE is_active = 1;
