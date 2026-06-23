-- V207: Make Study weekly-cache synchronization idempotent for DICOM re-uploads.
--
-- A failed or rolled-back upload can leave a stale pacs_studies_week_cache row
-- for a StudyInstanceUID even when the source pacs_studies row is gone. The
-- cache table is uniquely constrained by (hospital_id, study_instance_uid), so
-- a later valid re-upload of the same DICOM study must purge that stale cache
-- row before inserting the fresh cache row.

DELETE FROM public.pacs_studies_week_cache cache_row
WHERE NOT EXISTS (
    SELECT 1
    FROM public.pacs_studies source_row
    WHERE source_row.id = cache_row.id
      AND source_row.public_id = cache_row.public_id
      AND source_row.hospital_id = cache_row.hospital_id
      AND source_row.study_instance_uid = cache_row.study_instance_uid
);

CREATE OR REPLACE FUNCTION sync_pacs_study_week_cache(p_study_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
BEGIN
    DELETE FROM public.pacs_studies_week_cache cache_row
    USING public.pacs_studies source_row
    WHERE source_row.id = p_study_id
      AND (
          cache_row.id = source_row.id
          OR cache_row.public_id = source_row.public_id
          OR (
              cache_row.hospital_id = source_row.hospital_id
              AND cache_row.study_instance_uid = source_row.study_instance_uid
          )
      );

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

CREATE OR REPLACE FUNCTION pacs_study_week_cache_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM public.pacs_studies_week_cache
        WHERE id = OLD.id
           OR public_id = OLD.public_id
           OR (
               hospital_id = OLD.hospital_id
               AND study_instance_uid = OLD.study_instance_uid
           );
        RETURN OLD;
    END IF;

    IF NEW.is_active = 1
       AND COALESCE(
           NEW.image_received_at,
           NEW.received_at,
           NEW.created_at,
           NEW.created
       ) >= NOW() - INTERVAL '7 days' THEN
        DELETE FROM public.pacs_studies_week_cache
        WHERE id = NEW.id
           OR public_id = NEW.public_id
           OR (
               hospital_id = NEW.hospital_id
               AND study_instance_uid = NEW.study_instance_uid
           );

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
        VALUES (
            NEW.id,
            NEW.public_id,
            NEW.hospital_id,
            NEW.patient_id,
            NEW.modality_id,
            NEW.study_instance_uid,
            NEW.accession_number,
            NEW.reference_visit_code,
            NEW.modality,
            NEW.study_date,
            NEW.received_at,
            NEW.image_received_at,
            NEW.study_description,
            NEW.status,
            NEW.is_active,
            NEW.dicom_server_id,
            NEW.dicom_server_study_id,
            NEW.dicom_server_patient_id,
            NEW.dicom_server_series_id,
            NEW.instance_count,
            NEW.institution_name,
            NEW.created,
            NEW.modified,
            NEW.created_at,
            NOW()
        );
    ELSIF TG_OP = 'UPDATE' THEN
        DELETE FROM public.pacs_studies_week_cache
        WHERE id = NEW.id
           OR public_id = NEW.public_id
           OR (
               hospital_id = NEW.hospital_id
               AND study_instance_uid = NEW.study_instance_uid
           );
    END IF;

    RETURN NEW;
END
$fn$;

COMMENT ON FUNCTION sync_pacs_study_week_cache(BIGINT) IS
    'Synchronizes a recent active Study into the week cache, first removing stale cache rows for the same id/public_id/StudyInstanceUID.';
COMMENT ON FUNCTION pacs_study_week_cache_trigger() IS
    'Synchronizes recent active Studies directly from NEW and removes stale same-study cache rows before insert.';
