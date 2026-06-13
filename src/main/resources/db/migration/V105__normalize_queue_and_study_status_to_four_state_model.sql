-- Queue status model:
-- 1=WAITING, 2=IN_PROGRESS, 3=CANCELLED, 4=FAILED
--
-- Old successful/intermediate states are folded into IN_PROGRESS because this
-- project only keeps the queue lifecycle, not report completion status.

ALTER TABLE IF EXISTS pacs_patient_queue
    DROP CONSTRAINT IF EXISTS chk_pacs_patient_queue_status;

ALTER TABLE IF EXISTS pacs_studies
    DROP CONSTRAINT IF EXISTS chk_pacs_studies_status;

UPDATE pacs_patient_queue
SET status = CASE status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 2
    WHEN 5 THEN 2
    WHEN 6 THEN 2
    WHEN 7 THEN 3
    WHEN 8 THEN 4
    ELSE 1
END
WHERE status IS DISTINCT FROM CASE status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 2
    WHEN 5 THEN 2
    WHEN 6 THEN 2
    WHEN 7 THEN 3
    WHEN 8 THEN 4
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
    WHEN 3 THEN 2
    WHEN 4 THEN 2
    WHEN 5 THEN 2
    WHEN 6 THEN 2
    WHEN 7 THEN 3
    WHEN 8 THEN 4
    ELSE 1
END,
to_status = CASE to_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 2
    WHEN 5 THEN 2
    WHEN 6 THEN 2
    WHEN 7 THEN 3
    WHEN 8 THEN 4
    ELSE 1
END
WHERE from_status IS DISTINCT FROM CASE from_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 2
    WHEN 5 THEN 2
    WHEN 6 THEN 2
    WHEN 7 THEN 3
    WHEN 8 THEN 4
    ELSE 1
END
OR to_status IS DISTINCT FROM CASE to_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 2
    WHEN 5 THEN 2
    WHEN 6 THEN 2
    WHEN 7 THEN 3
    WHEN 8 THEN 4
    ELSE 1
END;

UPDATE pacs_studies
SET status = CASE status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 3
    ELSE 1
END
WHERE status IS DISTINCT FROM CASE status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 3
    ELSE 1
END;

ALTER TABLE pacs_studies
    ALTER COLUMN status SET DEFAULT 1;

ALTER TABLE pacs_studies
    ADD CONSTRAINT chk_pacs_studies_status
        CHECK (status BETWEEN 1 AND 4);

DELETE FROM role_module_details rmd
USING module_details md
WHERE rmd.module_detail_id = md.id
  AND md.code IN ('pacs.queue.translate', 'pacs.queue.complete');

UPDATE module_details
SET is_active = 2,
    modified = NOW()
WHERE code IN ('pacs.queue.translate', 'pacs.queue.complete');

DELETE FROM endpoint_permissions
WHERE endpoint_pattern IN ('/queue/queue-translate', '/queue/queue-complete')
   OR permission_code IN ('pacs.queue.translate', 'pacs.queue.complete');

DROP INDEX IF EXISTS idx_pacs_patient_queue_operational_hospital_id_desc;
DROP INDEX IF EXISTS idx_pacs_patient_queue_operational_hospital_schedule;

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_operational_hospital_id_desc
    ON pacs_patient_queue (hospital_id, id DESC)
    WHERE status IN (1, 2, 3, 4);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_operational_hospital_schedule
    ON pacs_patient_queue (hospital_id, scheduled_date, id DESC)
    WHERE status IN (1, 2, 3, 4);
