-- Direct Queue -> Study linkage for scalable PACS archive and operational workflow queries.
-- This keeps queue as the hot operational table and study as the imaging/archive table,
-- while avoiding repeated fuzzy joins by accession/study UID on every study-list request.

ALTER TABLE pacs_studies
    ADD COLUMN IF NOT EXISTS dicom_server_study_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dicom_server_patient_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dicom_server_series_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS viewer_url TEXT,
    ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ;

ALTER TABLE pacs_patient_queue
    ADD COLUMN IF NOT EXISTS study_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_patient_queue_study_id') THEN
        ALTER TABLE pacs_patient_queue
            ADD CONSTRAINT fk_pacs_patient_queue_study_id
                FOREIGN KEY (study_id)
                REFERENCES pacs_studies(id)
                ON UPDATE RESTRICT
                ON DELETE SET NULL;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS pacs_queue_study_links (
    id BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
    queue_id BIGINT NOT NULL REFERENCES pacs_patient_queue(id) ON DELETE CASCADE,
    study_id BIGINT NOT NULL REFERENCES pacs_studies(id) ON DELETE CASCADE,
    accession_number VARCHAR(120),
    dicom_server_study_id VARCHAR(255),
    study_instance_uid VARCHAR(255),
    is_primary SMALLINT NOT NULL DEFAULT 1,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_queue_study_links_queue_study
    ON pacs_queue_study_links (hospital_id, queue_id, study_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_queue_study_links_primary_queue
    ON pacs_queue_study_links (hospital_id, queue_id)
    WHERE is_primary = 1;

CREATE INDEX IF NOT EXISTS idx_pacs_queue_study_links_hospital_study_linked_desc
    ON pacs_queue_study_links (hospital_id, study_id, linked_at DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_queue_study_links_hospital_accession
    ON pacs_queue_study_links (hospital_id, accession_number);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_hospital_study_id
    ON pacs_patient_queue (hospital_id, study_id)
    WHERE study_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_date_id_desc
    ON pacs_studies (hospital_id, study_date DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_accession
    ON pacs_studies (hospital_id, accession_number)
    WHERE accession_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_study_uid
    ON pacs_studies (hospital_id, study_instance_uid);

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_dicom_server_study_id
    ON pacs_studies (hospital_id, dicom_server_study_id)
    WHERE dicom_server_study_id IS NOT NULL;

