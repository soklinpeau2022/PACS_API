WITH hospital_tokens AS (
    SELECT
        h.id AS hospital_id,
        COALESCE(
            (
                SELECT token
                FROM unnest(regexp_split_to_array(
                    UPPER(COALESCE(NULLIF(TRIM(h.abbr), ''), NULLIF(TRIM(h.code), ''), NULLIF(TRIM(h.name), ''), 'H' || h.id::text)),
                    '[^A-Z0-9]+'
                )) AS token
                WHERE token <> ''
                  AND token NOT IN ('HOSPITAL', 'HOSP', 'CLINIC', 'CENTER')
                  AND LENGTH(token) BETWEEN 2 AND 10
                LIMIT 1
            ),
            NULLIF(SUBSTRING(regexp_replace(
                UPPER(COALESCE(NULLIF(TRIM(h.abbr), ''), NULLIF(TRIM(h.code), ''), NULLIF(TRIM(h.name), ''), 'H' || h.id::text)),
                '[^A-Z0-9]',
                '',
                'g'
            ) FROM 1 FOR 10), ''),
            'H' || h.id::text
        ) AS hospital_token
    FROM hospitals h
)
UPDATE patients p
SET patient_uid = SUBSTRING(p.patient_uid FROM 1 FOR 2)
    || '-' || ht.hospital_token
    || '-P' || RIGHT(p.patient_uid, 7)
FROM hospital_tokens ht
WHERE ht.hospital_id = p.hospital_id
  AND p.patient_uid ~ '^[0-9]{2}P[0-9]{7}$';

WITH hospital_tokens AS (
    SELECT
        h.id AS hospital_id,
        COALESCE(
            (
                SELECT token
                FROM unnest(regexp_split_to_array(
                    UPPER(COALESCE(NULLIF(TRIM(h.abbr), ''), NULLIF(TRIM(h.code), ''), NULLIF(TRIM(h.name), ''), 'H' || h.id::text)),
                    '[^A-Z0-9]+'
                )) AS token
                WHERE token <> ''
                  AND token NOT IN ('HOSPITAL', 'HOSP', 'CLINIC', 'CENTER')
                  AND LENGTH(token) BETWEEN 2 AND 10
                LIMIT 1
            ),
            NULLIF(SUBSTRING(regexp_replace(
                UPPER(COALESCE(NULLIF(TRIM(h.abbr), ''), NULLIF(TRIM(h.code), ''), NULLIF(TRIM(h.name), ''), 'H' || h.id::text)),
                '[^A-Z0-9]',
                '',
                'g'
            ) FROM 1 FOR 10), ''),
            'H' || h.id::text
        ) AS hospital_token
    FROM hospitals h
),
candidates AS (
    SELECT
        p.id,
        p.hospital_id,
        TO_CHAR(COALESCE(p.created, NOW()), 'YY') AS year_prefix,
        ht.hospital_token,
        ROW_NUMBER() OVER (
            PARTITION BY p.hospital_id, TO_CHAR(COALESCE(p.created, NOW()), 'YY'), ht.hospital_token
            ORDER BY p.id
        ) AS rn
    FROM patients p
    INNER JOIN hospital_tokens ht ON ht.hospital_id = p.hospital_id
    WHERE p.patient_uid !~ '^[0-9]{2}-[A-Z0-9]{2,10}-P[0-9]{7}$'
),
max_existing AS (
    SELECT
        p.hospital_id,
        SUBSTRING(p.patient_uid FROM 1 FOR 2) AS year_prefix,
        SPLIT_PART(p.patient_uid, '-', 2) AS hospital_token,
        COALESCE(MAX(CAST(RIGHT(p.patient_uid, 7) AS BIGINT)), 0) AS max_sequence
    FROM patients p
    WHERE p.patient_uid ~ '^[0-9]{2}-[A-Z0-9]{2,10}-P[0-9]{7}$'
    GROUP BY p.hospital_id, SUBSTRING(p.patient_uid FROM 1 FOR 2), SPLIT_PART(p.patient_uid, '-', 2)
)
UPDATE patients p
SET patient_uid = c.year_prefix
    || '-' || c.hospital_token
    || '-P' || LPAD((COALESCE(m.max_sequence, 0) + c.rn)::text, 7, '0')
FROM candidates c
LEFT JOIN max_existing m
       ON m.hospital_id = c.hospital_id
      AND m.year_prefix = c.year_prefix
      AND m.hospital_token = c.hospital_token
WHERE p.id = c.id;

CREATE TABLE IF NOT EXISTS pacs_patient_sequences (
    id            BIGSERIAL PRIMARY KEY,
    hospital_id   BIGINT NOT NULL REFERENCES hospitals(id),
    sequence_year VARCHAR(2) NOT NULL,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    modified_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (hospital_id, sequence_year)
);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_sequences_hospital_year
    ON pacs_patient_sequences (hospital_id, sequence_year);

WITH parsed AS (
    SELECT
        hospital_id,
        SUBSTRING(patient_uid FROM 1 FOR 2) AS sequence_year,
        MAX(CAST(RIGHT(patient_uid, 7) AS BIGINT)) AS last_sequence
    FROM patients
    WHERE patient_uid ~ '^[0-9]{2}-[A-Z0-9]{2,10}-P[0-9]{7}$'
    GROUP BY hospital_id, SUBSTRING(patient_uid FROM 1 FOR 2)
)
INSERT INTO pacs_patient_sequences (hospital_id, sequence_year, last_sequence, modified_at)
SELECT hospital_id, sequence_year, last_sequence, NOW()
FROM parsed
ON CONFLICT (hospital_id, sequence_year) DO UPDATE
SET
    last_sequence = GREATEST(pacs_patient_sequences.last_sequence, EXCLUDED.last_sequence),
    modified_at = NOW();

CREATE UNIQUE INDEX IF NOT EXISTS ux_patients_patient_uid_global_lower
    ON patients (LOWER(patient_uid));
