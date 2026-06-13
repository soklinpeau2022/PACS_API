-- Backfill direct Queue -> Study links for legacy rows that were synchronized
-- before the explicit study_id / link-table refactor landed.

UPDATE pacs_studies s
SET
    dicom_server_study_id = COALESCE(s.dicom_server_study_id, q.dicom_server_study_id),
    dicom_server_patient_id = COALESCE(s.dicom_server_patient_id, q.dicom_server_patient_id),
    dicom_server_series_id = COALESCE(s.dicom_server_series_id, q.dicom_server_series_id),
    viewer_url = COALESCE(s.viewer_url, q.viewer_url),
    received_at = COALESCE(s.received_at, q.image_received_at, q.received_at, q.modified_at, q.created_at),
    modified = NOW()
FROM pacs_patient_queue q
WHERE q.hospital_id = s.hospital_id
  AND (
        (q.study_instance_uid IS NOT NULL AND q.study_instance_uid = s.study_instance_uid)
        OR (q.dicom_server_study_id IS NOT NULL AND q.dicom_server_study_id = s.dicom_server_study_id)
        OR (q.accession_number IS NOT NULL AND q.accession_number = s.accession_number)
  )
  AND (
        s.dicom_server_study_id IS NULL
        OR s.dicom_server_patient_id IS NULL
        OR s.dicom_server_series_id IS NULL
        OR s.viewer_url IS NULL
        OR s.received_at IS NULL
  );

UPDATE pacs_patient_queue q
SET study_id = s.id
FROM pacs_studies s
WHERE q.study_id IS NULL
  AND q.hospital_id = s.hospital_id
  AND (
        (q.study_instance_uid IS NOT NULL AND q.study_instance_uid = s.study_instance_uid)
        OR (q.dicom_server_study_id IS NOT NULL AND q.dicom_server_study_id = s.dicom_server_study_id)
        OR (q.accession_number IS NOT NULL AND q.accession_number = s.accession_number)
  );

INSERT INTO pacs_queue_study_links (
    hospital_id,
    queue_id,
    study_id,
    accession_number,
    dicom_server_study_id,
    study_instance_uid,
    is_primary,
    linked_at,
    created_by
)
SELECT
    q.hospital_id,
    q.id,
    q.study_id,
    q.accession_number,
    COALESCE(q.dicom_server_study_id, s.dicom_server_study_id),
    COALESCE(q.study_instance_uid, s.study_instance_uid),
    1,
    COALESCE(q.image_received_at, q.received_at, q.modified_at, q.created_at, NOW()),
    q.modified_by
FROM pacs_patient_queue q
INNER JOIN pacs_studies s
    ON s.id = q.study_id
LEFT JOIN pacs_queue_study_links qsl
    ON qsl.hospital_id = q.hospital_id
   AND qsl.queue_id = q.id
   AND qsl.study_id = q.study_id
WHERE q.study_id IS NOT NULL
  AND qsl.id IS NULL;

