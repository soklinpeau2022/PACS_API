\set ON_ERROR_STOP on
\pset pager off

-- This script chooses existing sample values and runs the main mapper query
-- shapes. Run on staging with production-like statistics and data distribution.
SELECT
    COALESCE((SELECT id FROM hospitals ORDER BY id LIMIT 1), 0)::bigint AS hospital_id,
    COALESCE((SELECT visit_code FROM pacs_worklists WHERE visit_code IS NOT NULL ORDER BY id DESC LIMIT 1), '') AS visit_code,
    COALESCE((SELECT accession_number FROM pacs_studies WHERE accession_number IS NOT NULL ORDER BY id DESC LIMIT 1), '') AS accession_number,
    COALESCE((SELECT study_instance_uid FROM pacs_studies ORDER BY id DESC LIMIT 1), '') AS study_instance_uid,
    COALESCE((SELECT patient_id FROM pacs_studies ORDER BY id DESC LIMIT 1), 0)::bigint AS patient_id,
    COALESCE((SELECT worklist_id FROM pacs_results WHERE worklist_id IS NOT NULL ORDER BY id DESC LIMIT 1), 0)::bigint AS worklist_id,
    COALESCE((SELECT study_id FROM pacs_results WHERE study_id IS NOT NULL ORDER BY id DESC LIMIT 1), 0)::bigint AS study_id,
    COALESCE((SELECT state_type FROM pacs_viewer_states ORDER BY id DESC LIMIT 1), 'OHIF_VIEWER_STATE') AS state_type,
    COALESCE((SELECT id FROM pacs_realtime_notification_events ORDER BY id DESC LIMIT 1), 0)::bigint AS event_id
\gset

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT q.id
FROM pacs_worklists q
WHERE q.hospital_id = :hospital_id
  AND q.status IN (1, 2, 3, 4)
  AND q.study_id IS NULL
  AND COALESCE(q.image_received_at, q.received_at) IS NULL
ORDER BY q.id DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT q.id
FROM pacs_worklists q
WHERE q.hospital_id = :hospital_id
  AND LOWER(q.visit_code) = LOWER(:'visit_code')
LIMIT 1;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT s.id
FROM pacs_studies s
WHERE s.hospital_id = :hospital_id
  AND s.is_active = 1
ORDER BY COALESCE(s.image_received_at, s.received_at, s.study_date::timestamp, s.created) DESC,
         s.id DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT s.id
FROM pacs_studies s
WHERE s.hospital_id = :hospital_id
  AND s.is_active = 1
  AND LOWER(s.accession_number) = LOWER(:'accession_number')
LIMIT 1;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT s.id
FROM pacs_studies s
WHERE s.hospital_id = :hospital_id
  AND s.study_instance_uid = :'study_instance_uid'
LIMIT 1;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT s.id
FROM pacs_studies s
WHERE s.hospital_id = :hospital_id
  AND s.patient_id = :patient_id
  AND s.is_active = 1
ORDER BY s.study_date DESC NULLS LAST, s.id DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT pr.id
FROM pacs_results pr
WHERE pr.hospital_id = :hospital_id
  AND pr.worklist_id = :worklist_id
  AND pr.is_active = 1
ORDER BY pr.modified_at DESC, pr.id DESC
LIMIT 1;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT pr.id
FROM pacs_results pr
WHERE pr.hospital_id = :hospital_id
  AND pr.study_id = :study_id
  AND pr.is_active = 1
ORDER BY pr.modified_at DESC, pr.id DESC
LIMIT 1;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT pvs.id
FROM pacs_viewer_states pvs
WHERE pvs.hospital_id = :hospital_id
  AND pvs.study_id = :study_id
  AND pvs.state_type = :'state_type'
  AND pvs.is_active = 1
ORDER BY pvs.modified_at DESC, pvs.id DESC
LIMIT 1;

EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT event.id
FROM pacs_realtime_notification_events event
WHERE event.hospital_id = :hospital_id
  AND event.id > :event_id
ORDER BY event.id
LIMIT 100;
