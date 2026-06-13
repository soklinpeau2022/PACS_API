-- Keep PACS study identifiers in pacs_studies, and keep link/result tables ID-based.
-- Older migrations intentionally copied these values for compatibility; this migration
-- backfills the relations, then removes the repeated text/URL columns.

INSERT INTO pacs_studies (
    hospital_id,
    patient_id,
    study_instance_uid,
    accession_number,
    modality,
    study_date,
    study_description,
    status,
    dicom_server_study_id,
    dicom_server_patient_id,
    dicom_server_series_id,
    viewer_url,
    received_at,
    is_active,
    created,
    modified
)
SELECT
    q.hospital_id,
    q.patient_id,
    COALESCE(NULLIF(q.study_instance_uid, ''), NULLIF(q.study_uuid, ''), NULLIF(q.dicom_server_study_id, '')),
    NULLIF(q.accession_number, ''),
    NULLIF(q.modality_code, ''),
    q.scheduled_date,
    NULLIF(q.study_description, ''),
    1,
    NULLIF(q.dicom_server_study_id, ''),
    NULLIF(q.dicom_server_patient_id, ''),
    NULLIF(q.dicom_server_series_id, ''),
    NULLIF(q.viewer_url, ''),
    COALESCE(q.image_received_at, q.received_at, NOW()),
    1,
    COALESCE(q.created_at, q.created, NOW()),
    NOW()
FROM pacs_worklists q
WHERE COALESCE(NULLIF(q.study_instance_uid, ''), NULLIF(q.study_uuid, ''), NULLIF(q.dicom_server_study_id, '')) IS NOT NULL
ON CONFLICT (study_instance_uid) DO UPDATE
SET
    accession_number = COALESCE(EXCLUDED.accession_number, pacs_studies.accession_number),
    modality = COALESCE(EXCLUDED.modality, pacs_studies.modality),
    study_date = COALESCE(EXCLUDED.study_date, pacs_studies.study_date),
    study_description = COALESCE(EXCLUDED.study_description, pacs_studies.study_description),
    dicom_server_study_id = COALESCE(EXCLUDED.dicom_server_study_id, pacs_studies.dicom_server_study_id),
    dicom_server_patient_id = COALESCE(EXCLUDED.dicom_server_patient_id, pacs_studies.dicom_server_patient_id),
    dicom_server_series_id = COALESCE(EXCLUDED.dicom_server_series_id, pacs_studies.dicom_server_series_id),
    viewer_url = COALESCE(EXCLUDED.viewer_url, pacs_studies.viewer_url),
    received_at = COALESCE(EXCLUDED.received_at, pacs_studies.received_at),
    modified = NOW();

UPDATE pacs_worklists q
SET study_id = s.id
FROM pacs_studies s
WHERE q.study_id IS NULL
  AND s.hospital_id = q.hospital_id
  AND s.is_active = 1
  AND (
      (NULLIF(q.study_instance_uid, '') IS NOT NULL AND s.study_instance_uid = q.study_instance_uid)
      OR (NULLIF(q.study_uuid, '') IS NOT NULL AND s.study_instance_uid = q.study_uuid)
      OR (NULLIF(q.dicom_server_study_id, '') IS NOT NULL AND s.dicom_server_study_id = q.dicom_server_study_id)
      OR (NULLIF(q.accession_number, '') IS NOT NULL AND s.accession_number = q.accession_number)
  );

INSERT INTO pacs_worklist_study_links (
    hospital_id,
    worklist_id,
    study_id,
    is_primary,
    linked_at,
    created_by
)
SELECT
    q.hospital_id,
    q.id,
    q.study_id,
    1,
    COALESCE(q.image_received_at, q.received_at, NOW()),
    q.modified_by
FROM pacs_worklists q
WHERE q.study_id IS NOT NULL
ON CONFLICT (hospital_id, worklist_id, study_id) DO UPDATE
SET
    is_primary = 1,
    linked_at = COALESCE(pacs_worklist_study_links.linked_at, EXCLUDED.linked_at);

