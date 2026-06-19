\set ON_ERROR_STOP on
\pset pager off

SELECT extname
FROM pg_extension
WHERE extname IN ('pgcrypto', 'pg_trgm')
ORDER BY extname;

SELECT
    parent.relname AS parent_table,
    child.relname AS partition_table
FROM pg_inherits inheritance
JOIN pg_class parent ON parent.oid = inheritance.inhparent
JOIN pg_class child ON child.oid = inheritance.inhrelid
JOIN pg_namespace parent_namespace ON parent_namespace.oid = parent.relnamespace
WHERE parent_namespace.nspname = 'public'
  AND parent.relkind = 'p'
  AND child.relkind = 'r'
ORDER BY parent.relname, child.relname;

SELECT
    'fake_partition_table' AS check_name,
    child.relname AS table_name
FROM pg_class child
JOIN pg_namespace child_namespace ON child_namespace.oid = child.relnamespace
WHERE child_namespace.nspname = 'public'
  AND child.relkind = 'r'
  AND (
      child.relname ~ '^(user_logs|system_activities|dicom_server_callback_log|pacs_realtime_notification_events)_[0-9]{6}$'
      OR child.relname ~ '^(pacs_worklist_histories|study_retention_delete_requests)_[0-9]{4}$'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM pg_inherits inheritance
      WHERE inheritance.inhrelid = child.oid
  )
ORDER BY child.relname;

SELECT
    parent_table,
    retention_mode,
    partition_granularity,
    retention_months,
    future_partitions,
    allow_auto_drop
FROM partition_maintenance_configs
ORDER BY parent_table;

SELECT
    'invalid_policy_partition_config' AS check_name,
    *
FROM partition_maintenance_configs
WHERE parent_table IN ('pacs_worklist_histories', 'study_retention_delete_requests')
  AND (
      retention_mode <> 'POLICY_BASED'
      OR partition_granularity <> 'YEAR'
      OR allow_auto_drop = TRUE
  );

SELECT
    'worklist_cache_orphan' AS check_name,
    cache_row.id
FROM pacs_worklists_week_cache cache_row
LEFT JOIN pacs_worklists source_row ON source_row.id = cache_row.id
WHERE source_row.id IS NULL;

SELECT
    'study_cache_orphan' AS check_name,
    cache_row.id
FROM pacs_studies_week_cache cache_row
LEFT JOIN pacs_studies source_row ON source_row.id = cache_row.id
WHERE source_row.id IS NULL;

SELECT
    'worklist_cache_older_than_7_days' AS check_name,
    id,
    created_at
FROM pacs_worklists_week_cache
WHERE COALESCE(created_at, created) < NOW() - INTERVAL '7 days';

SELECT
    'study_cache_older_than_7_days' AS check_name,
    id,
    received_at,
    created
FROM pacs_studies_week_cache
WHERE COALESCE(image_received_at, received_at, created_at, created)
      < NOW() - INTERVAL '7 days';

SELECT procedure.proname
FROM pg_proc procedure
JOIN pg_namespace procedure_namespace
  ON procedure_namespace.oid = procedure.pronamespace
WHERE procedure_namespace.nspname = 'public'
  AND procedure.proname IN (
      'refresh_pacs_week_cache',
      'sync_pacs_worklist_week_cache',
      'sync_pacs_study_week_cache',
      'cleanup_pacs_week_cache',
      'create_future_partitions',
      'drop_expired_fixed_partitions',
      'cleanup_policy_based_retention_data',
      'drop_policy_partitions_if_fully_expired',
      'run_partition_maintenance'
  )
ORDER BY procedure.proname;

SELECT
    constraint_row.conname,
    constraint_row.conrelid::regclass AS table_name,
    pg_get_constraintdef(constraint_row.oid) AS definition
FROM pg_constraint constraint_row
JOIN pg_namespace constraint_namespace
  ON constraint_namespace.oid = constraint_row.connamespace
WHERE constraint_namespace.nspname = 'public'
  AND constraint_row.contype = 'c'
ORDER BY constraint_row.conrelid::regclass::text, constraint_row.conname;

