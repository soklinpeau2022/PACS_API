CREATE TABLE IF NOT EXISTS services (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    code        VARCHAR(100) NOT NULL,
    description TEXT,
    is_active   SMALLINT NOT NULL DEFAULT 1,
    created_by  BIGINT,
    modified_by BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_services_name_active
    ON services (LOWER(name))
    WHERE is_active = 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_services_code_active
    ON services (LOWER(code))
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_services_active_id_desc
    ON services (is_active, id DESC);

ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(50);

ALTER TABLE pacs_patient_queue
    ADD COLUMN IF NOT EXISTS service_id BIGINT REFERENCES services(id),
    ADD COLUMN IF NOT EXISTS modality_id BIGINT REFERENCES modalities(id),
    ADD COLUMN IF NOT EXISTS visit_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS study_uuid VARCHAR(255),
    ADD COLUMN IF NOT EXISTS translated_by BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS translated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_by BIGINT,
    ADD COLUMN IF NOT EXISTS modified_by BIGINT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_patient_queue_hospital_visit_code
    ON pacs_patient_queue (hospital_id, visit_code)
    WHERE visit_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_visit_code
    ON pacs_patient_queue (visit_code);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_hospital_service_status_created
    ON pacs_patient_queue (hospital_id, service_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_hospital_modality_status_created
    ON pacs_patient_queue (hospital_id, modality_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_study_uuid
    ON pacs_patient_queue (study_uuid);

CREATE TABLE IF NOT EXISTS pacs_visit_sequences (
    id            BIGSERIAL PRIMARY KEY,
    hospital_id   BIGINT NOT NULL REFERENCES hospitals(id),
    sequence_date VARCHAR(8) NOT NULL,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    modified_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (hospital_id, sequence_date)
);
