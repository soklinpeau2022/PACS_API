-- V204: Make weekly-cache triggers constant-cost for source-table writes.
--
-- Historical bulk inserts previously called the sync functions for every row.
-- That performed a cache DELETE and a source-table SELECT even when the row was
-- older than seven days and could never enter the cache. The trigger now uses
-- NEW directly, skips old/inactive inserts without SQL, and only deletes cache
-- rows when an update makes an existing recent row ineligible.

CREATE OR REPLACE FUNCTION pacs_worklist_week_cache_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM public.pacs_worklists_week_cache
        WHERE id = OLD.id;
        RETURN OLD;
    END IF;

    IF COALESCE(NEW.created_at, NEW.created) >= NOW() - INTERVAL '7 days' THEN
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
        VALUES (
            NEW.id,
            NEW.public_id,
            NEW.hospital_id,
            NEW.patient_id,
            NEW.modality_id,
            NEW.dicom_route_id,
            NEW.study_id,
            NEW.visit_code,
            NEW.status,
            NEW.scheduled_date,
            NEW.scheduled_time,
            NEW.study_description,
            NEW.dicom_server_worklist_id,
            NEW.dicom_server_worklist_path,
            NEW.sent_at,
            NEW.received_at,
            NEW.image_received_at,
            NEW.cancelled_at,
            NEW.started_at,
            NEW.created_at,
            NEW.modified_at,
            NEW.created,
            NEW.created_by,
            NOW()
        )
        ON CONFLICT (id)
        DO UPDATE SET
            public_id = EXCLUDED.public_id,
            hospital_id = EXCLUDED.hospital_id,
            patient_id = EXCLUDED.patient_id,
            modality_id = EXCLUDED.modality_id,
            dicom_route_id = EXCLUDED.dicom_route_id,
            study_id = EXCLUDED.study_id,
            visit_code = EXCLUDED.visit_code,
            status = EXCLUDED.status,
            scheduled_date = EXCLUDED.scheduled_date,
            scheduled_time = EXCLUDED.scheduled_time,
            study_description = EXCLUDED.study_description,
            dicom_server_worklist_id = EXCLUDED.dicom_server_worklist_id,
            dicom_server_worklist_path = EXCLUDED.dicom_server_worklist_path,
            sent_at = EXCLUDED.sent_at,
            received_at = EXCLUDED.received_at,
            image_received_at = EXCLUDED.image_received_at,
            cancelled_at = EXCLUDED.cancelled_at,
            started_at = EXCLUDED.started_at,
            created_at = EXCLUDED.created_at,
            modified_at = EXCLUDED.modified_at,
            created = EXCLUDED.created,
            created_by = EXCLUDED.created_by,
            cached_at = EXCLUDED.cached_at;
    ELSIF TG_OP = 'UPDATE' THEN
        DELETE FROM public.pacs_worklists_week_cache
        WHERE id = NEW.id;
    END IF;

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
        DELETE FROM public.pacs_studies_week_cache
        WHERE id = OLD.id;
        RETURN OLD;
    END IF;

    IF NEW.is_active = 1
       AND COALESCE(
           NEW.image_received_at,
           NEW.received_at,
           NEW.created_at,
           NEW.created
       ) >= NOW() - INTERVAL '7 days' THEN
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
        )
        ON CONFLICT (id)
        DO UPDATE SET
            public_id = EXCLUDED.public_id,
            hospital_id = EXCLUDED.hospital_id,
            patient_id = EXCLUDED.patient_id,
            modality_id = EXCLUDED.modality_id,
            study_instance_uid = EXCLUDED.study_instance_uid,
            accession_number = EXCLUDED.accession_number,
            reference_visit_code = EXCLUDED.reference_visit_code,
            modality = EXCLUDED.modality,
            study_date = EXCLUDED.study_date,
            received_at = EXCLUDED.received_at,
            image_received_at = EXCLUDED.image_received_at,
            study_description = EXCLUDED.study_description,
            status = EXCLUDED.status,
            is_active = EXCLUDED.is_active,
            dicom_server_id = EXCLUDED.dicom_server_id,
            dicom_server_study_id = EXCLUDED.dicom_server_study_id,
            dicom_server_patient_id = EXCLUDED.dicom_server_patient_id,
            dicom_server_series_id = EXCLUDED.dicom_server_series_id,
            instance_count = EXCLUDED.instance_count,
            institution_name = EXCLUDED.institution_name,
            created = EXCLUDED.created,
            modified = EXCLUDED.modified,
            created_at = EXCLUDED.created_at,
            cached_at = EXCLUDED.cached_at;
    ELSIF TG_OP = 'UPDATE' THEN
        DELETE FROM public.pacs_studies_week_cache
        WHERE id = NEW.id;
    END IF;

    RETURN NEW;
END
$fn$;

COMMENT ON FUNCTION pacs_worklist_week_cache_trigger() IS
    'Synchronizes recent Worklists directly from NEW; historical inserts avoid cache SQL.';
COMMENT ON FUNCTION pacs_study_week_cache_trigger() IS
    'Synchronizes recent active Studies directly from NEW; historical/inactive inserts avoid cache SQL.';
