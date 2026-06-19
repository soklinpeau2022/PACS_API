-- V197: correct partition retention by table class.
--
-- MIGRATION-SAFETY: allowed-data-loss
-- MIGRATION-SAFETY: constraint-guard-reviewed
-- This migration performs transactional DEV-sized table swaps for the current
-- local high-growth tables. Rows are copied into the replacement partitioned
-- parents before the old tables are dropped in the same transaction. Fixed
-- technical logs are eligible for age-based child partition drops; medical and
-- policy/audit tables are not.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE partition_maintenance_configs
    DROP CONSTRAINT IF EXISTS chk_partition_maintenance_retention,
    DROP CONSTRAINT IF EXISTS chk_partition_maintenance_future,
    DROP CONSTRAINT IF EXISTS chk_partition_config_active,
    DROP CONSTRAINT IF EXISTS chk_partition_config_granularity,
    DROP CONSTRAINT IF EXISTS chk_partition_config_retention_mode,
    DROP CONSTRAINT IF EXISTS chk_partition_config_future,
    DROP CONSTRAINT IF EXISTS chk_partition_config_retention;

ALTER TABLE partition_maintenance_configs
    ADD COLUMN IF NOT EXISTS partition_granularity VARCHAR(20) NOT NULL DEFAULT 'MONTH',
    ADD COLUMN IF NOT EXISTS retention_mode VARCHAR(40) NOT NULL DEFAULT 'FIXED_MONTHS',
    ADD COLUMN IF NOT EXISTS future_partitions INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS allow_auto_drop BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE partition_maintenance_configs
    ALTER COLUMN retention_months DROP NOT NULL;

UPDATE partition_maintenance_configs
SET future_partitions = COALESCE(future_partitions, future_months, 3),
    updated_at = NOW()
