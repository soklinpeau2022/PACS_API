-- V199: Weekly PACS list caches for fast default Worklist/Study screens.
--
-- Main source-of-truth tables remain pacs_worklists and pacs_studies. These
-- cache tables keep the same IDs as the main rows and can be rebuilt at any
-- time from the source tables.
--
-- MIGRATION-SAFETY: allowed-data-loss

CREATE TABLE IF NOT EXISTS public.pacs_worklists_week_cache (
    id BIGINT PRIMARY KEY,
    public_id UUID NOT NULL,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    modality_id BIGINT,
    dicom_route_id BIGINT,
    study_id BIGINT,
    visit_code VARCHAR(80),
    status SMALLINT NOT NULL,
    scheduled_date DATE,
    scheduled_time TIME,
    study_description TEXT,
    dicom_server_worklist_id VARCHAR(255),
    dicom_server_worklist_path TEXT,
    sent_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ,
    image_received_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ,
    created TIMESTAMPTZ,
    cached_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_worklists_week_cache_public_id
    ON public.pacs_worklists_week_cache (public_id);

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_week_cache_hospital_status_created
    ON public.pacs_worklists_week_cache (hospital_id, status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_week_cache_hospital_created
    ON public.pacs_worklists_week_cache (hospital_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_week_cache_hospital_patient
    ON public.pacs_worklists_week_cache (hospital_id, patient_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_week_cache_hospital_modality_status
    ON public.pacs_worklists_week_cache (hospital_id, modality_id, status, created_at DESC, id DESC)
    WHERE modality_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_week_cache_visit_code
    ON public.pacs_worklists_week_cache (hospital_id, LOWER(visit_code))
    WHERE visit_code IS NOT NULL AND BTRIM(visit_code) <> '';

CREATE TABLE IF NOT EXISTS public.pacs_studies_week_cache (
    id BIGINT PRIMARY KEY,
    public_id UUID NOT NULL,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    modality_id BIGINT,
    study_instance_uid VARCHAR(200) NOT NULL,
    accession_number VARCHAR(100),
    reference_visit_code VARCHAR(120),
    modality VARCHAR(20),
    study_date DATE,
    received_at TIMESTAMPTZ,
    image_received_at TIMESTAMPTZ,
    study_description TEXT,
    status SMALLINT,
    is_active SMALLINT NOT NULL DEFAULT 1,
    dicom_server_id BIGINT,
    dicom_server_study_id VARCHAR(255),
    dicom_server_patient_id VARCHAR(255),
    dicom_server_series_id VARCHAR(255),
    instance_count INTEGER,
    institution_name VARCHAR(255),
    created TIMESTAMPTZ,
    modified TIMESTAMPTZ,
    created_at TIMESTAMPTZ,
    cached_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_studies_week_cache_public_id
    ON public.pacs_studies_week_cache (public_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_studies_week_cache_study_uid
    ON public.pacs_studies_week_cache (hospital_id, study_instance_uid);

CREATE INDEX IF NOT EXISTS idx_pacs_studies_week_cache_hospital_received
    ON public.pacs_studies_week_cache (hospital_id, received_at DESC, id DESC)
    WHERE received_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_week_cache_hospital_study_date
    ON public.pacs_studies_week_cache (hospital_id, study_date DESC, id DESC)
    WHERE study_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_week_cache_hospital_patient_received
    ON public.pacs_studies_week_cache (hospital_id, patient_id, received_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_pacs_studies_week_cache_hospital_accession
    ON public.pacs_studies_week_cache (hospital_id, accession_number)
    WHERE accession_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_week_cache_hospital_modality_received
    ON public.pacs_studies_week_cache (hospital_id, modality_id, received_at DESC, id DESC)
    WHERE modality_id IS NOT NULL;

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
        error_message,
        created_at,
        modified_at,
        created,
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
        error_message,
        created_at,
        modified_at,
        created,
        NOW()
    FROM public.pacs_worklists
    WHERE id = p_worklist_id
      AND COALESCE(created_at, created) >= NOW() - INTERVAL '7 days';
END
$fn$;

CREATE OR REPLACE FUNCTION sync_pacs_study_week_cache(p_study_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
BEGIN
    DELETE FROM public.pacs_studies_week_cache
    WHERE id = p_study_id;

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
    WHERE id = p_study_id
      AND is_active = 1
      AND COALESCE(image_received_at, received_at, created_at, created) >= NOW() - INTERVAL '7 days';
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
        error_message,
        created_at,
        modified_at,
        created,
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
        error_message,
        created_at,
        modified_at,
        created,
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

    RETURN FORMAT('PACS weekly cache refreshed: worklists=%s, studies=%s', worklist_rows, study_rows);
END
$fn$;

CREATE OR REPLACE FUNCTION cleanup_pacs_week_cache()
RETURNS TEXT
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
DECLARE
    worklist_rows BIGINT := 0;
    study_rows BIGINT := 0;
BEGIN
    DELETE FROM public.pacs_worklists_week_cache
    WHERE created_at < NOW() - INTERVAL '7 days';
    GET DIAGNOSTICS worklist_rows = ROW_COUNT;

    DELETE FROM public.pacs_studies_week_cache
    WHERE COALESCE(image_received_at, received_at, created_at, created) < NOW() - INTERVAL '7 days'
       OR is_active <> 1;
    GET DIAGNOSTICS study_rows = ROW_COUNT;

    ANALYZE public.pacs_worklists_week_cache;
    ANALYZE public.pacs_studies_week_cache;

    RETURN FORMAT('PACS weekly cache cleanup complete: worklists_deleted=%s, studies_deleted=%s', worklist_rows, study_rows);
END
$fn$;

CREATE OR REPLACE FUNCTION pacs_worklist_week_cache_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM public.pacs_worklists_week_cache WHERE id = OLD.id;
        RETURN OLD;
    END IF;

    PERFORM sync_pacs_worklist_week_cache(NEW.id);
    RETURN NEW;
END
$fn$;

CREATE OR REPLACE FUNCTION pacs_study_week_cache_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM public.pacs_studies_week_cache WHERE id = OLD.id;
        RETURN OLD;
    END IF;

    PERFORM sync_pacs_study_week_cache(NEW.id);
    RETURN NEW;
END
$fn$;

DROP TRIGGER IF EXISTS trg_pacs_worklists_week_cache_sync ON public.pacs_worklists;
CREATE TRIGGER trg_pacs_worklists_week_cache_sync
AFTER INSERT OR UPDATE OR DELETE ON public.pacs_worklists
FOR EACH ROW
EXECUTE FUNCTION pacs_worklist_week_cache_trigger();

DROP TRIGGER IF EXISTS trg_pacs_studies_week_cache_sync ON public.pacs_studies;
CREATE TRIGGER trg_pacs_studies_week_cache_sync
AFTER INSERT OR UPDATE OR DELETE ON public.pacs_studies
FOR EACH ROW
EXECUTE FUNCTION pacs_study_week_cache_trigger();

COMMENT ON TABLE public.pacs_worklists_week_cache IS
    'Seven-day rebuildable cache for default Worklist list screens. IDs match pacs_worklists; main table remains source of truth.';

COMMENT ON TABLE public.pacs_studies_week_cache IS
    'Seven-day rebuildable cache for default Study list screens. IDs match pacs_studies; main table remains source of truth.';

COMMENT ON FUNCTION refresh_pacs_week_cache() IS
    'Truncates and rebuilds PACS weekly list caches from pacs_worklists and pacs_studies, then analyzes both cache tables.';

COMMENT ON FUNCTION cleanup_pacs_week_cache() IS
    'Deletes expired or inactive rows from PACS weekly list caches and analyzes both cache tables.';

SELECT refresh_pacs_week_cache();
