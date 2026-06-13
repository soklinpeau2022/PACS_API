-- Build missing pacs_studies rows from legacy queues that already have received-study data.
-- This keeps the study archive usable immediately after upgrade, even before any new queue syncs occur.

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
    q.accession_number,
    COALESCE(NULLIF(q.modality_code, ''), NULLIF(m.abbr, ''), NULLIF(m.name, '')),
    COALESCE(q.scheduled_date, CAST(COALESCE(q.image_received_at, q.received_at, q.created_at, q.created) AS DATE)),
    q.study_description,
    CASE
        WHEN q.status = 6 THEN 3
        WHEN q.status = 7 THEN 4
        ELSE 2
    END,
    q.dicom_server_study_id,
    q.dicom_server_patient_id,
    q.dicom_server_series_id,
    q.viewer_url,
    COALESCE(q.image_received_at, q.received_at, q.created_at, q.created),
    1,
    COALESCE(q.created_at, q.created, NOW()),
    NOW()
FROM pacs_patient_queue q
LEFT JOIN modalities m ON m.id = q.modality_id
WHERE COALESCE(NULLIF(q.study_instance_uid, ''), NULLIF(q.study_uuid, ''), NULLIF(q.dicom_server_study_id, '')) IS NOT NULL
ON CONFLICT (study_instance_uid) DO UPDATE
SET
    accession_number = COALESCE(EXCLUDED.accession_number, pacs_studies.accession_number),
    modality = COALESCE(EXCLUDED.modality, pacs_studies.modality),
    study_date = COALESCE(EXCLUDED.study_date, pacs_studies.study_date),
    study_description = COALESCE(EXCLUDED.study_description, pacs_studies.study_description),
    status = GREATEST(COALESCE(pacs_studies.status, 1), COALESCE(EXCLUDED.status, 1)),
    dicom_server_study_id = COALESCE(EXCLUDED.dicom_server_study_id, pacs_studies.dicom_server_study_id),
    dicom_server_patient_id = COALESCE(EXCLUDED.dicom_server_patient_id, pacs_studies.dicom_server_patient_id),
    dicom_server_series_id = COALESCE(EXCLUDED.dicom_server_series_id, pacs_studies.dicom_server_series_id),
    viewer_url = COALESCE(EXCLUDED.viewer_url, pacs_studies.viewer_url),
    received_at = COALESCE(EXCLUDED.received_at, pacs_studies.received_at),
    modified = NOW();

UPDATE pacs_patient_queue q
SET study_id = s.id
FROM pacs_studies s
WHERE q.study_id IS NULL
  AND q.hospital_id = s.hospital_id
  AND COALESCE(NULLIF(q.study_instance_uid, ''), NULLIF(q.study_uuid, ''), NULLIF(q.dicom_server_study_id, '')) = s.study_instance_uid;

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
    COALESCE(q.image_received_at, q.received_at, q.modified_at, q.created_at, q.created, NOW()),
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

