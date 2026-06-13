-- Normalize queue workflow to six-state model:
-- 1=WAITING, 2=IN_PROGRESS, 3=RECEIVED, 4=TRANSLATED, 5=COMPLETED, 6=CANCELLED
-- Legacy codes are remapped:
-- 2/3/7 -> 2 (IN_PROGRESS), 4 -> 3 (RECEIVED), 5/6 -> 4 (TRANSLATED),
-- 8 -> 5 (COMPLETED), 9/10/11 -> 6 (CANCELLED)

ALTER TABLE IF EXISTS pacs_patient_queue
    DROP CONSTRAINT IF EXISTS chk_pacs_patient_queue_status;

UPDATE pacs_patient_queue
SET status = CASE status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 3
    WHEN 5 THEN 4
    WHEN 6 THEN 4
    WHEN 7 THEN 2
    WHEN 8 THEN 5
    WHEN 9 THEN 6
    WHEN 10 THEN 6
    WHEN 11 THEN 6
    ELSE 1
END
WHERE status IS DISTINCT FROM CASE status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 3
    WHEN 5 THEN 4
    WHEN 6 THEN 4
    WHEN 7 THEN 2
    WHEN 8 THEN 5
    WHEN 9 THEN 6
    WHEN 10 THEN 6
    WHEN 11 THEN 6
    ELSE 1
END;

ALTER TABLE pacs_patient_queue
    ALTER COLUMN status SET DEFAULT 1;

ALTER TABLE pacs_patient_queue
    ADD CONSTRAINT chk_pacs_patient_queue_status
        CHECK (status BETWEEN 1 AND 6);

UPDATE pacs_patient_queue_histories
SET from_status = CASE from_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 3
    WHEN 5 THEN 4
    WHEN 6 THEN 4
    WHEN 7 THEN 2
    WHEN 8 THEN 5
    WHEN 9 THEN 6
    WHEN 10 THEN 6
    WHEN 11 THEN 6
    ELSE 1
END,
to_status = CASE to_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 3
    WHEN 5 THEN 4
    WHEN 6 THEN 4
    WHEN 7 THEN 2
    WHEN 8 THEN 5
    WHEN 9 THEN 6
    WHEN 10 THEN 6
    WHEN 11 THEN 6
    ELSE 1
END
WHERE from_status IS DISTINCT FROM CASE from_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 3
    WHEN 5 THEN 4
    WHEN 6 THEN 4
    WHEN 7 THEN 2
    WHEN 8 THEN 5
    WHEN 9 THEN 6
    WHEN 10 THEN 6
    WHEN 11 THEN 6
    ELSE 1
END
OR to_status IS DISTINCT FROM CASE to_status
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 3 THEN 2
    WHEN 4 THEN 3
    WHEN 5 THEN 4
    WHEN 6 THEN 4
    WHEN 7 THEN 2
    WHEN 8 THEN 5
    WHEN 9 THEN 6
    WHEN 10 THEN 6
    WHEN 11 THEN 6
    ELSE 1
END;
