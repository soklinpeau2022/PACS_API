CREATE TABLE IF NOT EXISTS pacs_results (
    id                  BIGSERIAL PRIMARY KEY,
    hospital_id          BIGINT NOT NULL REFERENCES hospitals(id),
    modality_id          BIGINT NOT NULL REFERENCES modalities(id),
    study_id             BIGINT REFERENCES pacs_studies(id),
    queue_id             BIGINT REFERENCES pacs_patient_queue(id),
    worklist_id          VARCHAR(120),
    study_instance_uid   VARCHAR(200),
    accession_number     VARCHAR(100),
    patient_id           BIGINT REFERENCES patients(id),
    patient_code         VARCHAR(100),
    patient_name         VARCHAR(255),
    result_date          DATE NOT NULL DEFAULT CURRENT_DATE,
    template_id          BIGINT,
    result_text          TEXT,
    status               VARCHAR(30) NOT NULL DEFAULT 'IMAGE_RECEIVED',
    completed            BOOLEAN NOT NULL DEFAULT FALSE,
    is_active            SMALLINT NOT NULL DEFAULT 1,
    created_by           BIGINT REFERENCES users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_pacs_results_status
        CHECK (status IN ('IMAGE_RECEIVED', 'COMPLETED')),
    CONSTRAINT chk_pacs_results_active
        CHECK (is_active IN (1, 2))
);

CREATE TABLE IF NOT EXISTS pacs_result_images (
    id                  BIGSERIAL PRIMARY KEY,
    result_id            BIGINT NOT NULL REFERENCES pacs_results(id) ON DELETE CASCADE,
    hospital_id          BIGINT NOT NULL REFERENCES hospitals(id),
    modality_id          BIGINT NOT NULL REFERENCES modalities(id),
    image_path           TEXT NOT NULL,
    original_file_name   VARCHAR(255),
    file_type            VARCHAR(80),
    file_size            BIGINT,
    sort_order           INTEGER NOT NULL DEFAULT 0,
    is_active            SMALLINT NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_pacs_result_images_active
        CHECK (is_active IN (1, 2))
);

CREATE TABLE IF NOT EXISTS pacs_result_templates (
    id                  BIGSERIAL PRIMARY KEY,
    hospital_id          BIGINT NOT NULL REFERENCES hospitals(id),
    modality_id          BIGINT NOT NULL REFERENCES modalities(id),
    template_name        VARCHAR(150) NOT NULL,
    template_content     TEXT,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_results_template') THEN
        ALTER TABLE pacs_results
            ADD CONSTRAINT fk_pacs_results_template
                FOREIGN KEY (template_id) REFERENCES pacs_result_templates(id)
                DEFERRABLE INITIALLY IMMEDIATE;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_results_hospital_modality_study_active
    ON pacs_results (hospital_id, modality_id, study_id)
    WHERE is_active = 1 AND study_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_results_hospital_modality_queue_active
    ON pacs_results (hospital_id, modality_id, queue_id)
    WHERE is_active = 1 AND queue_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_results_hospital_modality_study_uid_active
    ON pacs_results (hospital_id, modality_id, study_instance_uid)
    WHERE is_active = 1 AND study_instance_uid IS NOT NULL AND study_instance_uid <> '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_results_hospital_modality_accession_active
    ON pacs_results (hospital_id, modality_id, accession_number)
    WHERE is_active = 1 AND accession_number IS NOT NULL AND accession_number <> '';

CREATE INDEX IF NOT EXISTS idx_pacs_results_hospital_status_created
    ON pacs_results (hospital_id, status, created_at DESC)
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_pacs_results_patient
    ON pacs_results (hospital_id, patient_id, created_at DESC)
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_pacs_results_patient_code
    ON pacs_results (hospital_id, patient_code)
    WHERE is_active = 1 AND patient_code IS NOT NULL AND patient_code <> '';

CREATE INDEX IF NOT EXISTS idx_pacs_result_images_result_active
    ON pacs_result_images (result_id, is_active, sort_order, id);

CREATE INDEX IF NOT EXISTS idx_pacs_result_images_hospital_modality
    ON pacs_result_images (hospital_id, modality_id, created_at DESC)
    WHERE is_active = 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_result_templates_name_active
    ON pacs_result_templates (hospital_id, modality_id, LOWER(template_name))
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_pacs_result_templates_hospital_modality
    ON pacs_result_templates (hospital_id, modality_id, active, template_name);