UPDATE pacs_results pr
SET study_id = s.id
FROM pacs_studies s
WHERE pr.study_id IS NULL
  AND s.hospital_id = pr.hospital_id
  AND s.is_active = 1
  AND (
      (NULLIF(pr.study_instance_uid, '') IS NOT NULL AND s.study_instance_uid = pr.study_instance_uid)
      OR (NULLIF(pr.accession_number, '') IS NOT NULL AND s.accession_number = pr.accession_number)
  );

UPDATE pacs_results pr
SET worklist_id = q.id
FROM pacs_worklists q
WHERE pr.worklist_id IS NULL
  AND q.hospital_id = pr.hospital_id
  AND (
      (pr.study_id IS NOT NULL AND q.study_id = pr.study_id)
      OR (NULLIF(pr.accession_number, '') IS NOT NULL AND q.accession_number = pr.accession_number)
      OR (NULLIF(pr.worklist_code, '') IS NOT NULL AND q.dicom_server_worklist_id = pr.worklist_code)
  );

UPDATE pacs_results pr
SET patient_id = s.patient_id
FROM pacs_studies s
WHERE pr.patient_id IS NULL
  AND pr.study_id IS NOT NULL
  AND s.id = pr.study_id
  AND s.hospital_id = pr.hospital_id;

UPDATE pacs_results pr
SET patient_id = q.patient_id
FROM pacs_worklists q
WHERE pr.patient_id IS NULL
  AND pr.worklist_id IS NOT NULL
  AND q.id = pr.worklist_id
  AND q.hospital_id = pr.hospital_id;

DROP INDEX IF EXISTS idx_pacs_worklists_active_hospital_status_schedule_id_desc;
DROP INDEX IF EXISTS idx_pacs_worklists_active_patient_modality;
DROP INDEX IF EXISTS idx_pacs_worklists_dicom_server_study_id;
DROP INDEX IF EXISTS idx_pacs_worklists_study_uuid;
DROP INDEX IF EXISTS idx_pacs_worklist_study_links_hospital_accession;
DROP INDEX IF EXISTS ux_pacs_results_hospital_modality_study_uid_active;
DROP INDEX IF EXISTS ux_pacs_results_hospital_modality_accession_active;
DROP INDEX IF EXISTS idx_pacs_results_patient_code;

ALTER TABLE pacs_worklists
    DROP COLUMN IF EXISTS study_uuid,
    DROP COLUMN IF EXISTS dicom_server_study_id,
    DROP COLUMN IF EXISTS study_instance_uid,
    DROP COLUMN IF EXISTS dicom_server_patient_id,
    DROP COLUMN IF EXISTS dicom_server_series_id,
    DROP COLUMN IF EXISTS viewer_url;

ALTER TABLE pacs_worklist_study_links
    DROP COLUMN IF EXISTS accession_number,
    DROP COLUMN IF EXISTS dicom_server_study_id,
    DROP COLUMN IF EXISTS study_instance_uid;

ALTER TABLE pacs_results
    DROP COLUMN IF EXISTS worklist_code,
    DROP COLUMN IF EXISTS study_instance_uid,
    DROP COLUMN IF EXISTS accession_number,
    DROP COLUMN IF EXISTS patient_code,
    DROP COLUMN IF EXISTS patient_name;

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_active_hospital_status_schedule_id_desc
    ON pacs_worklists (hospital_id, status, scheduled_date, id DESC)
    WHERE status IN (1, 2, 3, 4)
      AND study_id IS NULL
      AND COALESCE(image_received_at, received_at) IS NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_active_patient_modality
    ON pacs_worklists (hospital_id, patient_id, modality_id, status, id DESC)
    WHERE status IN (1, 2, 4)
      AND study_id IS NULL
      AND COALESCE(image_received_at, received_at) IS NULL;