WHERE future_partitions IS NULL
   OR future_partitions <> COALESCE(future_partitions, future_months, 3);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_partition_config_active') THEN
        ALTER TABLE partition_maintenance_configs
            ADD CONSTRAINT chk_partition_config_active
            CHECK (is_active IN (1, 2));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_partition_config_granularity') THEN
        ALTER TABLE partition_maintenance_configs
            ADD CONSTRAINT chk_partition_config_granularity
            CHECK (partition_granularity IN ('MONTH', 'YEAR'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_partition_config_retention_mode') THEN
        ALTER TABLE partition_maintenance_configs
            ADD CONSTRAINT chk_partition_config_retention_mode
            CHECK (retention_mode IN ('FIXED_MONTHS', 'POLICY_BASED'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_partition_config_future') THEN
        ALTER TABLE partition_maintenance_configs
            ADD CONSTRAINT chk_partition_config_future
            CHECK (future_partitions >= 1);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_partition_config_retention') THEN
        ALTER TABLE partition_maintenance_configs
            ADD CONSTRAINT chk_partition_config_retention
            CHECK (
                is_active = 2
                OR
                (
                    retention_mode = 'FIXED_MONTHS'
                    AND retention_months IS NOT NULL
                    AND retention_months > 0
                    AND allow_auto_drop = TRUE
                    AND partition_granularity = 'MONTH'
                )
                OR
                (
                    retention_mode = 'POLICY_BASED'
                    AND allow_auto_drop = FALSE
                    AND partition_granularity = 'YEAR'
                )
            );
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION ensure_partition_child_indexes(
    p_parent_schema TEXT,
    p_parent_table TEXT,
    p_child_schema TEXT,
    p_child_table TEXT
)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    created_count INTEGER := 0;
    idx_name TEXT;
BEGIN
    IF p_parent_table = 'dicom_server_callback_log' THEN
        idx_name := 'ux_' || SUBSTRING(MD5(p_child_table || '_hospital_dedupe') FROM 1 FOR 24);
        EXECUTE FORMAT(
            'CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I.%I (hospital_id, dedupe_key) WHERE dedupe_key IS NOT NULL',
            idx_name, p_child_schema, p_child_table
        );
        created_count := created_count + 1;
    ELSIF p_parent_table = 'pacs_realtime_notification_events' THEN
        idx_name := 'ux_' || SUBSTRING(MD5(p_child_table || '_hospital_dedupe') FROM 1 FOR 24);
        EXECUTE FORMAT(
            'CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I.%I (hospital_id, dedupe_key)',
            idx_name, p_child_schema, p_child_table
        );
        created_count := created_count + 1;
    END IF;

    RETURN created_count;
END
$fn$;

CREATE OR REPLACE FUNCTION pacs__create_initial_partitions(
    p_schema TEXT,
    p_table TEXT,
    p_granularity TEXT,
    p_start DATE,
    p_future_partitions INTEGER
)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    parent_qual TEXT := FORMAT('%I.%I', p_schema, p_table);
    part_start DATE;
    part_end DATE;
    final_start DATE;
    child_name TEXT;
    created_count INTEGER := 0;
BEGIN
    IF p_granularity = 'MONTH' THEN
        part_start := DATE_TRUNC('month', p_start)::DATE;
        final_start := (DATE_TRUNC('month', CURRENT_DATE) + MAKE_INTERVAL(months => p_future_partitions))::DATE;
    ELSIF p_granularity = 'YEAR' THEN
        part_start := DATE_TRUNC('year', p_start)::DATE;
        final_start := (DATE_TRUNC('year', CURRENT_DATE) + MAKE_INTERVAL(years => p_future_partitions))::DATE;
    ELSE
        RAISE EXCEPTION 'Unsupported partition granularity %', p_granularity;
    END IF;

    WHILE part_start <= final_start LOOP
        IF p_granularity = 'MONTH' THEN
            part_end := (part_start + INTERVAL '1 month')::DATE;
            child_name := p_table || '_' || TO_CHAR(part_start, 'YYYYMM');
        ELSE
            part_end := (part_start + INTERVAL '1 year')::DATE;
            child_name := p_table || '_' || TO_CHAR(part_start, 'YYYY');
        END IF;

        IF TO_REGCLASS(FORMAT('%I.%I', p_schema, child_name)) IS NULL THEN
            EXECUTE FORMAT(
                'CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
                p_schema, child_name, parent_qual, part_start, part_end
            );
            created_count := created_count + 1;
        END IF;
        PERFORM ensure_partition_child_indexes(p_schema, p_table, p_schema, child_name);

        part_start := part_end;
    END LOOP;

    child_name := p_table || '_default';
    IF TO_REGCLASS(FORMAT('%I.%I', p_schema, child_name)) IS NULL THEN
        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %s DEFAULT',
            p_schema, child_name, parent_qual
        );
        created_count := created_count + 1;
    END IF;
    PERFORM ensure_partition_child_indexes(p_schema, p_table, p_schema, child_name);

    RETURN created_count;
END
$fn$;

DO $$
DECLARE
    min_partition_date DATE;
BEGIN
    IF TO_REGCLASS('public.dicom_server_callback_log') IS NOT NULL
       AND (SELECT relkind FROM pg_class WHERE oid = 'public.dicom_server_callback_log'::REGCLASS) <> 'p' THEN
        ALTER SEQUENCE IF EXISTS public.dicom_server_callback_log_id_seq OWNED BY NONE;
        ALTER TABLE public.dicom_server_callback_log RENAME TO dicom_server_callback_log_v197_old;

        CREATE TABLE IF NOT EXISTS public.dicom_server_callback_log
            (LIKE public.dicom_server_callback_log_v197_old INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS)
            PARTITION BY RANGE (received_at);

        SELECT COALESCE(MIN(DATE_TRUNC('month', received_at)::DATE), (DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '12 months')::DATE)
        INTO min_partition_date
        FROM public.dicom_server_callback_log_v197_old;
        min_partition_date := LEAST(min_partition_date, (DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '12 months')::DATE);

        PERFORM pacs__create_initial_partitions('public', 'dicom_server_callback_log', 'MONTH', min_partition_date, 3);

        INSERT INTO public.dicom_server_callback_log
        SELECT * FROM public.dicom_server_callback_log_v197_old;

        DROP TABLE IF EXISTS public.dicom_server_callback_log_v197_old;

        ALTER TABLE public.dicom_server_callback_log
            ADD CONSTRAINT dicom_server_callback_log_pkey PRIMARY KEY (id, received_at),
            ADD CONSTRAINT chk_callback_log_attempt_count CHECK (attempt_count > 0),
            ADD CONSTRAINT chk_callback_log_payload_sha256 CHECK (payload_sha256 IS NULL OR payload_sha256 ~ '^[0-9a-f]{64}$');

        ALTER SEQUENCE IF EXISTS public.dicom_server_callback_log_id_seq
            OWNED BY public.dicom_server_callback_log.id;

        CREATE INDEX IF NOT EXISTS idx_callback_log_hospital_received
            ON public.dicom_server_callback_log (hospital_id, received_at DESC, id DESC);
        CREATE INDEX IF NOT EXISTS idx_callback_log_hospital_server_received
            ON public.dicom_server_callback_log (hospital_id, dicom_server_id, received_at DESC, id DESC)
            WHERE dicom_server_id IS NOT NULL;
        CREATE INDEX IF NOT EXISTS idx_callback_log_hospital_accession
            ON public.dicom_server_callback_log (hospital_id, accession_number)
            WHERE accession_number IS NOT NULL;

        ALTER TABLE public.dicom_server_callback_log
            ADD CONSTRAINT fk_callback_log_hospital
            FOREIGN KEY (hospital_id)
            REFERENCES public.hospitals(id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
        ALTER TABLE public.dicom_server_callback_log
            ADD CONSTRAINT fk_callback_log_server_hospital
            FOREIGN KEY (dicom_server_id, hospital_id)
            REFERENCES public.hospital_dicom_servers(id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;
END;
$$;

DO $$
DECLARE
    min_partition_date DATE;
BEGIN
    IF TO_REGCLASS('public.pacs_realtime_notification_events') IS NOT NULL
       AND (SELECT relkind FROM pg_class WHERE oid = 'public.pacs_realtime_notification_events'::REGCLASS) <> 'p' THEN
        ALTER SEQUENCE IF EXISTS public.pacs_realtime_notification_events_id_seq OWNED BY NONE;
        ALTER TABLE public.pacs_realtime_notification_events RENAME TO pacs_realtime_notification_events_v197_old;

        CREATE TABLE IF NOT EXISTS public.pacs_realtime_notification_events
            (LIKE public.pacs_realtime_notification_events_v197_old INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS)
            PARTITION BY RANGE (created_at);

        SELECT COALESCE(MIN(DATE_TRUNC('month', created_at)::DATE), (DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '12 months')::DATE)
        INTO min_partition_date
        FROM public.pacs_realtime_notification_events_v197_old;
        min_partition_date := LEAST(min_partition_date, (DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '12 months')::DATE);

        PERFORM pacs__create_initial_partitions('public', 'pacs_realtime_notification_events', 'MONTH', min_partition_date, 3);

        INSERT INTO public.pacs_realtime_notification_events
        SELECT * FROM public.pacs_realtime_notification_events_v197_old;

        DROP TABLE IF EXISTS public.pacs_realtime_notification_events_v197_old;

        ALTER TABLE public.pacs_realtime_notification_events
            ADD CONSTRAINT pacs_realtime_notification_events_pkey PRIMARY KEY (id, created_at);

        ALTER SEQUENCE IF EXISTS public.pacs_realtime_notification_events_id_seq
            OWNED BY public.pacs_realtime_notification_events.id;

        CREATE INDEX IF NOT EXISTS idx_pacs_realtime_events_hospital_cursor
            ON public.pacs_realtime_notification_events (hospital_id, id);
        CREATE INDEX IF NOT EXISTS idx_pacs_realtime_events_hospital_created
            ON public.pacs_realtime_notification_events (hospital_id, created_at DESC, id DESC);

        ALTER TABLE public.pacs_realtime_notification_events
            ADD CONSTRAINT fk_realtime_events_hospital_restrict
            FOREIGN KEY (hospital_id)
            REFERENCES public.hospitals(id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
        ALTER TABLE public.pacs_realtime_notification_events
            ADD CONSTRAINT fk_realtime_events_worklist_hospital
            FOREIGN KEY (worklist_id, hospital_id)
            REFERENCES public.pacs_worklists(id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
        ALTER TABLE public.pacs_realtime_notification_events
            ADD CONSTRAINT fk_realtime_events_study_hospital
            FOREIGN KEY (study_id, hospital_id)
            REFERENCES public.pacs_studies(id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;

        COMMENT ON TABLE public.pacs_realtime_notification_events IS
            'Durable hospital-scoped outbox used to replay callback and upload events over authenticated SSE.';
    END IF;
END;
$$;

DO $$
DECLARE
    min_partition_date DATE;
    old_default_exists BOOLEAN := TO_REGCLASS('public.pacs_worklist_histories_default') IS NOT NULL;
BEGIN
    IF TO_REGCLASS('public.pacs_worklist_histories') IS NOT NULL
       AND (
           (SELECT relkind FROM pg_class WHERE oid = 'public.pacs_worklist_histories'::REGCLASS) <> 'p'
           OR EXISTS (
               SELECT 1
               FROM pg_inherits inh
               JOIN pg_class child ON child.oid = inh.inhrelid
               WHERE inh.inhparent = 'public.pacs_worklist_histories'::REGCLASS
                 AND child.relname ~ '^pacs_worklist_histories_[0-9]{6}$'
           )
       ) THEN
        ALTER SEQUENCE IF EXISTS public.pacs_patient_queue_histories_id_seq OWNED BY NONE;
        ALTER TABLE public.pacs_worklist_histories RENAME TO pacs_worklist_histories_v197_old;

        CREATE TABLE IF NOT EXISTS public.pacs_worklist_histories
            (LIKE public.pacs_worklist_histories_v197_old INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS)
            PARTITION BY RANGE (created);

        ALTER TABLE public.pacs_worklist_histories
            ADD COLUMN IF NOT EXISTS retention_policy_id BIGINT,
            ADD COLUMN IF NOT EXISTS retain_until DATE,
            ADD COLUMN IF NOT EXISTS archive_after DATE,
            ADD COLUMN IF NOT EXISTS purge_after DATE;

        SELECT COALESCE(MIN(DATE_TRUNC('year', created)::DATE), DATE_TRUNC('year', CURRENT_DATE)::DATE)
        INTO min_partition_date
        FROM public.pacs_worklist_histories_v197_old;
        min_partition_date := LEAST(min_partition_date, DATE_TRUNC('year', CURRENT_DATE)::DATE);

        PERFORM pacs__create_initial_partitions('public', 'pacs_worklist_histories', 'YEAR', min_partition_date, 2);

        IF old_default_exists THEN
            CREATE TABLE IF NOT EXISTS public.pacs_worklist_histories_v197_default
                PARTITION OF public.pacs_worklist_histories DEFAULT;
        END IF;

        INSERT INTO public.pacs_worklist_histories (
            id,
            hospital_id,
            worklist_id,
            patient_id,
            from_status,
            to_status,
            action,
            reason,
            created,
            created_by,
            created_at,
            retention_policy_id,
            retain_until,
            archive_after,
            purge_after
        )
        SELECT
            id,
            hospital_id,
            worklist_id,
            patient_id,
            from_status,
            to_status,
            action,
            reason,
            created,
            created_by,
            created_at,
            NULL::BIGINT,
            NULL::DATE,
            NULL::DATE,
            NULL::DATE
        FROM public.pacs_worklist_histories_v197_old;

        DROP TABLE IF EXISTS public.pacs_worklist_histories_v197_old;

        IF old_default_exists
           AND TO_REGCLASS('public.pacs_worklist_histories_default') IS NULL
           AND TO_REGCLASS('public.pacs_worklist_histories_v197_default') IS NOT NULL THEN
            ALTER TABLE public.pacs_worklist_histories_v197_default
                RENAME TO pacs_worklist_histories_default;
        END IF;

        ALTER TABLE public.pacs_worklist_histories
            ADD CONSTRAINT pacs_worklist_histories_pkey PRIMARY KEY (id, created);

        ALTER SEQUENCE IF EXISTS public.pacs_patient_queue_histories_id_seq
            OWNED BY public.pacs_worklist_histories.id;

        CREATE INDEX IF NOT EXISTS idx_worklist_histories_hospital_worklist_created
            ON public.pacs_worklist_histories (hospital_id, worklist_id, created DESC, id DESC);
        CREATE INDEX IF NOT EXISTS idx_worklist_histories_hospital_created
            ON public.pacs_worklist_histories (hospital_id, created DESC, id DESC);
        CREATE INDEX IF NOT EXISTS idx_worklist_histories_patient_created
            ON public.pacs_worklist_histories (hospital_id, patient_id, created DESC, id DESC);
        CREATE INDEX IF NOT EXISTS idx_worklist_histories_purge_after
            ON public.pacs_worklist_histories (purge_after)
            WHERE purge_after IS NOT NULL;

        ALTER TABLE public.pacs_worklist_histories
            ADD CONSTRAINT fk_worklist_histories_patient_hospital
            FOREIGN KEY (patient_id, hospital_id)
            REFERENCES public.patients(id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
        ALTER TABLE public.pacs_worklist_histories
            ADD CONSTRAINT fk_worklist_histories_worklist_hospital
            FOREIGN KEY (worklist_id, hospital_id)
            REFERENCES public.pacs_worklists(id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
        ALTER TABLE public.pacs_worklist_histories
            ADD CONSTRAINT pacs_worklist_histories_hospital_id_fkey
            FOREIGN KEY (hospital_id)
            REFERENCES public.hospitals(id)
            NOT VALID;
    END IF;
END;
$$;

DO $$
DECLARE
    min_partition_date DATE;
    old_default_exists BOOLEAN := TO_REGCLASS('public.study_retention_delete_requests_default') IS NOT NULL;
BEGIN
    IF TO_REGCLASS('public.study_retention_delete_requests') IS NOT NULL
       AND (
           (SELECT relkind FROM pg_class WHERE oid = 'public.study_retention_delete_requests'::REGCLASS) <> 'p'
           OR EXISTS (
               SELECT 1
               FROM pg_inherits inh
               JOIN pg_class child ON child.oid = inh.inhrelid
               WHERE inh.inhparent = 'public.study_retention_delete_requests'::REGCLASS
                 AND child.relname ~ '^study_retention_delete_requests_[0-9]{6}$'
           )
       ) THEN
        ALTER SEQUENCE IF EXISTS public.study_retention_delete_requests_id_seq OWNED BY NONE;
        ALTER TABLE public.study_retention_delete_requests RENAME TO study_retention_delete_requests_v197_old;

        CREATE TABLE IF NOT EXISTS public.study_retention_delete_requests
            (LIKE public.study_retention_delete_requests_v197_old INCLUDING DEFAULTS INCLUDING STORAGE INCLUDING COMMENTS)
            PARTITION BY RANGE (created_at);

        ALTER TABLE public.study_retention_delete_requests
            ADD COLUMN IF NOT EXISTS retain_until DATE,
            ADD COLUMN IF NOT EXISTS archive_after DATE,
            ADD COLUMN IF NOT EXISTS purge_after DATE;

        SELECT COALESCE(MIN(DATE_TRUNC('year', created_at)::DATE), DATE_TRUNC('year', CURRENT_DATE)::DATE)
        INTO min_partition_date
        FROM public.study_retention_delete_requests_v197_old;
        min_partition_date := LEAST(min_partition_date, DATE_TRUNC('year', CURRENT_DATE)::DATE);

        PERFORM pacs__create_initial_partitions('public', 'study_retention_delete_requests', 'YEAR', min_partition_date, 2);

        IF old_default_exists THEN
            CREATE TABLE IF NOT EXISTS public.study_retention_delete_requests_v197_default
                PARTITION OF public.study_retention_delete_requests DEFAULT;
        END IF;

        INSERT INTO public.study_retention_delete_requests (
            id,
            public_id,
            hospital_id,
            study_id,
            policy_id,
            dicom_server_id,
            modality_id,
            status,
            expires_at,
            near_expiry_at,
            study_instance_uid,
            dicom_server_study_id,
            accession_number,
            reference_visit_code,
            patient_mrn,
            patient_name,
            requested_by,
            requested_at,
            approved_by,
            approved_at,
            rejected_by,
            rejected_at,
            deleted_at,
            decision_note,
            error_message,
            created_at,
            updated_at,
            retain_until,
            archive_after,
            purge_after
        )
        SELECT
            id,
            public_id,
            hospital_id,
            study_id,
            policy_id,
            dicom_server_id,
            modality_id,
            status,
            expires_at,
            near_expiry_at,
            study_instance_uid,
            dicom_server_study_id,
            accession_number,
            reference_visit_code,
            patient_mrn,
            patient_name,
            requested_by,
            requested_at,
            approved_by,
            approved_at,
            rejected_by,
            rejected_at,
            deleted_at,
            decision_note,
            error_message,
            created_at,
            updated_at,
            NULL::DATE,
            NULL::DATE,
            NULL::DATE
        FROM public.study_retention_delete_requests_v197_old;

        DROP TABLE IF EXISTS public.study_retention_delete_requests_v197_old;

        IF old_default_exists
           AND TO_REGCLASS('public.study_retention_delete_requests_default') IS NULL
           AND TO_REGCLASS('public.study_retention_delete_requests_v197_default') IS NOT NULL THEN
            ALTER TABLE public.study_retention_delete_requests_v197_default
                RENAME TO study_retention_delete_requests_default;
        END IF;

        ALTER TABLE public.study_retention_delete_requests
            ADD CONSTRAINT study_retention_delete_requests_pkey PRIMARY KEY (id, created_at),
            ADD CONSTRAINT ck_study_retention_delete_requests_status
            CHECK (
                status IN (
                    'PENDING_APPROVAL',
                    'APPROVED',
                    'DELETE_FAILED',
                    'DELETED',
                    'REJECTED',
                    'KEEP_PERMANENT'
                )
            );

        ALTER SEQUENCE IF EXISTS public.study_retention_delete_requests_id_seq
            OWNED BY public.study_retention_delete_requests.id;

        CREATE UNIQUE INDEX IF NOT EXISTS ux_study_retention_delete_requests_public_id
            ON public.study_retention_delete_requests (public_id, created_at);
        CREATE INDEX IF NOT EXISTS idx_study_retention_delete_requests_study_latest
            ON public.study_retention_delete_requests (hospital_id, study_id, created_at DESC, id DESC);
        CREATE INDEX IF NOT EXISTS idx_study_retention_delete_requests_status
            ON public.study_retention_delete_requests (hospital_id, status, updated_at DESC, id DESC);
        CREATE INDEX IF NOT EXISTS idx_study_retention_delete_requests_purge_after
            ON public.study_retention_delete_requests (purge_after)
            WHERE purge_after IS NOT NULL;
        CREATE INDEX IF NOT EXISTS idx_retention_requests_study_hospital
            ON public.study_retention_delete_requests (study_id, hospital_id)
            WHERE study_id IS NOT NULL;
        CREATE INDEX IF NOT EXISTS idx_retention_requests_server_hospital
            ON public.study_retention_delete_requests (dicom_server_id, hospital_id)
            WHERE dicom_server_id IS NOT NULL;

        ALTER TABLE public.study_retention_delete_requests
            ADD CONSTRAINT fk_retention_requests_study_hospital
            FOREIGN KEY (study_id, hospital_id)
            REFERENCES public.pacs_studies(id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
        ALTER TABLE public.study_retention_delete_requests
            ADD CONSTRAINT fk_retention_requests_server_hospital
            FOREIGN KEY (dicom_server_id, hospital_id)
            REFERENCES public.hospital_dicom_servers(id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
        ALTER TABLE public.study_retention_delete_requests
            ADD CONSTRAINT study_retention_delete_requests_hospital_id_fkey
            FOREIGN KEY (hospital_id)
            REFERENCES public.hospitals(id)
            NOT VALID;
        ALTER TABLE public.study_retention_delete_requests
            ADD CONSTRAINT study_retention_delete_requests_policy_id_fkey
            FOREIGN KEY (policy_id)
            REFERENCES public.study_retention_policies(id)
            ON DELETE SET NULL NOT VALID;
        ALTER TABLE public.study_retention_delete_requests
            ADD CONSTRAINT study_retention_delete_requests_modality_id_fkey
            FOREIGN KEY (modality_id)
            REFERENCES public.modalities(id)
            NOT VALID;
        ALTER TABLE public.study_retention_delete_requests
            ADD CONSTRAINT study_retention_delete_requests_requested_by_fkey
            FOREIGN KEY (requested_by)
            REFERENCES public.users(id)
            NOT VALID;
        ALTER TABLE public.study_retention_delete_requests
            ADD CONSTRAINT study_retention_delete_requests_approved_by_fkey
            FOREIGN KEY (approved_by)
            REFERENCES public.users(id)
            NOT VALID;
        ALTER TABLE public.study_retention_delete_requests
            ADD CONSTRAINT study_retention_delete_requests_rejected_by_fkey
            FOREIGN KEY (rejected_by)
            REFERENCES public.users(id)
            NOT VALID;
    END IF;
END;
$$;

INSERT INTO partition_maintenance_configs
    (
        parent_schema,
        parent_table,
        partition_column,
        partition_granularity,
        retention_mode,
        retention_months,
        future_months,
        future_partitions,
        allow_auto_drop,
        is_active
    )
VALUES
    ('public', 'user_logs', 'created', 'MONTH', 'FIXED_MONTHS', 12, 3, 3, TRUE, 1),
    ('public', 'system_activities', 'created', 'MONTH', 'FIXED_MONTHS', 12, 3, 3, TRUE, 1),
    ('public', 'dicom_server_callback_log', 'received_at', 'MONTH', 'FIXED_MONTHS', 12, 3, 3, TRUE, 1),
    ('public', 'pacs_realtime_notification_events', 'created_at', 'MONTH', 'FIXED_MONTHS', 12, 3, 3, TRUE, 1),
    ('public', 'pacs_worklist_histories', 'created', 'YEAR', 'POLICY_BASED', NULL, 2, 2, FALSE, 1),
    ('public', 'study_retention_delete_requests', 'created_at', 'YEAR', 'POLICY_BASED', NULL, 2, 2, FALSE, 1)
ON CONFLICT (parent_schema, parent_table)
DO UPDATE SET
    partition_column = EXCLUDED.partition_column,
    partition_granularity = EXCLUDED.partition_granularity,
    retention_mode = EXCLUDED.retention_mode,
    retention_months = EXCLUDED.retention_months,
    future_months = EXCLUDED.future_months,
    future_partitions = EXCLUDED.future_partitions,
    allow_auto_drop = EXCLUDED.allow_auto_drop,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

UPDATE partition_maintenance_configs
SET is_active = 2,
    allow_auto_drop = FALSE,
    updated_at = NOW()
WHERE parent_schema = 'public'
  AND parent_table IN ('pacs_worklists', 'pacs_studies');

CREATE OR REPLACE FUNCTION create_future_partitions()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    cfg RECORD;
    parent_reg REGCLASS;
    parent_kind "char";
    parent_qual TEXT;
    partition_name TEXT;
    month_or_year DATE;
    end_value DATE;
    default_name TEXT;
    created_count INTEGER := 0;
BEGIN
    FOR cfg IN
        SELECT
            parent_schema,
            parent_table,
            partition_column,
            partition_granularity,
            retention_mode,
            retention_months,
            future_partitions
        FROM partition_maintenance_configs
        WHERE is_active = 1
        ORDER BY parent_schema, parent_table
    LOOP
        parent_qual := FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table);
        parent_reg := TO_REGCLASS(parent_qual);

        IF parent_reg IS NULL THEN
            RAISE NOTICE 'partition maintenance skip %.%: parent table missing',
                cfg.parent_schema, cfg.parent_table;
            CONTINUE;
        END IF;

        SELECT relkind INTO parent_kind FROM pg_class WHERE oid = parent_reg;
        IF parent_kind <> 'p' THEN
            RAISE NOTICE 'partition maintenance skip %.%: table is not a native partitioned parent',
                cfg.parent_schema, cfg.parent_table;
            CONTINUE;
        END IF;

        default_name := cfg.parent_table || '_default';
        IF TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, default_name)) IS NULL THEN
            EXECUTE FORMAT(
                'CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %s DEFAULT',
                cfg.parent_schema, default_name, parent_qual
            );
            created_count := created_count + 1;
            RAISE NOTICE 'created default partition %.%', cfg.parent_schema, default_name;
        END IF;
        PERFORM ensure_partition_child_indexes(cfg.parent_schema, cfg.parent_table, cfg.parent_schema, default_name);

        IF cfg.partition_granularity = 'MONTH' THEN
            FOR month_or_year IN
                SELECT GENERATE_SERIES(
                    CASE
                        WHEN cfg.retention_mode = 'FIXED_MONTHS' THEN
                            (DATE_TRUNC('month', CURRENT_DATE) - MAKE_INTERVAL(months => cfg.retention_months))::DATE
                        ELSE
                            DATE_TRUNC('month', CURRENT_DATE)::DATE
                    END,
                    (DATE_TRUNC('month', CURRENT_DATE) + MAKE_INTERVAL(months => cfg.future_partitions))::DATE,
                    INTERVAL '1 month'
                )::DATE
            LOOP
                end_value := (month_or_year + INTERVAL '1 month')::DATE;
                partition_name := cfg.parent_table || '_' || TO_CHAR(month_or_year, 'YYYYMM');
                IF TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, partition_name)) IS NULL THEN
                    EXECUTE FORMAT(
                        'CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
                        cfg.parent_schema, partition_name, parent_qual, month_or_year, end_value
                    );
                    created_count := created_count + 1;
                    RAISE NOTICE 'created monthly partition %.% from % to %',
                        cfg.parent_schema, partition_name, month_or_year, end_value;
                END IF;
                PERFORM ensure_partition_child_indexes(cfg.parent_schema, cfg.parent_table, cfg.parent_schema, partition_name);
            END LOOP;
        ELSIF cfg.partition_granularity = 'YEAR' THEN
            FOR month_or_year IN
                SELECT GENERATE_SERIES(
                    DATE_TRUNC('year', CURRENT_DATE)::DATE,
                    (DATE_TRUNC('year', CURRENT_DATE) + MAKE_INTERVAL(years => cfg.future_partitions))::DATE,
                    INTERVAL '1 year'
                )::DATE
            LOOP
                end_value := (month_or_year + INTERVAL '1 year')::DATE;
                partition_name := cfg.parent_table || '_' || TO_CHAR(month_or_year, 'YYYY');
                IF TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, partition_name)) IS NULL THEN
                    EXECUTE FORMAT(
                        'CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
                        cfg.parent_schema, partition_name, parent_qual, month_or_year, end_value
                    );
                    created_count := created_count + 1;
                    RAISE NOTICE 'created yearly partition %.% from % to %',
                        cfg.parent_schema, partition_name, month_or_year, end_value;
                END IF;
            END LOOP;
        ELSE
            RAISE NOTICE 'partition maintenance skip %.%: unsupported granularity %',
                cfg.parent_schema, cfg.parent_table, cfg.partition_granularity;
        END IF;
    END LOOP;

    RETURN created_count;
END
$fn$;

CREATE OR REPLACE FUNCTION drop_expired_fixed_partitions()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    cfg RECORD;
    parent_reg REGCLASS;
    parent_kind "char";
    child RECORD;
    partition_month DATE;
    cutoff_month DATE;
    dropped_count INTEGER := 0;
BEGIN
    FOR cfg IN
        SELECT parent_schema, parent_table, retention_months
        FROM partition_maintenance_configs
        WHERE is_active = 1
          AND retention_mode = 'FIXED_MONTHS'
          AND partition_granularity = 'MONTH'
          AND allow_auto_drop = TRUE
        ORDER BY parent_schema, parent_table
    LOOP
        parent_reg := TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table));
        IF parent_reg IS NULL THEN
            CONTINUE;
        END IF;

        SELECT relkind INTO parent_kind FROM pg_class WHERE oid = parent_reg;
        IF parent_kind <> 'p' THEN
            RAISE NOTICE 'fixed partition drop skip %.%: table is not a native partitioned parent',
                cfg.parent_schema, cfg.parent_table;
            CONTINUE;
        END IF;

        cutoff_month := (DATE_TRUNC('month', CURRENT_DATE) - MAKE_INTERVAL(months => cfg.retention_months))::DATE;

        FOR child IN
            SELECT child_ns.nspname AS child_schema, child_cls.relname AS child_table
            FROM pg_inherits inh
            JOIN pg_class child_cls ON child_cls.oid = inh.inhrelid
            JOIN pg_namespace child_ns ON child_ns.oid = child_cls.relnamespace
            WHERE inh.inhparent = parent_reg
            ORDER BY child_cls.relname
        LOOP
            IF child.child_table = cfg.parent_table || '_default' THEN
                CONTINUE;
            END IF;

            IF child.child_table !~ ('^' || cfg.parent_table || '_[0-9]{4}(0[1-9]|1[0-2])$') THEN
                RAISE NOTICE 'fixed partition drop skip %.%: not parent_YYYYMM',
                    child.child_schema, child.child_table;
                CONTINUE;
            END IF;

            partition_month := TO_DATE(RIGHT(child.child_table, 6), 'YYYYMM');
            IF partition_month < cutoff_month THEN
                RAISE NOTICE 'dropping expired fixed-retention partition %.% (% older than cutoff %)',
                    child.child_schema, child.child_table, partition_month, cutoff_month;
                EXECUTE FORMAT('DROP TABLE IF EXISTS %I.%I', child.child_schema, child.child_table);
                dropped_count := dropped_count + 1;
            END IF;
        END LOOP;
    END LOOP;

    RETURN dropped_count;
END
$fn$;

CREATE OR REPLACE FUNCTION cleanup_policy_based_retention_data(p_dry_run BOOLEAN DEFAULT TRUE)
RETURNS TABLE(parent_table TEXT, eligible_rows BIGINT, action_taken TEXT)
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    eligible BIGINT;
BEGIN
    SELECT COUNT(*) INTO eligible
    FROM public.pacs_worklist_histories
    WHERE purge_after IS NOT NULL
      AND purge_after < CURRENT_DATE;

    IF NOT p_dry_run AND eligible > 0 THEN
        IF TO_REGCLASS('public.pacs_worklist_histories_archive') IS NOT NULL THEN
            EXECUTE
                'INSERT INTO public.pacs_worklist_histories_archive ' ||
                'SELECT *, NOW() AS archived_at FROM public.pacs_worklist_histories ' ||
                'WHERE purge_after IS NOT NULL AND purge_after < CURRENT_DATE ' ||
                'ON CONFLICT DO NOTHING';
        END IF;
        DELETE FROM public.pacs_worklist_histories
        WHERE purge_after IS NOT NULL
          AND purge_after < CURRENT_DATE;
        RETURN QUERY SELECT 'pacs_worklist_histories'::TEXT, eligible, 'deleted_policy_eligible_rows'::TEXT;
    ELSE
        RETURN QUERY SELECT 'pacs_worklist_histories'::TEXT, eligible, 'dry_run_only'::TEXT;
    END IF;

    SELECT COUNT(*) INTO eligible
    FROM public.study_retention_delete_requests
    WHERE purge_after IS NOT NULL
      AND purge_after < CURRENT_DATE;

    IF NOT p_dry_run AND eligible > 0 THEN
        IF TO_REGCLASS('public.study_retention_delete_requests_archive') IS NOT NULL THEN
            EXECUTE
                'INSERT INTO public.study_retention_delete_requests_archive ' ||
                'SELECT *, NOW() AS archived_at FROM public.study_retention_delete_requests ' ||
                'WHERE purge_after IS NOT NULL AND purge_after < CURRENT_DATE ' ||
                'ON CONFLICT DO NOTHING';
        END IF;
        DELETE FROM public.study_retention_delete_requests
        WHERE purge_after IS NOT NULL
          AND purge_after < CURRENT_DATE;
        RETURN QUERY SELECT 'study_retention_delete_requests'::TEXT, eligible, 'deleted_policy_eligible_rows'::TEXT;
    ELSE
        RETURN QUERY SELECT 'study_retention_delete_requests'::TEXT, eligible, 'dry_run_only'::TEXT;
    END IF;
END
$fn$;

CREATE OR REPLACE FUNCTION drop_policy_partitions_if_fully_expired(p_dry_run BOOLEAN DEFAULT TRUE)
RETURNS TABLE(parent_table TEXT, partition_table TEXT, total_rows BIGINT, protected_rows BIGINT, action_taken TEXT)
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    cfg RECORD;
    parent_reg REGCLASS;
    child RECORD;
    total_count BIGINT;
    protected_count BIGINT;
BEGIN
    FOR cfg IN
        SELECT parent_schema, parent_table
        FROM partition_maintenance_configs
        WHERE is_active = 1
          AND retention_mode = 'POLICY_BASED'
          AND partition_granularity = 'YEAR'
          AND allow_auto_drop = FALSE
        ORDER BY parent_schema, parent_table
    LOOP
        parent_reg := TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table));
        IF parent_reg IS NULL THEN
            CONTINUE;
        END IF;

        FOR child IN
            SELECT child_ns.nspname AS child_schema, child_cls.relname AS child_table
            FROM pg_inherits inh
            JOIN pg_class child_cls ON child_cls.oid = inh.inhrelid
            JOIN pg_namespace child_ns ON child_ns.oid = child_cls.relnamespace
            WHERE inh.inhparent = parent_reg
              AND child_cls.relname ~ ('^' || cfg.parent_table || '_[0-9]{4}$')
            ORDER BY child_cls.relname
        LOOP
            EXECUTE FORMAT('SELECT COUNT(*) FROM %I.%I', child.child_schema, child.child_table)
            INTO total_count;

            EXECUTE FORMAT(
                'SELECT COUNT(*) FROM %I.%I WHERE purge_after IS NULL OR purge_after >= CURRENT_DATE',
                child.child_schema, child.child_table
            )
            INTO protected_count;

            IF total_count > 0 AND protected_count = 0 THEN
                IF p_dry_run THEN
                    RETURN QUERY SELECT cfg.parent_table, child.child_table, total_count, protected_count, 'dry_run_drop_candidate'::TEXT;
                ELSE
                    RAISE NOTICE 'dropping fully policy-expired yearly partition %.%',
                        child.child_schema, child.child_table;
                    EXECUTE FORMAT('DROP TABLE IF EXISTS %I.%I', child.child_schema, child.child_table);
                    RETURN QUERY SELECT cfg.parent_table, child.child_table, total_count, protected_count, 'dropped'::TEXT;
                END IF;
            ELSE
                RETURN QUERY SELECT cfg.parent_table, child.child_table, total_count, protected_count, 'kept'::TEXT;
            END IF;
        END LOOP;
    END LOOP;
END
$fn$;

CREATE OR REPLACE FUNCTION run_partition_maintenance()
RETURNS TEXT
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    cfg RECORD;
    parent_reg REGCLASS;
    parent_kind "char";
    created_count INTEGER := 0;
    dropped_count INTEGER := 0;
    analyzed_count INTEGER := 0;
    policy_row RECORD;
    policy_rows BIGINT := 0;
    summary TEXT;
BEGIN
    SELECT create_future_partitions() INTO created_count;
    SELECT drop_expired_fixed_partitions() INTO dropped_count;

    FOR policy_row IN
        SELECT * FROM cleanup_policy_based_retention_data(TRUE)
    LOOP
        policy_rows := policy_rows + COALESCE(policy_row.eligible_rows, 0);
        RAISE NOTICE 'policy retention dry-run %. eligible_rows=%',
            policy_row.parent_table, policy_row.eligible_rows;
    END LOOP;

    FOR cfg IN
        SELECT parent_schema, parent_table
        FROM partition_maintenance_configs
        WHERE is_active = 1
        ORDER BY parent_schema, parent_table
    LOOP
        parent_reg := TO_REGCLASS(FORMAT('%I.%I', cfg.parent_schema, cfg.parent_table));
        IF parent_reg IS NULL THEN
            CONTINUE;
        END IF;

        SELECT relkind INTO parent_kind FROM pg_class WHERE oid = parent_reg;
        IF parent_kind = 'p' THEN
            EXECUTE FORMAT('ANALYZE %I.%I', cfg.parent_schema, cfg.parent_table);
            analyzed_count := analyzed_count + 1;
        END IF;
    END LOOP;

    summary := FORMAT(
        'partition maintenance complete: created=%s, dropped_fixed=%s, policy_dry_run_eligible=%s, analyzed=%s',
        created_count,
        dropped_count,
        policy_rows,
        analyzed_count
    );
    RAISE NOTICE '%', summary;
    RETURN summary;
END
$fn$;

CREATE OR REPLACE FUNCTION create_future_monthly_partitions()
RETURNS INTEGER
LANGUAGE sql
SECURITY INVOKER
AS $fn$
    SELECT create_future_partitions();
$fn$;

CREATE OR REPLACE FUNCTION drop_old_monthly_partitions()
RETURNS INTEGER
LANGUAGE sql
SECURITY INVOKER
AS $fn$
    SELECT drop_expired_fixed_partitions();
$fn$;

CREATE OR REPLACE FUNCTION run_monthly_partition_maintenance()
RETURNS TEXT
LANGUAGE sql
SECURITY INVOKER
AS $fn$
    SELECT run_partition_maintenance();
$fn$;

CREATE OR REPLACE FUNCTION schedule_monthly_partition_maintenance_pg_cron()
RETURNS TEXT
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    job_id BIGINT;
BEGIN
    IF TO_REGNAMESPACE('cron') IS NULL THEN
        RETURN 'pg_cron schema is not installed; use the Spring Boot scheduler.';
    END IF;

    BEGIN
        EXECUTE 'SELECT cron.unschedule($1)'
        USING 'emr-pacs-partition-maintenance';
    EXCEPTION
        WHEN OTHERS THEN
            NULL;
    END;

    BEGIN
        EXECUTE 'SELECT cron.schedule($1, $2, $3)'
        INTO job_id
        USING
            'emr-pacs-partition-maintenance',
            '0 2 1 * *',
            'SELECT run_partition_maintenance();';
    EXCEPTION
        WHEN undefined_function OR invalid_schema_name THEN
            RETURN 'pg_cron functions are not available; use the Spring Boot scheduler.';
    END;

    RETURN FORMAT('pg_cron scheduled partition maintenance with job id %s', job_id);
END
$fn$;

COMMENT ON TABLE partition_maintenance_configs IS
    'Configures fixed monthly technical-log retention separately from policy-based yearly medical/audit retention.';
COMMENT ON FUNCTION create_future_partitions() IS
    'Creates future MONTH or YEAR partitions from partition_maintenance_configs and always keeps default partitions.';
COMMENT ON FUNCTION drop_expired_fixed_partitions() IS
    'Drops only native fixed-retention monthly child partitions named parent_YYYYMM; never drops policy-based partitions.';
COMMENT ON FUNCTION cleanup_policy_based_retention_data(boolean) IS
    'Dry-runs or deletes only policy rows whose purge_after is set and older than current_date.';
COMMENT ON FUNCTION drop_policy_partitions_if_fully_expired(boolean) IS
    'Drops yearly policy partitions only when every row is purge eligible; dry-run by default.';
COMMENT ON FUNCTION run_partition_maintenance() IS
    'Creates future partitions, drops expired fixed technical logs, dry-runs policy cleanup, and analyzes active partition parents.';

DROP FUNCTION IF EXISTS pacs__create_initial_partitions(TEXT, TEXT, TEXT, DATE, INTEGER);
