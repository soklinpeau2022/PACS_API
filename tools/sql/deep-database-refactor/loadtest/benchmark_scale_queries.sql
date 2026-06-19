\set ON_ERROR_STOP on
\timing on

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, study_instance_uid, accession_number, received_at
FROM pacs_scale_lab.studies
WHERE hospital_id = 5
  AND is_active = 1
ORDER BY received_at DESC, id DESC
LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
WITH cursor AS (
    SELECT received_at, id
    FROM pacs_scale_lab.studies
    WHERE hospital_id = 5
      AND is_active = 1
    ORDER BY received_at DESC, id DESC
    OFFSET 10000
    LIMIT 1
)
SELECT studies.id, studies.study_instance_uid, studies.accession_number, studies.received_at
FROM pacs_scale_lab.studies studies, cursor
WHERE studies.hospital_id = 5
  AND studies.is_active = 1
  AND (studies.received_at, studies.id) < (cursor.received_at, cursor.id)
ORDER BY studies.received_at DESC, studies.id DESC
LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id
FROM pacs_scale_lab.studies
WHERE hospital_id = 5
  AND study_instance_uid = '1.2.826.0.1.3680043.10.100004';

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, study_instance_uid, received_at
FROM pacs_scale_lab.studies
WHERE hospital_id = 5
  AND patient_id = 100005
  AND is_active = 1
ORDER BY received_at DESC, id DESC
LIMIT 20;

-- ---------------------------------------------------------------------------
-- Worklist list (keyset) -- should use idx_scale_worklists_hospital_status_created.
-- ---------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, patient_id, modality_id, status
FROM pacs_scale_lab.worklists
WHERE hospital_id = 5
  AND status = 1
  AND (created_at, id) < (NOW(), 4000000000)
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Active-queue snapshot (dashboard) -- should use the partial active index.
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT status, COUNT(1)
FROM pacs_scale_lab.worklists
WHERE hospital_id = 5
  AND status = ANY (ARRAY[1, 2, 3, 4])
  AND study_id IS NULL
  AND image_received_at IS NULL
GROUP BY status;

-- ---------------------------------------------------------------------------
-- Patient search -- exact uid, then name prefix, then trigram fuzzy.
-- ---------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, patient_uid FROM pacs_scale_lab.patients
WHERE hospital_id = 5 AND LOWER(patient_uid) = lower('PID-000000100005');

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, first_name, last_name FROM pacs_scale_lab.patients
WHERE hospital_id = 5 AND is_active = 1
  AND LOWER(first_name) LIKE lower('First123') || '%'
ORDER BY id DESC LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, patient_uid FROM pacs_scale_lab.patients
WHERE patient_uid ILIKE '%100005%'
LIMIT 20;

-- ---------------------------------------------------------------------------
-- Result, image, event cursor, and dashboard paths.
-- ---------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, study_id, worklist_id, status, created_at
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
SELECT id, event_type, title, message, worklist_id, study_id, created_at
FROM pacs_scale_lab.notification_events
WHERE hospital_id = 5
  AND id > 100000
ORDER BY id ASC
LIMIT 100;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
INSERT INTO pacs_scale_lab.callback_log (
    id,
    hospital_id,
    dicom_server_id,
    dedupe_key,
    payload_sha256,
    event,
    accession_number,
    received_at,
    attempt_count,
    success
) VALUES (
    9223372036854775807,
    5,
    5,
    'callback-benchmark',
    repeat('a', 64),
    'BENCH',
    'BENCH-ACC',
    NOW(),
    1,
    TRUE
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
  AND stat_date BETWEEN current_date - INTERVAL '30 days' AND current_date
ORDER BY stat_date DESC, modality_id ASC;

-- ---------------------------------------------------------------------------
-- Partition pruning -- single-month filter should scan ONE partition only.
-- ---------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT id, action, created_at
FROM pacs_scale_lab.activity_log
WHERE created_at >= date_trunc('month', now())
  AND created_at <  date_trunc('month', now()) + interval '1 month'
ORDER BY created_at DESC, id DESC
LIMIT 20;
