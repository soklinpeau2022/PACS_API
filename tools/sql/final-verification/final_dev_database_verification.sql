\set ON_ERROR_STOP on
\pset pager off

-- Final DEV acceptance checks for the EMR/PACS large-scale database cleanup.
-- Every query should return zero rows for the anomaly sections, and TRUE for
-- the extension/partition checks.

SELECT extname
FROM pg_extension
WHERE extname IN ('pgcrypto', 'pg_trgm')
ORDER BY extname;

SELECT
    'legacy_uuid_default' AS check_name,
    table_name,
    column_name,
    column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND data_type = 'uuid'
  AND column_default IS NOT NULL
  AND column_default ~* '(md5|clock_timestamp)'
ORDER BY table_name, column_name;

SELECT
    COUNT(*) FILTER (WHERE contype = 'c') AS check_constraints,
    COUNT(*) FILTER (WHERE contype = 'f') AS foreign_keys,
    COUNT(*) FILTER (WHERE contype = 'u') AS unique_constraints
FROM pg_constraint c
JOIN pg_namespace n ON n.oid = c.connamespace
WHERE n.nspname = 'public';

SELECT
    relname AS partitioned_table,
    pg_get_partkeydef(oid) AS partition_key
FROM pg_class
WHERE relkind = 'p'
  AND relnamespace = 'public'::regnamespace
ORDER BY relname;

SELECT
    parent.relname AS parent_table,
    child.relname AS partition_name
FROM pg_inherits i
JOIN pg_class parent ON parent.oid = i.inhparent
JOIN pg_class child ON child.oid = i.inhrelid
JOIN pg_namespace ns ON ns.oid = parent.relnamespace
WHERE ns.nspname = 'public'
  AND parent.relkind = 'p'
ORDER BY parent.relname, child.relname;

-- Partition maintenance ------------------------------------------------------
SELECT
    'obsolete_future_months_column' AS check_name,
    column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'partition_maintenance_configs'
  AND column_name = 'future_months';

SELECT
    parent_schema,
    parent_table,
    partition_column,
    partition_granularity,
    retention_mode,
    retention_months,
    future_partitions,
    allow_auto_drop,
    is_active
FROM partition_maintenance_configs
ORDER BY parent_schema, parent_table;

SELECT
    'unexpected_partition_config' AS check_name,
    parent_schema,
    parent_table
FROM partition_maintenance_configs
WHERE (parent_schema, parent_table) NOT IN (
    ('public', 'user_logs'),
    ('public', 'system_activities'),
    ('public', 'dicom_server_callback_log'),
    ('public', 'pacs_realtime_notification_events'),
    ('public', 'pacs_worklist_histories'),
    ('public', 'study_retention_delete_requests')
)
ORDER BY parent_schema, parent_table;

SELECT
    'policy_table_marked_fixed_auto_drop' AS check_name,
    parent_schema,
    parent_table,
    partition_granularity,
    retention_mode,
    allow_auto_drop
FROM partition_maintenance_configs
WHERE parent_table IN ('pacs_worklist_histories', 'study_retention_delete_requests')
  AND (retention_mode <> 'POLICY_BASED'
       OR partition_granularity <> 'YEAR'
       OR allow_auto_drop = TRUE);

WITH expected AS (
    SELECT
        cfg.parent_schema,
        cfg.parent_table,
        period_start::date AS period_start,
        CASE cfg.partition_granularity
            WHEN 'MONTH' THEN cfg.parent_table || '_' || to_char(period_start, 'YYYYMM')
            WHEN 'YEAR' THEN cfg.parent_table || '_' || to_char(period_start, 'YYYY')
        END AS expected_partition
    FROM partition_maintenance_configs cfg
    CROSS JOIN LATERAL generate_series(
        CASE cfg.partition_granularity
            WHEN 'MONTH' THEN
                CASE
                    WHEN cfg.retention_mode = 'FIXED_MONTHS' THEN
                        (date_trunc('month', current_date) - make_interval(months => cfg.retention_months))::date
                    ELSE
                        date_trunc('month', current_date)::date
                END
            ELSE date_trunc('year', current_date)::date
        END,
        CASE cfg.partition_granularity
            WHEN 'MONTH' THEN (date_trunc('month', current_date) + make_interval(months => cfg.future_partitions))::date
            ELSE (date_trunc('year', current_date) + make_interval(years => cfg.future_partitions))::date
        END,
        CASE cfg.partition_granularity
            WHEN 'MONTH' THEN interval '1 month'
            ELSE interval '1 year'
        END
    ) AS period_start
    WHERE cfg.is_active = 1
      AND EXISTS (
          SELECT 1
          FROM pg_class cls
          WHERE cls.oid = to_regclass(format('%I.%I', cfg.parent_schema, cfg.parent_table))
            AND cls.relkind = 'p'
      )
)
SELECT
    'missing_future_partition' AS check_name,
    parent_schema,
    parent_table,
    period_start,
    expected_partition
