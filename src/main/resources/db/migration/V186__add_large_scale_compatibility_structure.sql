-- Additive compatibility stage for the 100M-row refactor.
-- No existing column is removed and no historical row is rewritten here.
-- Large-table backfills are intentionally handled by bounded operational SQL.

ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

ALTER TABLE pacs_studies
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

ALTER TABLE pacs_worklist_histories
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

ALTER TABLE system_activities
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

ALTER TABLE user_logs
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

ALTER TABLE pacs_result_images
    ADD COLUMN IF NOT EXISTS hospital_id BIGINT,
    ADD COLUMN IF NOT EXISTS modality_id BIGINT,
    ADD COLUMN IF NOT EXISTS study_id BIGINT,
    ADD COLUMN IF NOT EXISTS worklist_id BIGINT,
    ADD COLUMN IF NOT EXISTS file_sha256 CHAR(64);

ALTER TABLE dicom_server_callback_log
    ADD COLUMN IF NOT EXISTS hospital_id BIGINT,
    ADD COLUMN IF NOT EXISTS dicom_server_id BIGINT,
    ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS payload_sha256 CHAR(64),
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS last_received_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS pacs_result_versions (
    id             BIGSERIAL PRIMARY KEY,
    public_id      UUID NOT NULL DEFAULT md5(random()::text || clock_timestamp()::text)::uuid,
    hospital_id    BIGINT NOT NULL,
    result_id      BIGINT NOT NULL,
    version_no     INTEGER NOT NULL,
    modality_id    BIGINT NOT NULL,
    study_id       BIGINT,
    worklist_id    BIGINT,
    patient_id     BIGINT,
    result_date    DATE NOT NULL,
    template_id    BIGINT,
    result_text    TEXT,
    status         VARCHAR(30) NOT NULL,
    completed      BOOLEAN NOT NULL,
    is_active      SMALLINT NOT NULL,
    changed_by     BIGINT,
    change_reason  VARCHAR(255),
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS pacs_daily_stats (
    hospital_id             BIGINT NOT NULL,
    stat_date               DATE NOT NULL,
    modality_id             BIGINT NOT NULL DEFAULT 0,
    waiting_count           BIGINT NOT NULL DEFAULT 0,
    in_progress_count       BIGINT NOT NULL DEFAULT 0,
    cancelled_count         BIGINT NOT NULL DEFAULT 0,
    failed_count            BIGINT NOT NULL DEFAULT 0,
    received_study_count    BIGINT NOT NULL DEFAULT 0,
    completed_result_count  BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (hospital_id, stat_date, modality_id)
);

CREATE OR REPLACE FUNCTION pacs_sync_legacy_audit_timestamps()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'patients' THEN
        NEW.created_at := COALESCE(NEW.created_at, NEW.created, NOW());
        IF TG_OP = 'INSERT' THEN
            NEW.updated_at := COALESCE(NEW.updated_at, NEW.modified, NEW.created_at);
        ELSIF NEW.updated_at IS NULL OR NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
            NEW.updated_at := COALESCE(NEW.modified, NOW());
        END IF;
    ELSIF TG_TABLE_NAME = 'pacs_studies' THEN
        NEW.created_at := COALESCE(NEW.created_at, NEW.created, NOW());
        IF TG_OP = 'INSERT' THEN
            NEW.updated_at := COALESCE(NEW.updated_at, NEW.modified, NEW.created_at);
        ELSIF NEW.updated_at IS NULL OR NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
            NEW.updated_at := COALESCE(NEW.modified, NOW());
        END IF;
    ELSIF TG_TABLE_NAME = 'pacs_worklist_histories' THEN
        NEW.created_at := COALESCE(NEW.created_at, NEW.created, NOW());
    ELSIF TG_TABLE_NAME = 'system_activities' THEN
        NEW.created_at := COALESCE(NEW.created_at, NEW.created, NOW());
    ELSIF TG_TABLE_NAME = 'user_logs' THEN
        NEW.created_at := COALESCE(NEW.created_at, NEW.created, NOW());
    END IF;
    RETURN NEW;
END;
$$;

DO $$
DECLARE
    target_table TEXT;
    trigger_name TEXT;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'patients',
        'pacs_studies',
        'pacs_worklist_histories',
        'system_activities',
        'user_logs'
    ]
    LOOP
        trigger_name := 'trg_' || target_table || '_sync_audit_timestamps';
        IF NOT EXISTS (
            SELECT 1
            FROM pg_trigger
            WHERE tgname = trigger_name
              AND tgrelid = to_regclass('public.' || target_table)
        ) THEN
            EXECUTE format(
                'CREATE TRIGGER %I BEFORE INSERT OR UPDATE ON %I '
                || 'FOR EACH ROW EXECUTE FUNCTION pacs_sync_legacy_audit_timestamps()',
                trigger_name,
                target_table
            );
        END IF;
    END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION pacs_set_result_image_scope()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    result_scope RECORD;
