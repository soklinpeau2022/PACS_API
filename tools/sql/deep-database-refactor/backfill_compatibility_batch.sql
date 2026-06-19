\set ON_ERROR_STOP on
\pset pager off

\if :{?batch_size}
\else
\set batch_size 5000
\endif

-- Run repeatedly. Each statement updates at most batch_size rows and uses
-- SKIP LOCKED so multiple workers can safely cooperate.

WITH batch AS (
    SELECT id
    FROM patients
    WHERE created_at IS NULL OR updated_at IS NULL
    ORDER BY id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
UPDATE patients target
SET
    created_at = COALESCE(target.created_at, target.created, NOW()),
    updated_at = COALESCE(target.updated_at, target.modified, target.created, NOW())
FROM batch
WHERE target.id = batch.id;

WITH batch AS (
    SELECT id
    FROM pacs_studies
    WHERE created_at IS NULL OR updated_at IS NULL
    ORDER BY id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
UPDATE pacs_studies target
SET
    created_at = COALESCE(target.created_at, target.created, NOW()),
    updated_at = COALESCE(target.updated_at, target.modified, target.created, NOW())
FROM batch
WHERE target.id = batch.id;

WITH batch AS (
    SELECT id
    FROM pacs_worklist_histories
    WHERE created_at IS NULL
    ORDER BY id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
UPDATE pacs_worklist_histories target
SET created_at = COALESCE(target.created_at, target.created, NOW())
FROM batch
WHERE target.id = batch.id;

WITH batch AS (
    SELECT id
    FROM system_activities
    WHERE created_at IS NULL
    ORDER BY id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
UPDATE system_activities target
SET created_at = COALESCE(target.created_at, target.created, NOW())
FROM batch
WHERE target.id = batch.id;

WITH batch AS (
    SELECT id
    FROM user_logs
    WHERE created_at IS NULL
    ORDER BY id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
UPDATE user_logs target
SET created_at = COALESCE(target.created_at, target.created, NOW())
FROM batch
WHERE target.id = batch.id;

WITH batch AS (
    SELECT id
    FROM dicom_server_callback_log
    WHERE last_received_at IS NULL
    ORDER BY id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
UPDATE dicom_server_callback_log target
SET last_received_at = target.received_at
FROM batch
WHERE target.id = batch.id;

WITH batch AS (
    SELECT i.id
    FROM pacs_result_images i
    WHERE i.hospital_id IS NULL
       OR i.modality_id IS NULL
    ORDER BY i.id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
UPDATE pacs_result_images target
SET
    hospital_id = result.hospital_id,
    modality_id = result.modality_id,
    study_id = result.study_id,
    worklist_id = result.worklist_id
FROM batch
JOIN pacs_results result
  ON result.id = (
      SELECT image.result_id
      FROM pacs_result_images image
      WHERE image.id = batch.id
  )
WHERE target.id = batch.id;

WITH batch AS (
    SELECT w.id
    FROM pacs_worklists w
    WHERE w.study_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM pacs_worklist_study_links l
          WHERE l.hospital_id = w.hospital_id
            AND l.worklist_id = w.id
            AND l.study_id = w.study_id
      )
    ORDER BY w.id
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
INSERT INTO pacs_worklist_study_links (
    hospital_id,
    worklist_id,
    study_id,
    is_primary,
    linked_at,
    created_by
)
SELECT
    w.hospital_id,
    w.id,
    w.study_id,
    1,
    COALESCE(w.image_received_at, w.received_at, NOW()),
    w.modified_by
FROM pacs_worklists w
JOIN batch ON batch.id = w.id
ON CONFLICT (hospital_id, worklist_id, study_id)
DO UPDATE SET is_primary = 1;

SELECT
    (SELECT COUNT(*) FROM patients WHERE created_at IS NULL OR updated_at IS NULL) AS patients_remaining,
    (SELECT COUNT(*) FROM pacs_studies WHERE created_at IS NULL OR updated_at IS NULL) AS studies_remaining,
    (SELECT COUNT(*) FROM pacs_worklist_histories WHERE created_at IS NULL) AS histories_remaining,
    (SELECT COUNT(*) FROM system_activities WHERE created_at IS NULL) AS activities_remaining,
    (SELECT COUNT(*) FROM user_logs WHERE created_at IS NULL) AS user_logs_remaining,
    (SELECT COUNT(*) FROM dicom_server_callback_log WHERE last_received_at IS NULL) AS callback_logs_remaining,
    (SELECT COUNT(*) FROM pacs_result_images WHERE hospital_id IS NULL OR modality_id IS NULL) AS result_images_remaining;