FROM expected
WHERE to_regclass(format('%I.%I', parent_schema, expected_partition)) IS NULL
ORDER BY parent_schema, parent_table, period_start;

WITH children AS (
    SELECT
        cfg.parent_schema,
        cfg.parent_table,
        cfg.retention_months,
        child_ns.nspname AS child_schema,
        child.relname AS child_table,
        right(child.relname, 6) AS yyyymm
    FROM partition_maintenance_configs cfg
    JOIN pg_class parent ON parent.oid = to_regclass(format('%I.%I', cfg.parent_schema, cfg.parent_table))
    JOIN pg_inherits inh ON inh.inhparent = parent.oid
    JOIN pg_class child ON child.oid = inh.inhrelid
    JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace
    WHERE cfg.is_active = 1
      AND cfg.retention_mode = 'FIXED_MONTHS'
      AND cfg.partition_granularity = 'MONTH'
      AND cfg.allow_auto_drop = TRUE
      AND parent.relkind = 'p'
      AND child.relname ~ ('^' || cfg.parent_table || '_[0-9]{4}(0[1-9]|1[0-2])$')
)
SELECT
    'old_fixed_partition_candidate' AS check_name,
    parent_schema,
    parent_table,
    child_schema,
    child_table,
    to_date(yyyymm, 'YYYYMM') AS partition_month
FROM children
WHERE to_date(yyyymm, 'YYYYMM')
      < (date_trunc('month', current_date) - make_interval(months => retention_months))::date
ORDER BY parent_schema, parent_table, child_table;

SELECT
    'worklist_history_purge_eligible_rows' AS check_name,
    count(*) AS total
FROM pacs_worklist_histories
WHERE purge_after IS NOT NULL
  AND purge_after < current_date;

SELECT
    'retention_request_purge_eligible_rows' AS check_name,
    count(*) AS total
FROM study_retention_delete_requests
WHERE purge_after IS NOT NULL
  AND purge_after < current_date;

-- Weekly Worklist/Study cache ------------------------------------------------
SELECT
    to_regclass('public.pacs_worklists_week_cache') IS NOT NULL AS worklist_week_cache_exists,
    to_regclass('public.pacs_studies_week_cache') IS NOT NULL AS study_week_cache_exists;

SELECT
    'worklist_cache_heavy_error_column' AS check_name,
    column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'pacs_worklists_week_cache'
  AND column_name = 'error_message';

SELECT
    'worklist_cache_orphan' AS check_name,
    c.id
FROM pacs_worklists_week_cache c
LEFT JOIN pacs_worklists w ON w.id = c.id
WHERE w.id IS NULL
ORDER BY c.id
LIMIT 100;

SELECT
    'study_cache_orphan' AS check_name,
    c.id
FROM pacs_studies_week_cache c
LEFT JOIN pacs_studies s ON s.id = c.id
WHERE s.id IS NULL
ORDER BY c.id
LIMIT 100;

SELECT
    'worklist_cache_older_than_7_days' AS check_name,
    id,
    created_at
FROM pacs_worklists_week_cache
WHERE created_at < NOW() - INTERVAL '7 days'
ORDER BY created_at, id
LIMIT 100;

SELECT
    'study_cache_older_than_7_days' AS check_name,
    id,
    received_at,
    created_at,
    created
FROM pacs_studies_week_cache
WHERE COALESCE(image_received_at, received_at, created_at, created) < NOW() - INTERVAL '7 days'
ORDER BY COALESCE(image_received_at, received_at, created_at, created), id
LIMIT 100;

SELECT
    'worklist_cache_status_mismatch' AS check_name,
    c.id,
    c.status AS cache_status,
    w.status AS main_status
FROM pacs_worklists_week_cache c
JOIN pacs_worklists w ON w.id = c.id
WHERE c.status <> w.status
ORDER BY c.id
LIMIT 100;

SELECT
    'study_cache_accession_mismatch' AS check_name,
    c.id,
    c.accession_number AS cache_accession,
    s.accession_number AS main_accession
FROM pacs_studies_week_cache c
JOIN pacs_studies s ON s.id = c.id
WHERE COALESCE(c.accession_number, '') <> COALESCE(s.accession_number, '')
ORDER BY c.id
LIMIT 100;

