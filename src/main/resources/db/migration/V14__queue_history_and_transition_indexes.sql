CREATE TABLE IF NOT EXISTS pacs_patient_queue_histories (
    id           BIGSERIAL PRIMARY KEY,
    hospital_id  BIGINT NOT NULL REFERENCES hospitals(id),
    queue_id     BIGINT NOT NULL REFERENCES pacs_patient_queue(id),
    patient_id   BIGINT NOT NULL REFERENCES patients(id),
    from_status  VARCHAR(50) NOT NULL,
    to_status    VARCHAR(50) NOT NULL,
    action       VARCHAR(100) NOT NULL,
    reason       TEXT,
    created      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   BIGINT
);

CREATE INDEX IF NOT EXISTS idx_queue_histories_hospital_queue_created
    ON pacs_patient_queue_histories(hospital_id, queue_id, created DESC);

CREATE INDEX IF NOT EXISTS idx_queue_histories_patient_created
    ON pacs_patient_queue_histories(patient_id, created DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_hospital_status
    ON pacs_patient_queue(hospital_id, status, created DESC);