BEGIN
    SELECT
        hospital_id,
        modality_id,
        study_id,
        worklist_id
    INTO result_scope
    FROM pacs_results
    WHERE id = NEW.result_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PACS result % does not exist', NEW.result_id;
    END IF;

    NEW.hospital_id := result_scope.hospital_id;
    NEW.modality_id := result_scope.modality_id;
    NEW.study_id := result_scope.study_id;
    NEW.worklist_id := result_scope.worklist_id;
    RETURN NEW;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'trg_pacs_result_images_set_scope'
          AND tgrelid = 'pacs_result_images'::regclass
    ) THEN
        CREATE TRIGGER trg_pacs_result_images_set_scope
        BEFORE INSERT OR UPDATE OF result_id
        ON pacs_result_images
        FOR EACH ROW
        EXECUTE FUNCTION pacs_set_result_image_scope();
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION pacs_capture_result_version()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(
        OLD.hospital_id,
        OLD.modality_id,
        OLD.study_id,
        OLD.worklist_id,
        OLD.patient_id,
        OLD.result_date,
        OLD.template_id,
        OLD.result_text,
        OLD.status,
        OLD.completed,
        OLD.is_active
    ) IS DISTINCT FROM ROW(
        NEW.hospital_id,
        NEW.modality_id,
        NEW.study_id,
        NEW.worklist_id,
        NEW.patient_id,
        NEW.result_date,
        NEW.template_id,
        NEW.result_text,
        NEW.status,
        NEW.completed,
        NEW.is_active
    ) THEN
        INSERT INTO pacs_result_versions (
            hospital_id,
            result_id,
            version_no,
            modality_id,
            study_id,
            worklist_id,
            patient_id,
            result_date,
            template_id,
            result_text,
            status,
            completed,
            is_active,
            changed_by,
            change_reason,
            changed_at
        )
        VALUES (
            OLD.hospital_id,
            OLD.id,
            COALESCE((
                SELECT MAX(version_no) + 1
                FROM pacs_result_versions
                WHERE result_id = OLD.id
            ), 1),
            OLD.modality_id,
            OLD.study_id,
            OLD.worklist_id,
            OLD.patient_id,
            OLD.result_date,
            OLD.template_id,
            OLD.result_text,
            OLD.status,
            OLD.completed,
            OLD.is_active,
            OLD.created_by,
            'RESULT_UPDATED',
            NOW()
        );
    END IF;
    RETURN NEW;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'trg_pacs_results_capture_version'
          AND tgrelid = 'pacs_results'::regclass
    ) THEN
        CREATE TRIGGER trg_pacs_results_capture_version
        BEFORE UPDATE
        ON pacs_results
        FOR EACH ROW
        EXECUTE FUNCTION pacs_capture_result_version();
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION pacs_sync_worklist_primary_study()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.study_id IS NOT NULL
       AND (TG_OP = 'INSERT' OR NEW.study_id IS DISTINCT FROM OLD.study_id) THEN
        UPDATE pacs_worklist_study_links
        SET is_primary = 2
        WHERE hospital_id = NEW.hospital_id
          AND worklist_id = NEW.id
          AND is_primary = 1
          AND study_id <> NEW.study_id;

        INSERT INTO pacs_worklist_study_links (
            hospital_id,
            worklist_id,
            study_id,
            is_primary,
            linked_at,
            created_by
        )
        VALUES (
            NEW.hospital_id,
            NEW.id,
            NEW.study_id,
            1,
            COALESCE(NEW.image_received_at, NEW.received_at, NOW()),
            NEW.modified_by
        )
        ON CONFLICT (hospital_id, worklist_id, study_id)
        DO UPDATE SET
            is_primary = 1,
            linked_at = COALESCE(
                pacs_worklist_study_links.linked_at,
                EXCLUDED.linked_at
            );
    END IF;
    RETURN NEW;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'trg_pacs_worklists_sync_primary_study'
          AND tgrelid = 'pacs_worklists'::regclass
    ) THEN
        CREATE TRIGGER trg_pacs_worklists_sync_primary_study
        AFTER INSERT OR UPDATE OF study_id
        ON pacs_worklists
        FOR EACH ROW
        EXECUTE FUNCTION pacs_sync_worklist_primary_study();
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION pacs_sync_primary_link_to_worklist()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.is_primary = 1 THEN
        UPDATE pacs_worklists
        SET
            study_id = NEW.study_id,
            modified_at = NOW(),
            modified = NOW()
        WHERE id = NEW.worklist_id
          AND hospital_id = NEW.hospital_id
          AND study_id IS DISTINCT FROM NEW.study_id;
    END IF;
    RETURN NEW;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'trg_pacs_worklist_study_links_sync_worklist'
          AND tgrelid = 'pacs_worklist_study_links'::regclass
    ) THEN
        CREATE TRIGGER trg_pacs_worklist_study_links_sync_worklist
        AFTER INSERT OR UPDATE OF study_id, is_primary
        ON pacs_worklist_study_links
        FOR EACH ROW
        EXECUTE FUNCTION pacs_sync_primary_link_to_worklist();
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION pacs_refresh_daily_stats(
    target_date DATE,
    target_hospital_id BIGINT DEFAULT NULL
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    affected_rows INTEGER;
BEGIN
    INSERT INTO pacs_daily_stats (
        hospital_id,
        stat_date,
        modality_id,
        waiting_count,
        in_progress_count,
        cancelled_count,
        failed_count,
        received_study_count,
        completed_result_count,
        created_at,
        updated_at
    )
    SELECT
        h.id,
        target_date,
        0,
        COUNT(*) FILTER (
            WHERE w.status = 1
              AND COALESCE(w.scheduled_date, w.created_at::date, w.created::date) = target_date
        ),
        COUNT(*) FILTER (
            WHERE w.status = 2
              AND COALESCE(w.scheduled_date, w.created_at::date, w.created::date) = target_date
        ),
        COUNT(*) FILTER (
            WHERE w.status = 3
              AND COALESCE(w.scheduled_date, w.created_at::date, w.created::date) = target_date
        ),
        COUNT(*) FILTER (
            WHERE w.status = 4
              AND COALESCE(w.scheduled_date, w.created_at::date, w.created::date) = target_date
        ),
        (
            SELECT COUNT(*)
            FROM pacs_studies s
            WHERE s.hospital_id = h.id
              AND s.is_active = 1
              AND COALESCE(
                    s.image_received_at::date,
                    s.received_at::date,
                    s.study_date,
                    s.created::date
                  ) = target_date
        ),
        (
            SELECT COUNT(*)
            FROM pacs_results r
            WHERE r.hospital_id = h.id
              AND r.is_active = 1
              AND r.completed = TRUE
              AND r.result_date = target_date
        ),
        NOW(),
        NOW()
    FROM hospitals h
    LEFT JOIN pacs_worklists w
        ON w.hospital_id = h.id
       AND COALESCE(w.scheduled_date, w.created_at::date, w.created::date) = target_date
    WHERE h.is_active = 1
      AND (target_hospital_id IS NULL OR h.id = target_hospital_id)
    GROUP BY h.id
    ON CONFLICT (hospital_id, stat_date, modality_id)
    DO UPDATE SET
        waiting_count = EXCLUDED.waiting_count,
        in_progress_count = EXCLUDED.in_progress_count,
        cancelled_count = EXCLUDED.cancelled_count,
        failed_count = EXCLUDED.failed_count,
        received_study_count = EXCLUDED.received_study_count,
        completed_result_count = EXCLUDED.completed_result_count,
        updated_at = NOW();

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    RETURN affected_rows;
END;
$$;

COMMENT ON COLUMN pacs_worklists.study_id IS
    'Compatibility pointer to the primary study. pacs_worklist_study_links is the canonical relation.';

COMMENT ON TABLE pacs_result_versions IS
    'Immutable snapshots captured before meaningful PACS result updates.';

COMMENT ON TABLE pacs_daily_stats IS
    'Hospital daily summary used by dashboards instead of live counts on large clinical tables.';