SELECT
    'study_cache_status_mismatch' AS check_name,
    c.id,
    c.status AS cache_status,
    s.status AS main_status
FROM pacs_studies_week_cache c
JOIN pacs_studies s ON s.id = c.id
WHERE COALESCE(c.status, -1) <> COALESCE(s.status, -1)
ORDER BY c.id
LIMIT 100;

SELECT
    'worklist_cache_count' AS check_name,
    COUNT(*) AS total
FROM pacs_worklists_week_cache;

SELECT
    'study_cache_count' AS check_name,
    COUNT(*) AS total
FROM pacs_studies_week_cache;

SELECT
    'worklist_recent_missing_from_cache' AS check_name,
    w.id
FROM pacs_worklists w
LEFT JOIN pacs_worklists_week_cache c ON c.id = w.id
WHERE COALESCE(w.created_at, w.created) >= NOW() - INTERVAL '7 days'
  AND c.id IS NULL
ORDER BY w.id
LIMIT 100;

SELECT
    'study_recent_missing_from_cache' AS check_name,
    s.id
FROM pacs_studies s
LEFT JOIN pacs_studies_week_cache c ON c.id = s.id
WHERE s.is_active = 1
  AND COALESCE(s.image_received_at, s.received_at, s.created_at, s.created) >= NOW() - INTERVAL '7 days'
  AND c.id IS NULL
ORDER BY s.id
LIMIT 100;

-- Duplicate scoped identifiers ------------------------------------------------
SELECT 'duplicate_patient_uid_per_hospital' AS check_name, hospital_id, LOWER(patient_uid) AS key_value, COUNT(*) AS total
FROM patients
WHERE patient_uid IS NOT NULL
GROUP BY hospital_id, LOWER(patient_uid)
HAVING COUNT(*) > 1;

SELECT 'duplicate_visit_code_per_hospital' AS check_name, hospital_id, LOWER(visit_code) AS key_value, COUNT(*) AS total
FROM pacs_worklists
WHERE visit_code IS NOT NULL
  AND BTRIM(visit_code) <> ''
GROUP BY hospital_id, LOWER(visit_code)
HAVING COUNT(*) > 1;

SELECT 'duplicate_study_instance_uid_per_hospital' AS check_name, hospital_id, study_instance_uid AS key_value, COUNT(*) AS total
FROM pacs_studies
WHERE study_instance_uid IS NOT NULL
GROUP BY hospital_id, study_instance_uid
HAVING COUNT(*) > 1;

SELECT 'duplicate_active_result_per_study' AS check_name, hospital_id, study_id, COUNT(*) AS total
FROM pacs_results
WHERE is_active = 1
  AND study_id IS NOT NULL
GROUP BY hospital_id, study_id
HAVING COUNT(*) > 1;

SELECT 'duplicate_active_result_per_worklist' AS check_name, hospital_id, worklist_id, COUNT(*) AS total
FROM pacs_results
WHERE is_active = 1
  AND worklist_id IS NOT NULL
GROUP BY hospital_id, worklist_id
HAVING COUNT(*) > 1;

-- Cross-hospital / orphan relation checks ------------------------------------
SELECT 'worklist_patient_cross_hospital' AS check_name, w.id, w.hospital_id, w.patient_id
FROM pacs_worklists w
JOIN patients p ON p.id = w.patient_id
WHERE p.hospital_id <> w.hospital_id;

SELECT 'study_patient_cross_hospital' AS check_name, s.id, s.hospital_id, s.patient_id
FROM pacs_studies s
JOIN patients p ON p.id = s.patient_id
WHERE p.hospital_id <> s.hospital_id;

SELECT 'result_study_cross_hospital' AS check_name, r.id, r.hospital_id, r.study_id
FROM pacs_results r
JOIN pacs_studies s ON s.id = r.study_id
WHERE r.study_id IS NOT NULL
  AND s.hospital_id <> r.hospital_id;

SELECT 'result_worklist_cross_hospital' AS check_name, r.id, r.hospital_id, r.worklist_id
FROM pacs_results r
JOIN pacs_worklists w ON w.id = r.worklist_id
WHERE r.worklist_id IS NOT NULL
  AND w.hospital_id <> r.hospital_id;

SELECT 'image_result_cross_hospital' AS check_name, i.id, i.hospital_id, i.result_id
FROM pacs_result_images i
JOIN pacs_results r ON r.id = i.result_id
WHERE r.hospital_id <> i.hospital_id;

