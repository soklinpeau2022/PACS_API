ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(255);

UPDATE patients
SET
    first_name = COALESCE(
        NULLIF(BTRIM(first_name), ''),
        NULLIF(SPLIT_PART(BTRIM(COALESCE(name, '')), ' ', 1), ''),
        ''
    ),
    last_name = COALESCE(
        NULLIF(BTRIM(last_name), ''),
        NULLIF(BTRIM(
            CASE
                WHEN POSITION(' ' IN BTRIM(COALESCE(name, ''))) > 0
                    THEN SUBSTRING(BTRIM(COALESCE(name, '')) FROM POSITION(' ' IN BTRIM(COALESCE(name, ''))) + 1)
                ELSE ''
            END
        ), ''),
        ''
    );

ALTER TABLE patients
    ALTER COLUMN first_name SET DEFAULT '',
    ALTER COLUMN last_name SET DEFAULT '';

UPDATE patients
SET
    first_name = COALESCE(first_name, ''),
    last_name = COALESCE(last_name, '');

ALTER TABLE patients
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name SET NOT NULL;

DROP INDEX IF EXISTS idx_patients_name_trgm;

CREATE INDEX IF NOT EXISTS idx_patients_hospital_first_last_name
    ON patients (hospital_id, LOWER(first_name), LOWER(last_name), id DESC);

CREATE INDEX IF NOT EXISTS idx_patients_first_name_trgm
    ON patients USING gin (first_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_patients_last_name_trgm
    ON patients USING gin (last_name gin_trgm_ops);

ALTER TABLE patients
    DROP COLUMN IF EXISTS name;
