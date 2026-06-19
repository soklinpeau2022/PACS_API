-- V202: Close the remaining DEV database acceptance gaps.
--
-- MIGRATION-SAFETY: allowed-data-loss
-- MIGRATION-SAFETY: constraint-guard-reviewed
-- The only dropped data is pacs_worklists_week_cache.error_message. The cache
-- is rebuildable from pacs_worklists and is refreshed at the end of this
-- migration. Rollback is to add the nullable cache column back and restore the
-- V201 cache functions.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Replace every remaining legacy UUID default, including defaults copied onto
-- existing native partition children.
DO $$
DECLARE
    item RECORD;
BEGIN
    FOR item IN
        SELECT
            ns.nspname AS schema_name,
            cls.relname AS table_name,
            attr.attname AS column_name
        FROM pg_attrdef def
        JOIN pg_class cls
          ON cls.oid = def.adrelid
        JOIN pg_namespace ns
          ON ns.oid = cls.relnamespace
        JOIN pg_attribute attr
          ON attr.attrelid = cls.oid
         AND attr.attnum = def.adnum
        WHERE ns.nspname = 'public'
          AND cls.relkind IN ('p', 'r')
          AND attr.atttypid = 'uuid'::REGTYPE
          AND PG_GET_EXPR(def.adbin, def.adrelid) ~* '(md5|random|clock_timestamp)'
        ORDER BY cls.relkind DESC, cls.relname, attr.attname
    LOOP
        EXECUTE FORMAT(
            'ALTER TABLE %I.%I ALTER COLUMN %I SET DEFAULT gen_random_uuid()',
            item.schema_name,
            item.table_name,
            item.column_name
        );
    END LOOP;
END;
$$;

-- Default Worklist screens are summary lists. Keep workflow diagnostics on the
-- source-of-truth table and detail APIs instead of copying large error text.
ALTER TABLE public.pacs_worklists_week_cache
    DROP COLUMN IF EXISTS error_message;

CREATE OR REPLACE FUNCTION sync_pacs_worklist_week_cache(p_worklist_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
BEGIN
    DELETE FROM public.pacs_worklists_week_cache
    WHERE id = p_worklist_id;

    INSERT INTO public.pacs_worklists_week_cache (
        id,
        public_id,
        hospital_id,
        patient_id,
        modality_id,
        dicom_route_id,
        study_id,
        visit_code,
        status,
        scheduled_date,
        scheduled_time,
        study_description,
        dicom_server_worklist_id,
        dicom_server_worklist_path,
        sent_at,
        received_at,
        image_received_at,
        cancelled_at,
        started_at,
        created_at,
        modified_at,
        created,
        created_by,
        cached_at
    )
    SELECT
        id,
        public_id,
        hospital_id,
        patient_id,
        modality_id,
        dicom_route_id,
        study_id,
        visit_code,
        status,
        scheduled_date,
        scheduled_time,
        study_description,
        dicom_server_worklist_id,
        dicom_server_worklist_path,
        sent_at,
        received_at,
        image_received_at,
        cancelled_at,
        started_at,
        created_at,
        modified_at,
        created,
        created_by,
        NOW()
    FROM public.pacs_worklists
    WHERE id = p_worklist_id
      AND COALESCE(created_at, created) >= NOW() - INTERVAL '7 days';
END
$fn$;

CREATE OR REPLACE FUNCTION refresh_pacs_week_cache()
RETURNS TEXT
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    worklist_rows BIGINT := 0;
    study_rows BIGINT := 0;
BEGIN
    -- This function runs as one PostgreSQL transaction. If either refill
    -- fails, both TRUNCATE operations roll back and the previous cache remains.
    TRUNCATE TABLE public.pacs_worklists_week_cache;
    TRUNCATE TABLE public.pacs_studies_week_cache;

    INSERT INTO public.pacs_worklists_week_cache (
        id,
        public_id,
        hospital_id,
        patient_id,
        modality_id,
        dicom_route_id,
        study_id,
        visit_code,
        status,
        scheduled_date,
        scheduled_time,
        study_description,
        dicom_server_worklist_id,
        dicom_server_worklist_path,
        sent_at,
        received_at,
        image_received_at,
        cancelled_at,
        started_at,
        created_at,
        modified_at,
        created,
        created_by,
        cached_at
    )
    SELECT
        id,
        public_id,
        hospital_id,
        patient_id,
        modality_id,
        dicom_route_id,
        study_id,
        visit_code,
        status,
        scheduled_date,
        scheduled_time,
        study_description,
        dicom_server_worklist_id,
        dicom_server_worklist_path,
        sent_at,
        received_at,
        image_received_at,
        cancelled_at,
        started_at,
        created_at,
        modified_at,
        created,
        created_by,
        NOW()
    FROM public.pacs_worklists
    WHERE COALESCE(created_at, created) >= NOW() - INTERVAL '7 days';
    GET DIAGNOSTICS worklist_rows = ROW_COUNT;

    INSERT INTO public.pacs_studies_week_cache (
        id,
        public_id,
        hospital_id,
        patient_id,
        modality_id,
        study_instance_uid,
        accession_number,
        reference_visit_code,
        modality,
        study_date,
        received_at,
        image_received_at,
        study_description,
        status,
        is_active,
        dicom_server_id,
        dicom_server_study_id,
        dicom_server_patient_id,
        dicom_server_series_id,
        instance_count,
        institution_name,
        created,
        modified,
        created_at,
        cached_at
    )
    SELECT
        id,
        public_id,
        hospital_id,
        patient_id,
        modality_id,
        study_instance_uid,
        accession_number,
        reference_visit_code,
        modality,
        study_date,
        received_at,
        image_received_at,
        study_description,
        status,
        is_active,
        dicom_server_id,
        dicom_server_study_id,
        dicom_server_patient_id,
        dicom_server_series_id,
        instance_count,
        institution_name,
        created,
        modified,
        created_at,
        NOW()
    FROM public.pacs_studies
    WHERE is_active = 1
      AND COALESCE(image_received_at, received_at, created_at, created) >= NOW() - INTERVAL '7 days';
    GET DIAGNOSTICS study_rows = ROW_COUNT;

    ANALYZE public.pacs_worklists_week_cache;
    ANALYZE public.pacs_studies_week_cache;

    RETURN FORMAT(
        'PACS weekly cache refreshed: worklists=%s, studies=%s',
        worklist_rows,
        study_rows
    );
END
$fn$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_pacs_worklists_week_cache_status'
          AND conrelid = 'public.pacs_worklists_week_cache'::REGCLASS
    ) THEN
        ALTER TABLE public.pacs_worklists_week_cache
            ADD CONSTRAINT chk_pacs_worklists_week_cache_status
            CHECK (status IN (1, 2, 3, 4));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_pacs_studies_week_cache_active'
          AND conrelid = 'public.pacs_studies_week_cache'::REGCLASS
    ) THEN
        ALTER TABLE public.pacs_studies_week_cache
            ADD CONSTRAINT chk_pacs_studies_week_cache_active
            CHECK (is_active IN (1, 2));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_pacs_studies_week_cache_status'
          AND conrelid = 'public.pacs_studies_week_cache'::REGCLASS
    ) THEN
        ALTER TABLE public.pacs_studies_week_cache
            ADD CONSTRAINT chk_pacs_studies_week_cache_status
            CHECK (status IS NULL OR status IN (1, 2));
    END IF;