SELECT 'orphan_result_image' AS check_name, i.id, i.result_id
FROM pacs_result_images i
LEFT JOIN pacs_results r ON r.id = i.result_id
WHERE r.id IS NULL;

SELECT 'orphan_worklist_study_link_worklist' AS check_name, l.id, l.worklist_id
FROM pacs_worklist_study_links l
LEFT JOIN pacs_worklists w ON w.id = l.worklist_id AND w.hospital_id = l.hospital_id
WHERE w.id IS NULL;

SELECT 'orphan_worklist_study_link_study' AS check_name, l.id, l.study_id
FROM pacs_worklist_study_links l
LEFT JOIN pacs_studies s ON s.id = l.study_id AND s.hospital_id = l.hospital_id
WHERE s.id IS NULL;

-- Payload and path checks -----------------------------------------------------
SELECT 'oversized_viewer_payload' AS check_name, id, hospital_id, payload_size_bytes
FROM pacs_viewer_states
WHERE payload_size_bytes > 10485760;

SELECT 'absolute_result_image_path' AS check_name, id, image_path
FROM pacs_result_images
WHERE image_path ~* '^[a-z][a-z0-9+.-]*://'
   OR image_path ~ '^//'
   OR image_path ~ '^[A-Za-z]:[\\/]';

SELECT 'callback_log_null_hospital_id' AS check_name, COUNT(*) AS total
FROM dicom_server_callback_log
WHERE hospital_id IS NULL;

SELECT
    'callback_log_hospital_id_nullable' AS check_name,
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'dicom_server_callback_log'
  AND column_name = 'hospital_id';

WITH accession_matches AS (
    SELECT u.id AS unmatched_id, w.hospital_id
    FROM dicom_server_unmatched_callback_log u
    JOIN pacs_worklists w ON LOWER(w.visit_code) = LOWER(u.accession_number)
    WHERE u.accession_number IS NOT NULL
      AND BTRIM(u.accession_number) <> ''

    UNION ALL

    SELECT u.id AS unmatched_id, s.hospital_id
    FROM dicom_server_unmatched_callback_log u
    JOIN pacs_studies s ON LOWER(s.accession_number) = LOWER(u.accession_number)
    WHERE u.accession_number IS NOT NULL
      AND BTRIM(u.accession_number) <> ''

    UNION ALL

    SELECT u.id AS unmatched_id, s.hospital_id
    FROM dicom_server_unmatched_callback_log u
    JOIN pacs_studies s ON LOWER(s.reference_visit_code) = LOWER(u.accession_number)
    WHERE u.accession_number IS NOT NULL
      AND BTRIM(u.accession_number) <> ''
),
resolved AS (
    SELECT
        u.id,
        COUNT(DISTINCT m.hospital_id) FILTER (WHERE m.hospital_id IS NOT NULL) AS hospital_count
    FROM dicom_server_unmatched_callback_log u
    LEFT JOIN accession_matches m ON m.unmatched_id = u.id
    GROUP BY u.id
)
SELECT
    check_name,
    total
FROM (
    SELECT
        'resolvable_unmatched_callback_log_rows' AS check_name,
        COUNT(*) FILTER (WHERE hospital_count = 1) AS total
    FROM resolved
    UNION ALL
    SELECT
        'ambiguous_unmatched_callback_log_rows' AS check_name,
        COUNT(*) FILTER (WHERE hospital_count > 1) AS total
    FROM resolved
    UNION ALL
    SELECT
        'unresolved_unmatched_callback_log_rows_preserved' AS check_name,
        COUNT(*) FILTER (WHERE hospital_count = 0) AS total
    FROM resolved
) checks
ORDER BY check_name;

SELECT
    'retained_worklist_id_hospital_index' AS check_name,
    COUNT(*) AS retained_index_count
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'pacs_worklists'
  AND indexname IN ('ux_worklists_id_hospital', 'ux_pacs_worklists_id_hospital');

-- Final hot-table index inventory -------------------------------------------
SELECT tablename, COUNT(*) AS index_count
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN (
      'patients',
      'pacs_worklists',
      'pacs_studies',
      'pacs_results',
      'pacs_result_images',
      'system_activities',
      'user_logs',
      'dicom_server_callback_log',
      'pacs_realtime_notification_events'
  )
GROUP BY tablename
ORDER BY tablename;

SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('pacs_worklists', 'pacs_studies', 'pacs_results')
ORDER BY tablename, indexname;

SELECT
    'unvalidated_public_constraint' AS check_name,
    conrelid::regclass AS table_name,
    conname,
    contype
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
  AND NOT convalidated
ORDER BY conrelid::regclass::text, conname;
