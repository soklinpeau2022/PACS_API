\set ON_ERROR_STOP on
\timing on

-- Run after prepare_scale_lab.sql or against a disposable DEV database with
-- representative data. These are the final acceptance query shapes for
-- EXPLAIN (ANALYZE, BUFFERS) inspection.

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, patient_id, modality_id, status, scheduled_date, created_at
FROM pacs_scale_lab.worklists
WHERE hospital_id = 5
  AND status = 1
  AND (created_at, id) < (NOW(), 9223372036854775807)
ORDER BY created_at DESC, id DESC
LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, study_instance_uid, accession_number, modality_id, received_at
FROM pacs_scale_lab.studies
WHERE hospital_id = 5
  AND is_active = 1
  AND received_at >= NOW() - INTERVAL '90 days'
  AND received_at < NOW() + INTERVAL '1 day'
  AND (received_at, id) < (NOW(), 9223372036854775807)
ORDER BY received_at DESC, id DESC
LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, patient_uid
FROM pacs_scale_lab.patients
WHERE hospital_id = 5
  AND LOWER(patient_uid) = LOWER('PID-000000100005')
LIMIT 1;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, first_name, last_name
FROM pacs_scale_lab.patients
WHERE hospital_id = 5
  AND is_active = 1
  AND LOWER(first_name) >= LOWER('First123')
  AND LOWER(first_name) < LOWER('First123') || chr(255)
ORDER BY id DESC
LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, patient_id, status, created_at
FROM pacs_scale_lab.results
WHERE hospital_id = 5
  AND patient_id = 100005
  AND is_active = 1
ORDER BY created_at DESC, id DESC
LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, image_path, sort_order
FROM pacs_scale_lab.result_images
WHERE hospital_id = 5
  AND result_id = 100005
  AND is_active = 1
ORDER BY sort_order ASC, id ASC;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
INSERT INTO pacs_scale_lab.callback_log (
    id,
    hospital_id,
    dicom_server_id,
    dedupe_key,
    payload_sha256,
    event,
    accession_number,
    success,
    received_at,
    attempt_count
) VALUES (
    9223372036854775807,
    5,
    5,
    'bench-dedupe-key',
    repeat('a', 64),
    'BENCH',
    'BENCH-ACC',
    TRUE,
    NOW(),
    1
)
ON CONFLICT (hospital_id, dedupe_key)
WHERE dedupe_key IS NOT NULL
DO UPDATE SET
    attempt_count = pacs_scale_lab.callback_log.attempt_count + 1,
    received_at = EXCLUDED.received_at,
    success = pacs_scale_lab.callback_log.success OR EXCLUDED.success;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT hospital_id, stat_date, modality_id, waiting_count, received_study_count
FROM pacs_scale_lab.daily_stats
WHERE hospital_id = 5
  AND stat_date BETWEEN CURRENT_DATE - INTERVAL '30 days' AND CURRENT_DATE
ORDER BY stat_date DESC, modality_id ASC;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, event_type, title, message, worklist_id, study_id, created_at
FROM pacs_scale_lab.notification_events
WHERE hospital_id = 5
  AND id > 100000
ORDER BY id ASC
LIMIT 100;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, action, created_at
FROM pacs_scale_lab.activity_log
WHERE created_at >= date_trunc('month', now())
  AND created_at < date_trunc('month', now()) + INTERVAL '1 month'
ORDER BY created_at DESC, id DESC
LIMIT 20;