END;
$$;

-- Copy the intersection of source/archive columns. An optional archived_at
-- column is populated explicitly. A missing required archive column aborts the
-- caller before source rows can be deleted or a partition can be dropped.
CREATE OR REPLACE FUNCTION pacs_archive_policy_rows(
    p_source REGCLASS,
    p_archive REGCLASS,
    p_where_sql TEXT DEFAULT 'TRUE'
)
RETURNS BIGINT
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    source_columns TEXT;
    archive_columns TEXT;
    missing_required TEXT;
    archive_has_archived_at BOOLEAN;
    archived_rows BIGINT := 0;
BEGIN
    SELECT STRING_AGG(FORMAT('%I', source_attr.attname), ', ' ORDER BY source_attr.attnum),
           STRING_AGG(FORMAT('%I', archive_attr.attname), ', ' ORDER BY source_attr.attnum)
    INTO source_columns, archive_columns
    FROM pg_attribute source_attr
    JOIN pg_attribute archive_attr
      ON archive_attr.attrelid = p_archive
     AND archive_attr.attname = source_attr.attname
     AND archive_attr.attnum > 0
     AND NOT archive_attr.attisdropped
     AND archive_attr.attgenerated = ''
     AND archive_attr.attidentity <> 'a'
    WHERE source_attr.attrelid = p_source
      AND source_attr.attnum > 0
      AND NOT source_attr.attisdropped;

    SELECT EXISTS (
        SELECT 1
        FROM pg_attribute
        WHERE attrelid = p_archive
          AND attname = 'archived_at'
          AND attnum > 0
          AND NOT attisdropped
          AND attgenerated = ''
          AND attidentity <> 'a'
    )
    INTO archive_has_archived_at;

    SELECT STRING_AGG(archive_attr.attname, ', ' ORDER BY archive_attr.attnum)
    INTO missing_required
    FROM pg_attribute archive_attr
    LEFT JOIN pg_attribute source_attr
      ON source_attr.attrelid = p_source
     AND source_attr.attname = archive_attr.attname
     AND source_attr.attnum > 0
     AND NOT source_attr.attisdropped
    LEFT JOIN pg_attrdef archive_default
      ON archive_default.adrelid = archive_attr.attrelid
     AND archive_default.adnum = archive_attr.attnum
    WHERE archive_attr.attrelid = p_archive
      AND archive_attr.attnum > 0
      AND NOT archive_attr.attisdropped
      AND archive_attr.attnotnull
      AND archive_attr.attgenerated = ''
      AND archive_attr.attidentity = ''
      AND archive_attr.attname <> 'archived_at'
      AND source_attr.attname IS NULL
      AND archive_default.oid IS NULL;

    IF missing_required IS NOT NULL THEN
        RAISE EXCEPTION
            'archive table % has required columns not present in source %: %',
            p_archive,
            p_source,
            missing_required;
    END IF;

    IF source_columns IS NULL OR archive_columns IS NULL THEN
        RAISE EXCEPTION 'archive table % shares no writable columns with source %',
            p_archive, p_source;
    END IF;

    IF archive_has_archived_at THEN
        archive_columns := archive_columns || ', archived_at';
        source_columns := source_columns || ', NOW()';
    END IF;

    EXECUTE FORMAT(
        'INSERT INTO %s (%s) SELECT %s FROM %s source_rows WHERE %s ON CONFLICT DO NOTHING',
        p_archive,
        archive_columns,
        source_columns,
        p_source,
        COALESCE(NULLIF(BTRIM(p_where_sql), ''), 'TRUE')
    );
    GET DIAGNOSTICS archived_rows = ROW_COUNT;
    RETURN archived_rows;
