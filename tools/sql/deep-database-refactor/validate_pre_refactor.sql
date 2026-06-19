\set ON_ERROR_STOP on
\pset pager off

-- Every result set must be empty before V186-V188 are promoted to production.

SELECT hospital_id, LOWER(patient_uid) AS normalized_patient_uid, COUNT(*)
FROM patients
GROUP BY hospital_id, LOWER(patient_uid)
HAVING COUNT(*) > 1;

SELECT hospital_id, LOWER(visit_code) AS normalized_visit_code, COUNT(*)
FROM pacs_worklists
WHERE NULLIF(BTRIM(visit_code), '') IS NOT NULL
GROUP BY hospital_id, LOWER(visit_code)
HAVING COUNT(*) > 1;

SELECT hospital_id, study_instance_uid, COUNT(*)
FROM pacs_studies
GROUP BY hospital_id, study_instance_uid
HAVING COUNT(*) > 1;

SELECT w.id, w.hospital_id, w.patient_id, p.hospital_id AS patient_hospital_id
FROM pacs_worklists w
JOIN patients p ON p.id = w.patient_id
WHERE p.hospital_id <> w.hospital_id;

SELECT s.id, s.hospital_id, s.patient_id, p.hospital_id AS patient_hospital_id
FROM pacs_studies s
JOIN patients p ON p.id = s.patient_id
WHERE p.hospital_id <> s.hospital_id;

SELECT l.id, l.hospital_id, w.hospital_id AS worklist_hospital_id
FROM pacs_worklist_study_links l
JOIN pacs_worklists w ON w.id = l.worklist_id
WHERE w.hospital_id <> l.hospital_id;

SELECT l.id, l.hospital_id, s.hospital_id AS study_hospital_id
FROM pacs_worklist_study_links l
JOIN pacs_studies s ON s.id = l.study_id
WHERE s.hospital_id <> l.hospital_id;

SELECT r.id, r.hospital_id, r.study_id, s.hospital_id AS study_hospital_id
FROM pacs_results r
JOIN pacs_studies s ON s.id = r.study_id
WHERE r.study_id IS NOT NULL
  AND s.hospital_id <> r.hospital_id;

SELECT r.id, r.hospital_id, r.worklist_id, w.hospital_id AS worklist_hospital_id
FROM pacs_results r
JOIN pacs_worklists w ON w.id = r.worklist_id
WHERE r.worklist_id IS NOT NULL
  AND w.hospital_id <> r.hospital_id;

SELECT r.id, r.hospital_id, r.patient_id, p.hospital_id AS patient_hospital_id
FROM pacs_results r
JOIN patients p ON p.id = r.patient_id
WHERE r.patient_id IS NOT NULL
  AND p.hospital_id <> r.hospital_id;

SELECT r.id, r.hospital_id
FROM pacs_results r
WHERE r.study_id IS NULL
  AND r.worklist_id IS NULL;

SELECT hospital_id, study_id, COUNT(*)
FROM pacs_results
WHERE is_active = 1
  AND study_id IS NOT NULL
GROUP BY hospital_id, study_id
HAVING COUNT(*) > 1;

SELECT hospital_id, worklist_id, COUNT(*)
FROM pacs_results
WHERE is_active = 1
  AND worklist_id IS NOT NULL
GROUP BY hospital_id, worklist_id
HAVING COUNT(*) > 1;

SELECT v.id, v.hospital_id, v.study_id, s.hospital_id AS study_hospital_id
FROM pacs_viewer_states v
JOIN pacs_studies s ON s.id = v.study_id
WHERE v.study_id IS NOT NULL
  AND s.hospital_id <> v.hospital_id;

SELECT v.id, v.hospital_id, v.worklist_id, w.hospital_id AS worklist_hospital_id
FROM pacs_viewer_states v
JOIN pacs_worklists w ON w.id = v.worklist_id
WHERE v.worklist_id IS NOT NULL
  AND w.hospital_id <> v.hospital_id;

SELECT v.id, v.hospital_id, v.patient_id, p.hospital_id AS patient_hospital_id
FROM pacs_viewer_states v
JOIN patients p ON p.id = v.patient_id
WHERE v.patient_id IS NOT NULL
  AND p.hospital_id <> v.hospital_id;

SELECT i.id, i.result_id
FROM pacs_result_images i
LEFT JOIN pacs_results r ON r.id = i.result_id
WHERE r.id IS NULL;

SELECT hospital_id, study_id, state_type, COUNT(*)
FROM pacs_viewer_states
WHERE is_active = 1
  AND worklist_id IS NULL
  AND study_id IS NOT NULL
GROUP BY hospital_id, study_id, state_type
HAVING COUNT(*) > 1;

SELECT hospital_id, worklist_id, state_type, COUNT(*)
FROM pacs_viewer_states
WHERE is_active = 1
  AND worklist_id IS NOT NULL
GROUP BY hospital_id, worklist_id, state_type
HAVING COUNT(*) > 1;

SELECT w.id, w.hospital_id, w.study_id
FROM pacs_worklists w
WHERE w.study_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM pacs_worklist_study_links l
      WHERE l.hospital_id = w.hospital_id
        AND l.worklist_id = w.id
        AND l.study_id = w.study_id
  );

SELECT l.id, l.worklist_id, l.study_id
FROM pacs_worklist_study_links l
LEFT JOIN pacs_worklists w ON w.id = l.worklist_id
LEFT JOIN pacs_studies s ON s.id = l.study_id
WHERE w.id IS NULL
   OR s.id IS NULL;

SELECT h.id, h.worklist_id, h.patient_id
FROM pacs_worklist_histories h
LEFT JOIN pacs_worklists w ON w.id = h.worklist_id
LEFT JOIN patients p ON p.id = h.patient_id
WHERE w.id IS NULL
   OR p.id IS NULL;

SELECT id, hospital_id, payload_size_bytes, payload_sha256
FROM pacs_viewer_states
WHERE payload_size_bytes < 0
   OR payload_size_bytes > 10485760
   OR (payload_sha256 IS NOT NULL AND payload_sha256 !~ '^[0-9a-f]{64}$');

SELECT
    COUNT(*) FILTER (WHERE created < NOW() - INTERVAL '6 months') AS old_system_activities
FROM system_activities;

SELECT
    COUNT(*) FILTER (WHERE created < NOW() - INTERVAL '6 months') AS old_user_logs
FROM user_logs;

SELECT
    COUNT(*) FILTER (WHERE success AND received_at < NOW() - INTERVAL '90 days') AS old_success_callbacks,
    COUNT(*) FILTER (WHERE NOT success AND received_at < NOW() - INTERVAL '365 days') AS old_failed_callbacks
FROM dicom_server_callback_log;

SELECT
    COUNT(*) FILTER (WHERE created_at < NOW() - INTERVAL '14 days') AS old_realtime_events
FROM pacs_realtime_notification_events;

SELECT
    relname,
    indexrelname,
    idx_scan,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC, relname, indexrelname;

SELECT
    relname,
    n_live_tup,
    n_dead_tup,
    pg_size_pretty(pg_relation_size(relid)) AS table_size,
    pg_size_pretty(pg_indexes_size(relid)) AS index_size,
    pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC;

