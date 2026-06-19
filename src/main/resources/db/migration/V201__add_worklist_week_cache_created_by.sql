-- V201: Keep the weekly Worklist cache aligned with the shared list
-- projection, which reads q.created_by.
--
-- MIGRATION-SAFETY: allowed-data-loss

ALTER TABLE public.pacs_worklists_week_cache
    ADD COLUMN IF NOT EXISTS created_by BIGINT;

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
        error_message,
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
        error_message,
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

    RETURN FORMAT('PACS weekly cache refreshed: worklists=%s, studies=%s', worklist_rows, study_rows);
END
$fn$;

SELECT refresh_pacs_week_cache();