END
$fn$;

CREATE OR REPLACE FUNCTION cleanup_policy_based_retention_data(p_dry_run BOOLEAN DEFAULT TRUE)
RETURNS TABLE(parent_table TEXT, eligible_rows BIGINT, action_taken TEXT)
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    target_table TEXT;
    source_reg REGCLASS;
    archive_reg REGCLASS;
    eligible BIGINT;
    archived BIGINT;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'pacs_worklist_histories',
        'study_retention_delete_requests'
    ]
    LOOP
        source_reg := TO_REGCLASS(FORMAT('public.%I', target_table));
        IF source_reg IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE FORMAT(
            'SELECT COUNT(*) FROM %s WHERE purge_after IS NOT NULL AND purge_after < CURRENT_DATE',
            source_reg
        )
        INTO eligible;

        IF p_dry_run OR eligible = 0 THEN
            RETURN QUERY SELECT target_table, eligible, 'dry_run_only'::TEXT;
            CONTINUE;
        END IF;

        archive_reg := TO_REGCLASS(FORMAT('public.%I', target_table || '_archive'));
        archived := 0;
        IF archive_reg IS NOT NULL THEN
            archived := pacs_archive_policy_rows(
                source_reg,
                archive_reg,
                'purge_after IS NOT NULL AND purge_after < CURRENT_DATE'
            );
        END IF;

        EXECUTE FORMAT(
            'DELETE FROM %s WHERE purge_after IS NOT NULL AND purge_after < CURRENT_DATE',
            source_reg
        );

        RETURN QUERY
        SELECT
            target_table,
            eligible,
            CASE
                WHEN archive_reg IS NOT NULL
                    THEN FORMAT('archived_%s_deleted_%s', archived, eligible)
                ELSE FORMAT('deleted_%s_policy_eligible_rows', eligible)
            END;
    END LOOP;
END
$fn$;

CREATE OR REPLACE FUNCTION drop_policy_partitions_if_fully_expired(p_dry_run BOOLEAN DEFAULT TRUE)
RETURNS TABLE(
    parent_table TEXT,
    partition_table TEXT,
    total_rows BIGINT,
    protected_rows BIGINT,
    action_taken TEXT
)
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    cfg RECORD;
    parent_reg REGCLASS;
    child RECORD;
    child_reg REGCLASS;
    archive_reg REGCLASS;
    total_count BIGINT;
    protected_count BIGINT;
    archived_count BIGINT;
    partition_year INTEGER;