DO $validation$
DECLARE
    required_function TEXT;
    required_parent TEXT;
    missing_extensions INTEGER;
    invalid_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO missing_extensions
    FROM (VALUES ('pgcrypto'), ('pg_trgm')) required(extension_name)
    WHERE NOT EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = required.extension_name
    );
    IF missing_extensions > 0 THEN
        RAISE EXCEPTION 'Required PostgreSQL extensions are missing.';
    END IF;

    FOREACH required_function IN ARRAY ARRAY[
        'refresh_pacs_week_cache',
        'sync_pacs_worklist_week_cache',
        'sync_pacs_study_week_cache',
        'cleanup_pacs_week_cache',
        'create_future_partitions',
        'drop_expired_fixed_partitions',
        'cleanup_policy_based_retention_data',
        'drop_policy_partitions_if_fully_expired',
        'run_partition_maintenance'
    ]
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_proc procedure
            JOIN pg_namespace procedure_namespace
              ON procedure_namespace.oid = procedure.pronamespace
            WHERE procedure_namespace.nspname = 'public'
              AND procedure.proname = required_function
        ) THEN
            RAISE EXCEPTION 'Required function %.% is missing.', 'public', required_function;
        END IF;
    END LOOP;

    FOREACH required_parent IN ARRAY ARRAY[
        'user_logs',
        'system_activities',
        'dicom_server_callback_log',
        'pacs_realtime_notification_events',
        'pacs_worklist_histories',
        'study_retention_delete_requests'
    ]
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_class parent
            WHERE parent.oid = to_regclass(format('public.%I', required_parent))
              AND parent.relkind = 'p'
        ) THEN
            RAISE EXCEPTION 'Required native partition parent public.% is missing.', required_parent;
        END IF;
        IF NOT EXISTS (
            SELECT 1
            FROM pg_inherits inheritance
            WHERE inheritance.inhparent = to_regclass(format('public.%I', required_parent))
        ) THEN
            RAISE EXCEPTION 'Partition parent public.% has no native children.', required_parent;
        END IF;
    END LOOP;

    SELECT COUNT(*) INTO invalid_rows
    FROM pg_class child
    JOIN pg_namespace child_namespace ON child_namespace.oid = child.relnamespace
    WHERE child_namespace.nspname = 'public'
      AND child.relkind = 'r'
      AND (
          child.relname ~ '^(user_logs|system_activities|dicom_server_callback_log|pacs_realtime_notification_events)_[0-9]{6}$'
          OR child.relname ~ '^(pacs_worklist_histories|study_retention_delete_requests)_[0-9]{4}$'
      )
      AND NOT child.relispartition;
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'Found % fake partition table(s).', invalid_rows;
    END IF;

    SELECT COUNT(*) INTO invalid_rows
    FROM partition_maintenance_configs
    WHERE parent_table IN ('pacs_worklist_histories', 'study_retention_delete_requests')
      AND (
          retention_mode <> 'POLICY_BASED'
          OR partition_granularity <> 'YEAR'
          OR allow_auto_drop = TRUE
      );
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'Policy-based partition config is unsafe.';
    END IF;

    SELECT COUNT(*) INTO invalid_rows
    FROM pacs_worklists_week_cache cache_row
    LEFT JOIN pacs_worklists source_row ON source_row.id = cache_row.id
    WHERE source_row.id IS NULL
       OR COALESCE(cache_row.created_at, cache_row.created) < NOW() - INTERVAL '7 days';
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'Worklist week cache contains % invalid row(s).', invalid_rows;
    END IF;

    SELECT COUNT(*) INTO invalid_rows
    FROM pacs_studies_week_cache cache_row
    LEFT JOIN pacs_studies source_row ON source_row.id = cache_row.id
    WHERE source_row.id IS NULL
       OR COALESCE(
           cache_row.image_received_at,
           cache_row.received_at,
           cache_row.created_at,
           cache_row.created
       ) < NOW() - INTERVAL '7 days';
    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'Study week cache contains % invalid row(s).', invalid_rows;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND NOT convalidated
    ) THEN
        RAISE EXCEPTION 'Public schema contains unvalidated constraints.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_index index_row
        JOIN pg_class index_class ON index_class.oid = index_row.indexrelid
        JOIN pg_namespace index_namespace ON index_namespace.oid = index_class.relnamespace
        WHERE index_namespace.nspname = 'public'
          AND NOT index_row.indisvalid
    ) THEN
        RAISE EXCEPTION 'Public schema contains invalid indexes.';
    END IF;

    RAISE NOTICE 'PACS database validation passed.';
END
$validation$;
