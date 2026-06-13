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
