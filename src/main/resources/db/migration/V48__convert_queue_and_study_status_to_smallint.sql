-- Convert workflow statuses to compact numeric codes for better storage/index performance.
-- Queue status code map:
-- 1=WAITING, 2=SENDING, 3=SENT_TO_PACS, 4=RECEIVED, 5=TRANSLATED,
-- 6=REPORTED, 7=IN_PROGRESS, 8=COMPLETED, 9=CANCELLED, 10=RETURNED, 11=NO_SHOW
-- Study status code map:
-- 1=PENDING, 2=IN_PROGRESS, 3=COMPLETED, 4=CANCELLED

ALTER TABLE IF EXISTS pacs_patient_queue
    DROP CONSTRAINT IF EXISTS chk_pacs_patient_queue_status;

ALTER TABLE IF EXISTS pacs_studies
    DROP CONSTRAINT IF EXISTS chk_pacs_studies_status;

ALTER TABLE pacs_patient_queue
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE pacs_patient_queue
    ALTER COLUMN status TYPE SMALLINT
    USING CASE UPPER(TRIM(COALESCE(status::text, '')))
        WHEN '1' THEN 1
        WHEN 'WAITING' THEN 1
        WHEN '2' THEN 2
        WHEN 'SENDING' THEN 2
        WHEN '3' THEN 3
        WHEN 'SENT_TO_PACS' THEN 3
        WHEN '4' THEN 4
        WHEN 'RECEIVED' THEN 4
        WHEN '5' THEN 5
        WHEN 'TRANSLATED' THEN 5
        WHEN '6' THEN 6
        WHEN 'REPORTED' THEN 6
        WHEN '7' THEN 7
        WHEN 'IN_PROGRESS' THEN 7
        WHEN '8' THEN 8
        WHEN 'COMPLETED' THEN 8
        WHEN '9' THEN 9
        WHEN 'CANCELLED' THEN 9
        WHEN '10' THEN 10
        WHEN 'RETURNED' THEN 10
        WHEN '11' THEN 11
        WHEN 'NO_SHOW' THEN 11
        ELSE 1
    END;

ALTER TABLE pacs_patient_queue
    ALTER COLUMN status SET DEFAULT 1;

ALTER TABLE pacs_studies
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE pacs_studies
    ALTER COLUMN status TYPE SMALLINT
    USING CASE UPPER(TRIM(COALESCE(status::text, '')))
        WHEN '1' THEN 1
        WHEN 'PENDING' THEN 1
        WHEN '2' THEN 2
        WHEN 'IN_PROGRESS' THEN 2
        WHEN '3' THEN 3
        WHEN 'COMPLETED' THEN 3
        WHEN '4' THEN 4
        WHEN 'CANCELLED' THEN 4
        ELSE 1
    END;

ALTER TABLE pacs_studies
    ALTER COLUMN status SET DEFAULT 1;

ALTER TABLE pacs_patient_queue_histories
    ALTER COLUMN from_status TYPE SMALLINT
    USING CASE UPPER(TRIM(COALESCE(from_status::text, '')))
        WHEN '1' THEN 1
        WHEN 'WAITING' THEN 1
        WHEN '2' THEN 2
        WHEN 'SENDING' THEN 2
        WHEN '3' THEN 3
        WHEN 'SENT_TO_PACS' THEN 3
        WHEN '4' THEN 4
        WHEN 'RECEIVED' THEN 4
        WHEN '5' THEN 5
        WHEN 'TRANSLATED' THEN 5
        WHEN '6' THEN 6
        WHEN 'REPORTED' THEN 6
        WHEN '7' THEN 7
        WHEN 'IN_PROGRESS' THEN 7
        WHEN '8' THEN 8
        WHEN 'COMPLETED' THEN 8
        WHEN '9' THEN 9
        WHEN 'CANCELLED' THEN 9
        WHEN '10' THEN 10
        WHEN 'RETURNED' THEN 10
        WHEN '11' THEN 11
        WHEN 'NO_SHOW' THEN 11
        ELSE 1
    END;

ALTER TABLE pacs_patient_queue_histories
    ALTER COLUMN to_status TYPE SMALLINT
    USING CASE UPPER(TRIM(COALESCE(to_status::text, '')))
        WHEN '1' THEN 1
        WHEN 'WAITING' THEN 1
        WHEN '2' THEN 2
        WHEN 'SENDING' THEN 2
        WHEN '3' THEN 3
        WHEN 'SENT_TO_PACS' THEN 3
        WHEN '4' THEN 4
        WHEN 'RECEIVED' THEN 4
        WHEN '5' THEN 5
        WHEN 'TRANSLATED' THEN 5
        WHEN '6' THEN 6
        WHEN 'REPORTED' THEN 6
        WHEN '7' THEN 7
        WHEN 'IN_PROGRESS' THEN 7
        WHEN '8' THEN 8
        WHEN 'COMPLETED' THEN 8
        WHEN '9' THEN 9
        WHEN 'CANCELLED' THEN 9
        WHEN '10' THEN 10
        WHEN 'RETURNED' THEN 10
        WHEN '11' THEN 11
        WHEN 'NO_SHOW' THEN 11
        ELSE 1
    END;

ALTER TABLE pacs_patient_queue
    ADD CONSTRAINT chk_pacs_patient_queue_status
        CHECK (status BETWEEN 1 AND 11);

ALTER TABLE pacs_studies
    ADD CONSTRAINT chk_pacs_studies_status
        CHECK (status BETWEEN 1 AND 4);
