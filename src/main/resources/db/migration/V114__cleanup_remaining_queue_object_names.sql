-- Finish Queue -> Worklist naming cleanup for database object names.
-- V113 renamed the runtime tables, columns, endpoint permissions, and module data.
-- This migration cleans remaining constraint/index names only.

CREATE OR REPLACE FUNCTION pg_temp.rename_constraint_if_exists(
    p_table_name text,
    p_old_name text,
    p_new_name text
) RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    v_table regclass;
BEGIN
    v_table := to_regclass(p_table_name);
    IF v_table IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = v_table
          AND conname = p_old_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = v_table
          AND conname = p_new_name
    ) THEN
        EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I', v_table, p_old_name, p_new_name);
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION pg_temp.rename_index_if_exists(
    p_old_name text,
    p_new_name text
) RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF to_regclass('public.' || p_old_name) IS NOT NULL
       AND to_regclass('public.' || p_new_name) IS NULL THEN
        EXECUTE format('ALTER INDEX %I RENAME TO %I', p_old_name, p_new_name);
    END IF;
END;
$$;

SELECT pg_temp.rename_constraint_if_exists('public.pacs_results', 'pacs_results_queue_id_fkey', 'pacs_results_worklist_id_fkey');

SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'fk_queue_hospital_dicom_server', 'fk_worklists_hospital_dicom_server');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'fk_queue_patient_hospital', 'fk_worklists_patient_hospital');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_created_at_not_null', 'pacs_worklists_created_at_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_created_not_null', 'pacs_worklists_created_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_dicom_server_id_fkey', 'pacs_worklists_dicom_server_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_hospital_id_fkey', 'pacs_worklists_hospital_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_hospital_id_not_null', 'pacs_worklists_hospital_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_id_not_null', 'pacs_worklists_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_modulight_id_fkey', 'pacs_worklists_modality_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_patient_id_fkey', 'pacs_worklists_patient_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_patient_id_not_null', 'pacs_worklists_patient_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_pkey', 'pacs_worklists_pkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklists', 'pacs_patient_queue_status_not_null', 'pacs_worklists_status_not_null');

SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'fk_queue_histories_patient_hospital', 'fk_worklist_histories_patient_hospital');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'fk_queue_histories_queue_hospital', 'fk_worklist_histories_worklist_hospital');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_action_not_null', 'pacs_worklist_histories_action_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_created_not_null', 'pacs_worklist_histories_created_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_from_status_not_null', 'pacs_worklist_histories_from_status_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_hospital_id_fkey', 'pacs_worklist_histories_hospital_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_hospital_id_not_null', 'pacs_worklist_histories_hospital_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_id_not_null', 'pacs_worklist_histories_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_patient_id_fkey', 'pacs_worklist_histories_patient_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_patient_id_not_null', 'pacs_worklist_histories_patient_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_pkey', 'pacs_worklist_histories_pkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_queue_id_fkey', 'pacs_worklist_histories_worklist_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_queue_id_not_null', 'pacs_worklist_histories_worklist_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_histories', 'pacs_patient_queue_histories_to_status_not_null', 'pacs_worklist_histories_to_status_not_null');

SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'chk_pacs_queue_results_is_active', 'chk_pacs_worklist_results_is_active');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'fk_queue_results_queue_hospital', 'fk_worklist_results_worklist_hospital');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_created_at_not_null', 'pacs_worklist_results_created_at_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_created_by_fkey', 'pacs_worklist_results_created_by_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_created_not_null', 'pacs_worklist_results_created_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_description_not_null', 'pacs_worklist_results_description_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_hospital_id_fkey', 'pacs_worklist_results_hospital_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_hospital_id_not_null', 'pacs_worklist_results_hospital_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_id_not_null', 'pacs_worklist_results_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_is_active_not_null', 'pacs_worklist_results_is_active_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_modified_at_not_null', 'pacs_worklist_results_modified_at_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_modified_by_fkey', 'pacs_worklist_results_modified_by_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_modified_not_null', 'pacs_worklist_results_modified_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_pkey', 'pacs_worklist_results_pkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_queue_id_fkey', 'pacs_worklist_results_worklist_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_results', 'pacs_queue_results_queue_id_not_null', 'pacs_worklist_results_worklist_id_not_null');

SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_study_links', 'pacs_queue_study_links_created_by_fkey', 'pacs_worklist_study_links_created_by_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_study_links', 'pacs_queue_study_links_hospital_id_fkey', 'pacs_worklist_study_links_hospital_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_study_links', 'pacs_queue_study_links_hospital_id_not_null', 'pacs_worklist_study_links_hospital_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_study_links', 'pacs_queue_study_links_id_not_null', 'pacs_worklist_study_links_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_study_links', 'pacs_queue_study_links_is_primary_not_null', 'pacs_worklist_study_links_is_primary_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_study_links', 'pacs_queue_study_links_linked_at_not_null', 'pacs_worklist_study_links_linked_at_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_study_links', 'pacs_queue_study_links_pkey', 'pacs_worklist_study_links_pkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_study_links', 'pacs_queue_study_links_queue_id_not_null', 'pacs_worklist_study_links_worklist_id_not_null');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_study_links', 'pacs_queue_study_links_study_id_fkey', 'pacs_worklist_study_links_study_id_fkey');
SELECT pg_temp.rename_constraint_if_exists('public.pacs_worklist_study_links', 'pacs_queue_study_links_study_id_not_null', 'pacs_worklist_study_links_study_id_not_null');

SELECT pg_temp.rename_index_if_exists('idx_queue_histories_hospital_queue_id_desc', 'idx_worklist_histories_hospital_worklist_id_desc');
SELECT pg_temp.rename_index_if_exists('pacs_patient_queue_histories_pkey', 'pacs_worklist_histories_pkey');
SELECT pg_temp.rename_index_if_exists('idx_queue_results_description_trgm', 'idx_worklist_results_description_trgm');
SELECT pg_temp.rename_index_if_exists('idx_queue_results_hospital_active_id_desc', 'idx_worklist_results_hospital_active_id_desc');
SELECT pg_temp.rename_index_if_exists('idx_queue_results_hospital_queue_active', 'idx_worklist_results_hospital_worklist_active');
SELECT pg_temp.rename_index_if_exists('idx_queue_results_hospital_queue_id_desc', 'idx_worklist_results_hospital_worklist_id_desc');
SELECT pg_temp.rename_index_if_exists('pacs_queue_results_pkey', 'pacs_worklist_results_pkey');
SELECT pg_temp.rename_index_if_exists('pacs_queue_study_links_pkey', 'pacs_worklist_study_links_pkey');
SELECT pg_temp.rename_index_if_exists('idx_pacs_patient_queue_hospital_dicom_server', 'idx_pacs_worklists_hospital_dicom_server');
SELECT pg_temp.rename_index_if_exists('idx_pacs_patient_queue_image_received_at', 'idx_pacs_worklists_image_received_at');
SELECT pg_temp.rename_index_if_exists('idx_pacs_patient_queue_dicom_server_study_id', 'idx_pacs_worklists_dicom_server_study_id');
SELECT pg_temp.rename_index_if_exists('idx_pacs_patient_queue_status_accession', 'idx_pacs_worklists_status_accession');
SELECT pg_temp.rename_index_if_exists('idx_pacs_queue_hospital_status', 'idx_pacs_worklists_hospital_status');
SELECT pg_temp.rename_index_if_exists('idx_queue_hospital_dicom_server_status_id_desc', 'idx_worklists_hospital_dicom_server_status_id_desc');
SELECT pg_temp.rename_index_if_exists('idx_queue_hospital_modality_status_id_desc', 'idx_worklists_hospital_modality_status_id_desc');
SELECT pg_temp.rename_index_if_exists('pacs_patient_queue_pkey', 'pacs_worklists_pkey');
SELECT pg_temp.rename_index_if_exists('ux_pacs_patient_queue_hospital_accession', 'ux_pacs_worklists_hospital_accession');
SELECT pg_temp.rename_index_if_exists('ux_queue_id_hospital', 'ux_worklists_id_hospital');
