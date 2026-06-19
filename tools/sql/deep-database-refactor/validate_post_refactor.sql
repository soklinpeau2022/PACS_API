\set ON_ERROR_STOP on
\pset pager off

SELECT conrelid::regclass AS table_name,
       conname,
       convalidated,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conname IN (
    'fk_worklists_study_hospital',
    'fk_worklist_study_links_worklist_hospital',
    'fk_worklist_study_links_study_hospital',
    'fk_results_study_hospital',
    'fk_results_worklist_hospital',
    'fk_results_patient_hospital',
    'fk_result_images_result_hospital',
    'fk_viewer_states_study_hospital',
    'fk_viewer_states_worklist_hospital',
    'fk_viewer_states_patient_hospital',
    'fk_realtime_events_worklist_hospital',
    'fk_realtime_events_study_hospital',
    'fk_callback_log_server_hospital',
    'fk_result_versions_result_hospital'
)
ORDER BY conrelid::regclass::text, conname;

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'ux_pacs_studies_id_hospital',
      'ux_pacs_results_id_hospital',
      'ux_pacs_results_hospital_study_active',
      'ux_pacs_results_hospital_worklist_active',
      'ux_callback_log_hospital_dedupe',
      'idx_callback_log_hospital_received',
      'idx_pacs_result_images_hospital_result_active',
      'idx_pacs_result_versions_hospital_result_changed'
  )
ORDER BY indexname;

SELECT
    (SELECT COUNT(*) FROM patients WHERE created_at IS NULL OR updated_at IS NULL) AS patients_missing_audit_time,
    (SELECT COUNT(*) FROM pacs_studies WHERE created_at IS NULL OR updated_at IS NULL) AS studies_missing_audit_time,
    (SELECT COUNT(*) FROM pacs_result_images WHERE hospital_id IS NULL OR modality_id IS NULL) AS images_missing_scope,
    (SELECT COUNT(*) FROM pacs_worklists w
       WHERE w.study_id IS NOT NULL
         AND NOT EXISTS (
             SELECT 1
             FROM pacs_worklist_study_links l
             WHERE l.hospital_id = w.hospital_id
               AND l.worklist_id = w.id
               AND l.study_id = w.study_id
         )) AS worklist_link_mismatches;

SELECT tbl.relname AS table_name,
       idx.relname AS index_name,
       pi.indisvalid,
       pi.indisready,
       pi.indislive
FROM pg_index pi
JOIN pg_class idx ON idx.oid = pi.indexrelid
JOIN pg_class tbl ON tbl.oid = pi.indrelid
JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
WHERE ns.nspname = 'public'
  AND (NOT pi.indisvalid OR NOT pi.indisready OR NOT pi.indislive)
ORDER BY tbl.relname, idx.relname;

