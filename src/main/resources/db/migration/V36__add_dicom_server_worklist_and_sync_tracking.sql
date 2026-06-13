ALTER TABLE pacs_patient_queue
    ADD COLUMN IF NOT EXISTS accession_number VARCHAR(120),
    ADD COLUMN IF NOT EXISTS modality_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS machine_ae_title VARCHAR(100),
    ADD COLUMN IF NOT EXISTS dicom_server_worklist_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dicom_server_worklist_path VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dicom_server_study_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS study_instance_uid VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dicom_server_patient_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS viewer_url TEXT,
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ;

UPDATE pacs_patient_queue
SET accession_number = visit_code
WHERE (accession_number IS NULL OR accession_number = '')
  AND visit_code IS NOT NULL;

WITH duplicated_accessions AS (
    SELECT
        id,
        hospital_id,
        accession_number,
        ROW_NUMBER() OVER (PARTITION BY hospital_id, accession_number ORDER BY id) AS rn
    FROM pacs_patient_queue
    WHERE accession_number IS NOT NULL
      AND accession_number <> ''
)
UPDATE pacs_patient_queue q
SET accession_number = q.accession_number || '-' || q.id
FROM duplicated_accessions d
WHERE q.id = d.id
  AND d.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_patient_queue_hospital_accession
    ON pacs_patient_queue (hospital_id, accession_number)
    WHERE accession_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_status_accession
    ON pacs_patient_queue (status, accession_number);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_dicom_server_study_id
    ON pacs_patient_queue (dicom_server_study_id);
