\set ON_ERROR_STOP on
\pset pager off

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    id,
    public_id,
    hospital_id,
    patient_id,
    modality_id,
    visit_code,
    status,
    scheduled_date,
    created_at
FROM pacs_worklists_week_cache
WHERE hospital_id = 1
  AND status = 1
ORDER BY created_at DESC, id DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    id,
    public_id,
    hospital_id,
    patient_id,
    modality_id,
    study_instance_uid,
    accession_number,
    study_date,
    received_at
FROM pacs_studies_week_cache
WHERE hospital_id = 1
ORDER BY received_at DESC, id DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    id,
    event,
    success,
    received_at,
    accession_number
FROM dicom_server_callback_log
WHERE hospital_id = 1
  AND received_at >= NOW() - INTERVAL '30 days'
ORDER BY received_at DESC, id DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS)
SELECT
    id,
    from_status,
    to_status,
    action,
    reason,
    created,
    created_by
FROM pacs_worklist_histories
WHERE hospital_id = 1
  AND worklist_id = 100
ORDER BY created DESC, id DESC
LIMIT 100;