BEGIN
    FOR cfg IN
        SELECT parent_schema, parent_table
        FROM partition_maintenance_configs
        WHERE is_active = 1
          AND parent_schema = 'public'
          AND parent_table IN (
              'pacs_worklist_histories',
              'study_retention_delete_requests'
          )
          AND retention_mode = 'POLICY_BASED'
          AND partition_granularity = 'YEAR'
          AND allow_auto_drop = FALSE
        ORDER BY parent_table
    LOOP
        parent_reg := TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table));
        IF parent_reg IS NULL THEN
            CONTINUE;
        END IF;

        archive_reg := TO_REGCLASS(
            FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table || '_archive')
        );

        FOR child IN
            SELECT
                child_ns.nspname AS child_schema,
                child_cls.relname AS child_table
            FROM pg_inherits inheritance
            JOIN pg_class child_cls
              ON child_cls.oid = inheritance.inhrelid
            JOIN pg_namespace child_ns
              ON child_ns.oid = child_cls.relnamespace
            WHERE inheritance.inhparent = parent_reg
              AND child_cls.relispartition
              AND child_cls.relname ~ ('^' || cfg.parent_table || '_[0-9]{4}$')
            ORDER BY child_cls.relname
        LOOP
            partition_year := RIGHT(child.child_table, 4)::INTEGER;
            IF partition_year >= EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER THEN
                RETURN QUERY
                SELECT cfg.parent_table, child.child_table, 0::BIGINT, 0::BIGINT, 'kept_current_or_future_year'::TEXT;
                CONTINUE;
            END IF;

            child_reg := TO_REGCLASS(
                FORMAT('%I.%I', child.child_schema, child.child_table)
            );

            EXECUTE FORMAT('SELECT COUNT(*) FROM %s', child_reg)
            INTO total_count;

            EXECUTE FORMAT(
                'SELECT COUNT(*) FROM %s WHERE purge_after IS NULL OR purge_after >= CURRENT_DATE',
                child_reg
            )
            INTO protected_count;

            IF total_count = 0 OR protected_count > 0 THEN
                RETURN QUERY
                SELECT cfg.parent_table, child.child_table, total_count, protected_count, 'kept'::TEXT;
                CONTINUE;
            END IF;

            IF p_dry_run THEN
                RETURN QUERY
                SELECT cfg.parent_table, child.child_table, total_count, protected_count, 'dry_run_drop_candidate'::TEXT;
                CONTINUE;
            END IF;

            archived_count := 0;
            IF archive_reg IS NOT NULL THEN
                archived_count := pacs_archive_policy_rows(child_reg, archive_reg, 'TRUE');
            END IF;

            EXECUTE FORMAT(
                'DROP TABLE IF EXISTS %I.%I',
                child.child_schema,
                child.child_table
            );

            RETURN QUERY
            SELECT
                cfg.parent_table,
                child.child_table,
                total_count,
                protected_count,
                CASE
                    WHEN archive_reg IS NOT NULL
                        THEN FORMAT('archived_%s_and_dropped', archived_count)
                    ELSE 'dropped'
                END;
        END LOOP;
    END LOOP;
END
$fn$;

-- The current DEV rows satisfy these constraints. Validate the final checks and
-- hospital-safe parent FKs; validating partitioned parents also validates their
-- native child constraints.
DO $$
DECLARE
    item RECORD;
BEGIN
    FOR item IN
        SELECT
            ns.nspname AS schema_name,
            cls.relname AS table_name,
            constraint_row.conname AS constraint_name
        FROM pg_constraint constraint_row
        JOIN pg_class cls
          ON cls.oid = constraint_row.conrelid
        JOIN pg_namespace ns
          ON ns.oid = cls.relnamespace
        WHERE ns.nspname = 'public'
          AND constraint_row.contype = 'c'
          AND NOT constraint_row.convalidated
        ORDER BY cls.relname, constraint_row.conname
    LOOP
        EXECUTE FORMAT(
            'ALTER TABLE %I.%I VALIDATE CONSTRAINT %I',
            item.schema_name,
            item.table_name,
            item.constraint_name
        );
    END LOOP;

    FOR item IN
        SELECT
            ns.nspname AS schema_name,
            cls.relname AS table_name,
            constraint_row.conname AS constraint_name
        FROM pg_constraint constraint_row
        JOIN pg_class cls
          ON cls.oid = constraint_row.conrelid
        JOIN pg_namespace ns
          ON ns.oid = cls.relnamespace
        WHERE ns.nspname = 'public'
          AND constraint_row.contype = 'f'
          AND NOT constraint_row.convalidated
          AND NOT cls.relispartition
          AND cls.relname IN (
              'pacs_result_images',
              'pacs_realtime_notification_events',
              'pacs_worklist_histories',
              'study_retention_delete_requests'
          )
        ORDER BY cls.relname, constraint_row.conname
    LOOP
        EXECUTE FORMAT(
            'ALTER TABLE %I.%I VALIDATE CONSTRAINT %I',
            item.schema_name,
            item.table_name,
            item.constraint_name
        );
    END LOOP;
END;
$$;

COMMENT ON FUNCTION pacs_archive_policy_rows(REGCLASS, REGCLASS, TEXT) IS
    'Archives the writable common columns from a policy source table before approved row deletion or partition drop.';
COMMENT ON FUNCTION drop_policy_partitions_if_fully_expired(BOOLEAN) IS
    'Dry-runs by default; only past yearly native partitions for the two approved policy tables can be archived and dropped when every row is purge eligible.';

SELECT refresh_pacs_week_cache();
