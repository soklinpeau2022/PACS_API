--
-- PostgreSQL database dump
--

\restrict XtAWpzamsUKeUxgOxqpyKuwnroKaINC4fPr3KYFh2ghP5RIrr0IxpoGRSRfzDq9

-- Dumped from database version 18.4 (Debian 18.4-1.pgdg13+1)
-- Dumped by pg_dump version 18.4 (Debian 18.4-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS public.user_logs DROP CONSTRAINT IF EXISTS user_logs_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.user_hospitals DROP CONSTRAINT IF EXISTS user_hospitals_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.user_hospitals DROP CONSTRAINT IF EXISTS user_hospitals_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.user_groups DROP CONSTRAINT IF EXISTS user_groups_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.user_groups DROP CONSTRAINT IF EXISTS user_groups_role_id_fkey;
ALTER TABLE IF EXISTS ONLY public.study_retention_policies DROP CONSTRAINT IF EXISTS study_retention_policies_modified_by_fkey;
ALTER TABLE IF EXISTS ONLY public.study_retention_policies DROP CONSTRAINT IF EXISTS study_retention_policies_modality_id_fkey;
ALTER TABLE IF EXISTS ONLY public.study_retention_policies DROP CONSTRAINT IF EXISTS study_retention_policies_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.study_retention_policies DROP CONSTRAINT IF EXISTS study_retention_policies_dicom_server_id_fkey;
ALTER TABLE IF EXISTS ONLY public.study_retention_policies DROP CONSTRAINT IF EXISTS study_retention_policies_created_by_fkey;
ALTER TABLE IF EXISTS public.study_retention_delete_requests DROP CONSTRAINT IF EXISTS study_retention_delete_requests_requested_by_fkey;
ALTER TABLE IF EXISTS public.study_retention_delete_requests DROP CONSTRAINT IF EXISTS study_retention_delete_requests_rejected_by_fkey;
ALTER TABLE IF EXISTS public.study_retention_delete_requests DROP CONSTRAINT IF EXISTS study_retention_delete_requests_policy_id_fkey;
ALTER TABLE IF EXISTS public.study_retention_delete_requests DROP CONSTRAINT IF EXISTS study_retention_delete_requests_modality_id_fkey;
ALTER TABLE IF EXISTS public.study_retention_delete_requests DROP CONSTRAINT IF EXISTS study_retention_delete_requests_hospital_id_fkey;
ALTER TABLE IF EXISTS public.study_retention_delete_requests DROP CONSTRAINT IF EXISTS study_retention_delete_requests_approved_by_fkey;
ALTER TABLE IF EXISTS ONLY public.role_module_details DROP CONSTRAINT IF EXISTS role_module_details_role_id_fkey;
ALTER TABLE IF EXISTS ONLY public.role_module_details DROP CONSTRAINT IF EXISTS role_module_details_module_detail_id_fkey;
ALTER TABLE IF EXISTS ONLY public.refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.patients DROP CONSTRAINT IF EXISTS patients_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_worklists DROP CONSTRAINT IF EXISTS pacs_worklists_modality_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_worklists DROP CONSTRAINT IF EXISTS pacs_worklists_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_worklist_study_links DROP CONSTRAINT IF EXISTS pacs_worklist_study_links_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_worklist_study_links DROP CONSTRAINT IF EXISTS pacs_worklist_study_links_created_by_fkey;
ALTER TABLE IF EXISTS public.pacs_worklist_histories DROP CONSTRAINT IF EXISTS pacs_worklist_histories_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_visit_sequences DROP CONSTRAINT IF EXISTS pacs_visit_sequences_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_studies DROP CONSTRAINT IF EXISTS pacs_studies_uploaded_by_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_studies DROP CONSTRAINT IF EXISTS pacs_studies_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_results DROP CONSTRAINT IF EXISTS pacs_results_modality_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_results DROP CONSTRAINT IF EXISTS pacs_results_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_results DROP CONSTRAINT IF EXISTS pacs_results_created_by_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_result_templates DROP CONSTRAINT IF EXISTS pacs_result_templates_modality_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_result_templates DROP CONSTRAINT IF EXISTS pacs_result_templates_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_patient_sequences DROP CONSTRAINT IF EXISTS pacs_patient_sequences_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.modules DROP CONSTRAINT IF EXISTS modules_module_type_id_fkey;
ALTER TABLE IF EXISTS ONLY public.module_details DROP CONSTRAINT IF EXISTS module_details_module_id_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_modalities DROP CONSTRAINT IF EXISTS hospital_modulights_modulight_id_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_modalities DROP CONSTRAINT IF EXISTS hospital_modulights_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_modality_server_routes DROP CONSTRAINT IF EXISTS hospital_modulight_server_routes_modified_by_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_modality_server_routes DROP CONSTRAINT IF EXISTS hospital_modulight_server_routes_created_by_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_servers DROP CONSTRAINT IF EXISTS hospital_dicom_servers_modified_by_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_servers DROP CONSTRAINT IF EXISTS hospital_dicom_servers_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_servers DROP CONSTRAINT IF EXISTS hospital_dicom_servers_created_by_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_routing_configs DROP CONSTRAINT IF EXISTS hospital_dicom_routing_configs_modified_by_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_routing_configs DROP CONSTRAINT IF EXISTS hospital_dicom_routing_configs_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_routing_configs DROP CONSTRAINT IF EXISTS hospital_dicom_routing_configs_created_by_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_machines DROP CONSTRAINT IF EXISTS hospital_dicom_machines_modified_by_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_machines DROP CONSTRAINT IF EXISTS hospital_dicom_machines_hospital_id_fkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_machines DROP CONSTRAINT IF EXISTS hospital_dicom_machines_created_by_fkey;
ALTER TABLE IF EXISTS ONLY public.pacs_worklists DROP CONSTRAINT IF EXISTS fk_worklists_study_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_worklists DROP CONSTRAINT IF EXISTS fk_worklists_patient_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_worklist_study_links DROP CONSTRAINT IF EXISTS fk_worklist_study_links_worklist_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_worklist_study_links DROP CONSTRAINT IF EXISTS fk_worklist_study_links_study_hospital;
ALTER TABLE IF EXISTS public.pacs_worklist_histories DROP CONSTRAINT IF EXISTS fk_worklist_histories_worklist_hospital;
ALTER TABLE IF EXISTS public.pacs_worklist_histories DROP CONSTRAINT IF EXISTS fk_worklist_histories_patient_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_viewer_states DROP CONSTRAINT IF EXISTS fk_viewer_states_worklist_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_viewer_states DROP CONSTRAINT IF EXISTS fk_viewer_states_study_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_viewer_states DROP CONSTRAINT IF EXISTS fk_viewer_states_patient_hospital;
ALTER TABLE IF EXISTS ONLY public.revoked_tokens DROP CONSTRAINT IF EXISTS fk_revoked_tokens_user;
ALTER TABLE IF EXISTS ONLY public.revoked_tokens DROP CONSTRAINT IF EXISTS fk_revoked_tokens_hospital;
ALTER TABLE IF EXISTS public.study_retention_delete_requests DROP CONSTRAINT IF EXISTS fk_retention_requests_study_hospital;
ALTER TABLE IF EXISTS public.study_retention_delete_requests DROP CONSTRAINT IF EXISTS fk_retention_requests_server_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_results DROP CONSTRAINT IF EXISTS fk_results_worklist_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_results DROP CONSTRAINT IF EXISTS fk_results_study_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_results DROP CONSTRAINT IF EXISTS fk_results_patient_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_result_versions DROP CONSTRAINT IF EXISTS fk_result_versions_result_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_result_images DROP CONSTRAINT IF EXISTS fk_result_images_worklist_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_result_images DROP CONSTRAINT IF EXISTS fk_result_images_study_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_result_images DROP CONSTRAINT IF EXISTS fk_result_images_result_hospital;
ALTER TABLE IF EXISTS ONLY public.refresh_tokens DROP CONSTRAINT IF EXISTS fk_refresh_tokens_rotated_from;
ALTER TABLE IF EXISTS public.pacs_realtime_notification_events DROP CONSTRAINT IF EXISTS fk_realtime_events_worklist_hospital;
ALTER TABLE IF EXISTS public.pacs_realtime_notification_events DROP CONSTRAINT IF EXISTS fk_realtime_events_study_hospital;
ALTER TABLE IF EXISTS public.pacs_realtime_notification_events DROP CONSTRAINT IF EXISTS fk_realtime_events_hospital_restrict;
ALTER TABLE IF EXISTS ONLY public.pacs_worklists DROP CONSTRAINT IF EXISTS fk_pacs_worklists_route_hospital_modality;
ALTER TABLE IF EXISTS ONLY public.pacs_viewer_states DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_modified_by;
ALTER TABLE IF EXISTS ONLY public.pacs_viewer_states DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_modality;
ALTER TABLE IF EXISTS ONLY public.pacs_viewer_states DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_viewer_states DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_deleted_by;
ALTER TABLE IF EXISTS ONLY public.pacs_viewer_states DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_created_by;
ALTER TABLE IF EXISTS ONLY public.pacs_studies DROP CONSTRAINT IF EXISTS fk_pacs_studies_patient_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_studies DROP CONSTRAINT IF EXISTS fk_pacs_studies_hospital_modality;
ALTER TABLE IF EXISTS ONLY public.pacs_studies DROP CONSTRAINT IF EXISTS fk_pacs_studies_dicom_server_hospital;
ALTER TABLE IF EXISTS ONLY public.pacs_results DROP CONSTRAINT IF EXISTS fk_pacs_results_template;
ALTER TABLE IF EXISTS ONLY public.pacs_result_templates DROP CONSTRAINT IF EXISTS fk_pacs_result_templates_modified_by;
ALTER TABLE IF EXISTS ONLY public.pacs_result_templates DROP CONSTRAINT IF EXISTS fk_pacs_result_templates_created_by;
ALTER TABLE IF EXISTS ONLY public.pacs_daily_stats DROP CONSTRAINT IF EXISTS fk_pacs_daily_stats_hospital;
ALTER TABLE IF EXISTS ONLY public.oauth2_clients DROP CONSTRAINT IF EXISTS fk_oauth2_clients_dicom_server;
ALTER TABLE IF EXISTS ONLY public.hospital_modality_server_routes DROP CONSTRAINT IF EXISTS fk_hmsr_routing_config_hospital;
ALTER TABLE IF EXISTS ONLY public.hospital_modality_server_routes DROP CONSTRAINT IF EXISTS fk_hmsr_routing_config;
ALTER TABLE IF EXISTS ONLY public.hospital_modality_server_routes DROP CONSTRAINT IF EXISTS fk_hmsr_machine_hospital_modality;
ALTER TABLE IF EXISTS ONLY public.hospital_modality_server_routes DROP CONSTRAINT IF EXISTS fk_hmsr_machine;
ALTER TABLE IF EXISTS ONLY public.hospital_modality_server_routes DROP CONSTRAINT IF EXISTS fk_hmsr_hospital_modality;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_routing_configs DROP CONSTRAINT IF EXISTS fk_hdrc_dicom_server_hospital;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_machines DROP CONSTRAINT IF EXISTS fk_hdm_hospital_modality;
ALTER TABLE IF EXISTS public.dicom_server_callback_log DROP CONSTRAINT IF EXISTS fk_callback_log_server_hospital;
ALTER TABLE IF EXISTS public.dicom_server_callback_log DROP CONSTRAINT IF EXISTS fk_callback_log_hospital;
DROP TRIGGER IF EXISTS trg_patients_sync_audit_timestamps ON public.patients;
DROP TRIGGER IF EXISTS trg_pacs_worklists_week_cache_sync ON public.pacs_worklists;
DROP TRIGGER IF EXISTS trg_pacs_worklists_sync_primary_study ON public.pacs_worklists;
DROP TRIGGER IF EXISTS trg_pacs_worklist_study_links_sync_worklist ON public.pacs_worklist_study_links;
DROP TRIGGER IF EXISTS trg_pacs_studies_week_cache_sync ON public.pacs_studies;
DROP TRIGGER IF EXISTS trg_pacs_studies_sync_audit_timestamps ON public.pacs_studies;
DROP TRIGGER IF EXISTS trg_pacs_results_capture_version ON public.pacs_results;
DROP TRIGGER IF EXISTS trg_pacs_result_images_set_scope ON public.pacs_result_images;
DROP INDEX IF EXISTS public.ux_user_hospitals_one_default_active;
DROP INDEX IF EXISTS public.ux_unmatched_callback_original;
DROP INDEX IF EXISTS public.ux_unmatched_callback_dedupe;
DROP INDEX IF EXISTS public.ux_study_retention_policies_scope_active;
DROP INDEX IF EXISTS public.ux_study_retention_policies_public_id;
DROP INDEX IF EXISTS public.ux_roles_active_name_global;
DROP INDEX IF EXISTS public.ux_patients_id_hospital;
DROP INDEX IF EXISTS public.ux_patients_hospital_patient_uid_lower;
DROP INDEX IF EXISTS public.ux_partition_maintenance_parent;
DROP INDEX IF EXISTS public.ux_pacs_worklists_week_cache_public_id;
DROP INDEX IF EXISTS public.ux_pacs_worklists_id_hospital;
DROP INDEX IF EXISTS public.ux_pacs_worklists_hospital_visit_code_lower;
DROP INDEX IF EXISTS public.ux_pacs_worklist_study_links_worklist_study;
DROP INDEX IF EXISTS public.ux_pacs_worklist_study_links_primary_worklist;
DROP INDEX IF EXISTS public.ux_pacs_viewer_states_public_id;
DROP INDEX IF EXISTS public.ux_pacs_viewer_states_current_worklist;
DROP INDEX IF EXISTS public.ux_pacs_viewer_states_current_study_uid;
DROP INDEX IF EXISTS public.ux_pacs_viewer_states_current_study;
DROP INDEX IF EXISTS public.ux_pacs_studies_week_cache_study_uid;
DROP INDEX IF EXISTS public.ux_pacs_studies_week_cache_public_id;
DROP INDEX IF EXISTS public.ux_pacs_studies_id_hospital;
DROP INDEX IF EXISTS public.ux_pacs_studies_hospital_study_instance_uid;
DROP INDEX IF EXISTS public.ux_pacs_results_public_id;
DROP INDEX IF EXISTS public.ux_pacs_results_id_hospital;
DROP INDEX IF EXISTS public.ux_pacs_results_hospital_worklist_active;
DROP INDEX IF EXISTS public.ux_pacs_results_hospital_study_active;
DROP INDEX IF EXISTS public.ux_pacs_result_versions_result_version;
DROP INDEX IF EXISTS public.ux_pacs_result_versions_public_id;
DROP INDEX IF EXISTS public.ux_pacs_result_templates_name_active;
DROP INDEX IF EXISTS public.ux_pacs_result_images_public_id;
DROP INDEX IF EXISTS public.ux_modalities_name_active;
DROP INDEX IF EXISTS public.ux_modalities_abbr_active;
DROP INDEX IF EXISTS public.ux_hospitals_active_visit_code_token;
DROP INDEX IF EXISTS public.ux_hospitals_abbr_active;
DROP INDEX IF EXISTS public.ux_hospital_dicom_servers_hospital_name_active;
DROP INDEX IF EXISTS public.ux_hospital_dicom_servers_hospital_endpoint_active;
DROP INDEX IF EXISTS public.ux_hmsr_id_hospital_modality;
DROP INDEX IF EXISTS public.ux_hmsr_config_machine_active;
DROP INDEX IF EXISTS public.ux_hmsr_active_machine;
DROP INDEX IF EXISTS public.ux_hds_id_hospital;
DROP INDEX IF EXISTS public.ux_hds_hospital_id_id;
DROP INDEX IF EXISTS public.ux_hdrc_id_hospital;
DROP INDEX IF EXISTS public.ux_hdrc_hospital_server_active;
DROP INDEX IF EXISTS public.ux_hdm_active_machine_endpoint;
DROP INDEX IF EXISTS public.uq_users_public_id;
DROP INDEX IF EXISTS public.uq_user_logs_public_id;
DROP INDEX IF EXISTS public.uq_unmatched_callback_public_id;
DROP INDEX IF EXISTS public.uq_roles_public_id;
DROP INDEX IF EXISTS public.uq_patients_public_id;
DROP INDEX IF EXISTS public.uq_pacs_worklists_public_id;
DROP INDEX IF EXISTS public.uq_pacs_studies_public_id;
DROP INDEX IF EXISTS public.uq_pacs_result_templates_public_id;
DROP INDEX IF EXISTS public.uq_modules_public_id;
DROP INDEX IF EXISTS public.uq_module_types_public_id;
DROP INDEX IF EXISTS public.uq_module_details_public_id;
DROP INDEX IF EXISTS public.uq_modalities_public_id;
DROP INDEX IF EXISTS public.uq_hospitals_public_id;
DROP INDEX IF EXISTS public.uq_hospital_modality_server_routes_public_id;
DROP INDEX IF EXISTS public.uq_hospital_dicom_servers_public_id;
DROP INDEX IF EXISTS public.uq_hospital_dicom_routing_configs_public_id;
DROP INDEX IF EXISTS public.uq_hospital_dicom_machines_public_id;
DROP INDEX IF EXISTS public.uq_system_activities_public_id;
DROP INDEX IF EXISTS public.ux_study_retention_delete_requests_public_id;
DROP INDEX IF EXISTS public.idx_worklist_histories_purge_after;
DROP INDEX IF EXISTS public.idx_worklist_histories_patient_created;
DROP INDEX IF EXISTS public.idx_worklist_histories_hospital_worklist_created;
DROP INDEX IF EXISTS public.idx_worklist_histories_hospital_created;
DROP INDEX IF EXISTS public.idx_users_username_trgm;
DROP INDEX IF EXISTS public.idx_users_telephone_trgm;
DROP INDEX IF EXISTS public.idx_users_modified_by;
DROP INDEX IF EXISTS public.idx_users_last_name_trgm;
DROP INDEX IF EXISTS public.idx_users_first_name_trgm;
DROP INDEX IF EXISTS public.idx_users_email_trgm;
DROP INDEX IF EXISTS public.idx_users_created_by;
DROP INDEX IF EXISTS public.idx_users_active_id_desc;
DROP INDEX IF EXISTS public.idx_users_active_created_at_desc;
DROP INDEX IF EXISTS public.idx_user_logs_user_created;
DROP INDEX IF EXISTS public.idx_user_logs_created_id;
DROP INDEX IF EXISTS public.idx_user_hospitals_user_id;
DROP INDEX IF EXISTS public.idx_user_hospitals_user_active_default;
DROP INDEX IF EXISTS public.idx_user_hospitals_hospital_id;
DROP INDEX IF EXISTS public.idx_user_hospitals_active_hospital_user;
DROP INDEX IF EXISTS public.idx_user_groups_user_role_active;
DROP INDEX IF EXISTS public.idx_user_groups_role_active;
DROP INDEX IF EXISTS public.idx_unmatched_callback_received;
DROP INDEX IF EXISTS public.idx_unmatched_callback_accession;
DROP INDEX IF EXISTS public.idx_system_activities_status_created;
DROP INDEX IF EXISTS public.idx_system_activities_created_id;
DROP INDEX IF EXISTS public.idx_system_activities_created_by_created;
DROP INDEX IF EXISTS public.idx_study_retention_policies_scope;
DROP INDEX IF EXISTS public.idx_study_retention_policies_auto_delete;
DROP INDEX IF EXISTS public.idx_study_retention_delete_requests_study_latest;
DROP INDEX IF EXISTS public.idx_study_retention_delete_requests_status;
DROP INDEX IF EXISTS public.idx_study_retention_delete_requests_purge_after;
DROP INDEX IF EXISTS public.idx_roles_active_created_at_desc;
DROP INDEX IF EXISTS public.idx_role_module_details_module_role;
DROP INDEX IF EXISTS public.idx_revoked_tokens_jti_expires;
DROP INDEX IF EXISTS public.idx_revoked_tokens_expires;
DROP INDEX IF EXISTS public.idx_retention_requests_study_hospital;
DROP INDEX IF EXISTS public.idx_retention_requests_server_hospital;
DROP INDEX IF EXISTS public.idx_refresh_tokens_user_id;
DROP INDEX IF EXISTS public.idx_refresh_tokens_user_client_active;
DROP INDEX IF EXISTS public.idx_refresh_tokens_expires;
DROP INDEX IF EXISTS public.idx_patients_last_name_trgm;
DROP INDEX IF EXISTS public.idx_patients_hospital_name;
DROP INDEX IF EXISTS public.idx_patients_hospital_hn;
DROP INDEX IF EXISTS public.idx_patients_hospital_active_id;
DROP INDEX IF EXISTS public.idx_patients_first_name_trgm;
DROP INDEX IF EXISTS public.idx_pacs_worklists_week_cache_visit_code;
DROP INDEX IF EXISTS public.idx_pacs_worklists_week_cache_hospital_status_created;
DROP INDEX IF EXISTS public.idx_pacs_worklists_week_cache_hospital_patient;
DROP INDEX IF EXISTS public.idx_pacs_worklists_week_cache_hospital_modality_status;
DROP INDEX IF EXISTS public.idx_pacs_worklists_week_cache_hospital_created;
DROP INDEX IF EXISTS public.idx_pacs_worklists_hospital_status_scheduled;
DROP INDEX IF EXISTS public.idx_pacs_worklists_hospital_status_created;
DROP INDEX IF EXISTS public.idx_pacs_worklists_hospital_route_status;
DROP INDEX IF EXISTS public.idx_pacs_worklists_hospital_patient;
DROP INDEX IF EXISTS public.idx_pacs_worklists_hospital_notification_created;
DROP INDEX IF EXISTS public.idx_pacs_worklists_hospital_modality_status;
DROP INDEX IF EXISTS public.idx_pacs_worklist_study_links_primary_study_latest;
DROP INDEX IF EXISTS public.idx_pacs_worklist_study_links_hospital_study_linked_desc;
DROP INDEX IF EXISTS public.idx_pacs_viewer_states_patient;
DROP INDEX IF EXISTS public.idx_pacs_viewer_states_accession_scope;
DROP INDEX IF EXISTS public.idx_pacs_studies_week_cache_hospital_study_date;
DROP INDEX IF EXISTS public.idx_pacs_studies_week_cache_hospital_received;
DROP INDEX IF EXISTS public.idx_pacs_studies_week_cache_hospital_patient_received;
DROP INDEX IF EXISTS public.idx_pacs_studies_week_cache_hospital_modality_received;
DROP INDEX IF EXISTS public.idx_pacs_studies_week_cache_hospital_accession;
DROP INDEX IF EXISTS public.idx_pacs_studies_hospital_study_date;
DROP INDEX IF EXISTS public.idx_pacs_studies_hospital_reference_visit;
DROP INDEX IF EXISTS public.idx_pacs_studies_hospital_received;
DROP INDEX IF EXISTS public.idx_pacs_studies_hospital_patient_date;
DROP INDEX IF EXISTS public.idx_pacs_studies_hospital_modality_date;
DROP INDEX IF EXISTS public.idx_pacs_studies_hospital_dicom_server_study;
DROP INDEX IF EXISTS public.idx_pacs_studies_hospital_accession;
DROP INDEX IF EXISTS public.idx_pacs_results_worklist_hospital;
DROP INDEX IF EXISTS public.idx_pacs_results_study_hospital;
DROP INDEX IF EXISTS public.idx_pacs_results_patient_hospital;
DROP INDEX IF EXISTS public.idx_pacs_results_hospital_status_created;
DROP INDEX IF EXISTS public.idx_pacs_results_hospital_patient_created;
DROP INDEX IF EXISTS public.idx_pacs_result_versions_hospital_result_changed;
DROP INDEX IF EXISTS public.idx_pacs_result_templates_scope_active;
DROP INDEX IF EXISTS public.idx_pacs_result_templates_hospital_modality;
DROP INDEX IF EXISTS public.idx_pacs_result_images_hospital_worklist_active;
DROP INDEX IF EXISTS public.idx_pacs_result_images_hospital_study_active;
DROP INDEX IF EXISTS public.idx_pacs_result_images_hospital_result_active;
DROP INDEX IF EXISTS public.idx_pacs_realtime_events_hospital_cursor;
DROP INDEX IF EXISTS public.idx_pacs_realtime_events_hospital_created;
DROP INDEX IF EXISTS public.idx_oauth2_clients_dicom_server_callback;
DROP INDEX IF EXISTS public.idx_modules_active_type;
DROP INDEX IF EXISTS public.idx_module_types_active_display_order;
DROP INDEX IF EXISTS public.idx_module_details_code;
DROP INDEX IF EXISTS public.idx_modalities_modified_by;
DROP INDEX IF EXISTS public.idx_modalities_created_by;
DROP INDEX IF EXISTS public.idx_modalities_active_id_desc;
DROP INDEX IF EXISTS public.idx_hospitals_modified_by;
DROP INDEX IF EXISTS public.idx_hospitals_logo_updated_at;
DROP INDEX IF EXISTS public.idx_hospitals_created_by;
DROP INDEX IF EXISTS public.idx_hospitals_active_created_at_desc;
DROP INDEX IF EXISTS public.idx_hospital_modalities_modality_active;
DROP INDEX IF EXISTS public.idx_hospital_modalities_hospital_active;
DROP INDEX IF EXISTS public.idx_hospital_dicom_servers_result_key_hospital;
DROP INDEX IF EXISTS public.idx_hospital_dicom_servers_hospital_active;
DROP INDEX IF EXISTS public.idx_hmsr_hospital_modality_active_machine;
DROP INDEX IF EXISTS public.idx_hmsr_hospital_modality_active;
DROP INDEX IF EXISTS public.idx_hmsr_hospital_active_id_desc;
DROP INDEX IF EXISTS public.idx_hmsr_config_active_modality_machine;
DROP INDEX IF EXISTS public.idx_hds_name_trgm;
DROP INDEX IF EXISTS public.idx_hds_ip_trgm;
DROP INDEX IF EXISTS public.idx_hds_ae_trgm;
DROP INDEX IF EXISTS public.idx_hdrc_package_built_hospital;
DROP INDEX IF EXISTS public.idx_hdrc_hospital_server_active;
DROP INDEX IF EXISTS public.idx_hdrc_hospital_active;
DROP INDEX IF EXISTS public.idx_hdm_hospital_modality_active;
DROP INDEX IF EXISTS public.idx_endpoint_permissions_required_scope;
DROP INDEX IF EXISTS public.idx_endpoint_permissions_method_pattern_active;
DROP INDEX IF EXISTS public.idx_countries_active;
DROP INDEX IF EXISTS public.flyway_schema_history_s_idx;
DROP INDEX IF EXISTS public.idx_callback_log_hospital_received;
DROP INDEX IF EXISTS public.idx_callback_log_hospital_accession;
DROP INDEX IF EXISTS public.idx_callback_log_hospital_server_received;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_machines DROP CONSTRAINT IF EXISTS ux_hdm_id_hospital_modality;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_username_key;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE IF EXISTS ONLY public.user_logs_default DROP CONSTRAINT IF EXISTS user_logs_default_pkey;
ALTER TABLE IF EXISTS ONLY public.user_logs DROP CONSTRAINT IF EXISTS user_logs_pkey;
ALTER TABLE IF EXISTS ONLY public.user_hospitals DROP CONSTRAINT IF EXISTS user_hospitals_user_id_hospital_id_key;
ALTER TABLE IF EXISTS ONLY public.user_hospitals DROP CONSTRAINT IF EXISTS user_hospitals_pkey;
ALTER TABLE IF EXISTS ONLY public.user_groups DROP CONSTRAINT IF EXISTS user_groups_user_id_role_id_key;
ALTER TABLE IF EXISTS ONLY public.user_groups DROP CONSTRAINT IF EXISTS user_groups_pkey;
ALTER TABLE IF EXISTS ONLY public.system_activities_default DROP CONSTRAINT IF EXISTS system_activities_default_pkey;
ALTER TABLE IF EXISTS ONLY public.system_activities DROP CONSTRAINT IF EXISTS system_activities_pkey;
ALTER TABLE IF EXISTS ONLY public.study_retention_policies DROP CONSTRAINT IF EXISTS study_retention_policies_pkey;
ALTER TABLE IF EXISTS ONLY public.study_retention_delete_requests_default DROP CONSTRAINT IF EXISTS study_retention_delete_requests_default_pkey;
ALTER TABLE IF EXISTS ONLY public.study_retention_delete_requests DROP CONSTRAINT IF EXISTS study_retention_delete_requests_pkey;
ALTER TABLE IF EXISTS ONLY public.roles DROP CONSTRAINT IF EXISTS roles_pkey;
ALTER TABLE IF EXISTS ONLY public.role_module_details DROP CONSTRAINT IF EXISTS role_module_details_role_id_module_detail_id_key;
ALTER TABLE IF EXISTS ONLY public.role_module_details DROP CONSTRAINT IF EXISTS role_module_details_pkey;
ALTER TABLE IF EXISTS ONLY public.revoked_tokens DROP CONSTRAINT IF EXISTS revoked_tokens_pkey;
ALTER TABLE IF EXISTS ONLY public.revoked_tokens DROP CONSTRAINT IF EXISTS revoked_tokens_jti_key;
ALTER TABLE IF EXISTS ONLY public.refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_token_hash_key;
ALTER TABLE IF EXISTS ONLY public.refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_pkey;
ALTER TABLE IF EXISTS ONLY public.patients DROP CONSTRAINT IF EXISTS patients_pkey;
ALTER TABLE IF EXISTS ONLY public.partition_maintenance_configs DROP CONSTRAINT IF EXISTS partition_maintenance_configs_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_worklists_week_cache DROP CONSTRAINT IF EXISTS pacs_worklists_week_cache_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_worklists DROP CONSTRAINT IF EXISTS pacs_worklists_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_worklist_study_links DROP CONSTRAINT IF EXISTS pacs_worklist_study_links_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_worklist_histories_default DROP CONSTRAINT IF EXISTS pacs_worklist_histories_default_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_worklist_histories DROP CONSTRAINT IF EXISTS pacs_worklist_histories_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_visit_sequences DROP CONSTRAINT IF EXISTS pacs_visit_sequences_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_visit_sequences DROP CONSTRAINT IF EXISTS pacs_visit_sequences_hospital_id_sequence_date_key;
ALTER TABLE IF EXISTS ONLY public.pacs_viewer_states DROP CONSTRAINT IF EXISTS pacs_viewer_states_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_system_settings DROP CONSTRAINT IF EXISTS pacs_system_settings_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_studies_week_cache DROP CONSTRAINT IF EXISTS pacs_studies_week_cache_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_studies DROP CONSTRAINT IF EXISTS pacs_studies_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_results DROP CONSTRAINT IF EXISTS pacs_results_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_result_versions DROP CONSTRAINT IF EXISTS pacs_result_versions_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_result_templates DROP CONSTRAINT IF EXISTS pacs_result_templates_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_result_images DROP CONSTRAINT IF EXISTS pacs_result_images_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_realtime_notification_events_default DROP CONSTRAINT IF EXISTS pacs_realtime_notification_events_default_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_realtime_notification_events DROP CONSTRAINT IF EXISTS pacs_realtime_notification_events_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_patient_sequences DROP CONSTRAINT IF EXISTS pacs_patient_sequences_pkey;
ALTER TABLE IF EXISTS ONLY public.pacs_patient_sequences DROP CONSTRAINT IF EXISTS pacs_patient_sequences_hospital_id_sequence_year_key;
ALTER TABLE IF EXISTS ONLY public.pacs_daily_stats DROP CONSTRAINT IF EXISTS pacs_daily_stats_pkey;
ALTER TABLE IF EXISTS ONLY public.oauth2_clients DROP CONSTRAINT IF EXISTS oauth2_clients_pkey;
ALTER TABLE IF EXISTS ONLY public.oauth2_clients DROP CONSTRAINT IF EXISTS oauth2_clients_client_id_key;
ALTER TABLE IF EXISTS ONLY public.modalities DROP CONSTRAINT IF EXISTS modulights_pkey;
ALTER TABLE IF EXISTS ONLY public.modules DROP CONSTRAINT IF EXISTS modules_pkey;
ALTER TABLE IF EXISTS ONLY public.modules DROP CONSTRAINT IF EXISTS modules_code_key;
ALTER TABLE IF EXISTS ONLY public.module_types DROP CONSTRAINT IF EXISTS module_types_pkey;
ALTER TABLE IF EXISTS ONLY public.module_types DROP CONSTRAINT IF EXISTS module_types_code_key;
ALTER TABLE IF EXISTS ONLY public.module_details DROP CONSTRAINT IF EXISTS module_details_pkey;
ALTER TABLE IF EXISTS ONLY public.module_details DROP CONSTRAINT IF EXISTS module_details_code_key;
ALTER TABLE IF EXISTS ONLY public.hospitals DROP CONSTRAINT IF EXISTS hospitals_pkey;
ALTER TABLE IF EXISTS ONLY public.hospitals DROP CONSTRAINT IF EXISTS hospitals_code_key;
ALTER TABLE IF EXISTS ONLY public.hospital_modalities DROP CONSTRAINT IF EXISTS hospital_modulights_pkey;
ALTER TABLE IF EXISTS ONLY public.hospital_modalities DROP CONSTRAINT IF EXISTS hospital_modulights_hospital_id_modulight_id_key;
ALTER TABLE IF EXISTS ONLY public.hospital_modality_server_routes DROP CONSTRAINT IF EXISTS hospital_modulight_server_routes_pkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_servers DROP CONSTRAINT IF EXISTS hospital_dicom_servers_pkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_routing_configs DROP CONSTRAINT IF EXISTS hospital_dicom_routing_configs_pkey;
ALTER TABLE IF EXISTS ONLY public.hospital_dicom_machines DROP CONSTRAINT IF EXISTS hospital_dicom_machines_pkey;
ALTER TABLE IF EXISTS ONLY public.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS ONLY public.endpoint_permissions DROP CONSTRAINT IF EXISTS endpoint_permissions_pkey;
ALTER TABLE IF EXISTS ONLY public.endpoint_permissions DROP CONSTRAINT IF EXISTS endpoint_permissions_http_method_endpoint_pattern_permissio_key;
ALTER TABLE IF EXISTS ONLY public.dicom_server_unmatched_callback_log DROP CONSTRAINT IF EXISTS dicom_server_unmatched_callback_log_pkey;
ALTER TABLE IF EXISTS ONLY public.dicom_server_callback_log_default DROP CONSTRAINT IF EXISTS dicom_server_callback_log_default_pkey;
ALTER TABLE IF EXISTS ONLY public.dicom_server_callback_log DROP CONSTRAINT IF EXISTS dicom_server_callback_log_pkey;
ALTER TABLE IF EXISTS ONLY public.countries DROP CONSTRAINT IF EXISTS countries_pkey;
ALTER TABLE IF EXISTS ONLY public.countries DROP CONSTRAINT IF EXISTS countries_name_key;
ALTER TABLE IF EXISTS public.users ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.user_logs ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.user_hospitals ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.user_groups ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.system_activities ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.study_retention_policies ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.study_retention_delete_requests ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.roles ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.role_module_details ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.revoked_tokens ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.refresh_tokens ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.patients ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.partition_maintenance_configs ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_worklists ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_worklist_study_links ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_worklist_histories ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_visit_sequences ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_viewer_states ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_studies ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_results ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_result_versions ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_result_templates ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_result_images ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_realtime_notification_events ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.pacs_patient_sequences ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.oauth2_clients ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.modules ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.module_types ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.module_details ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.modalities ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.hospitals ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.hospital_modality_server_routes ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.hospital_modalities ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.hospital_dicom_servers ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.hospital_dicom_routing_configs ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.hospital_dicom_machines ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.endpoint_permissions ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.dicom_server_unmatched_callback_log ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.dicom_server_callback_log ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.countries ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS public.users_id_seq;
DROP TABLE IF EXISTS public.users;
DROP TABLE IF EXISTS public.user_logs_default;
DROP SEQUENCE IF EXISTS public.user_logs_id_seq;
DROP TABLE IF EXISTS public.user_logs;
DROP SEQUENCE IF EXISTS public.user_hospitals_id_seq;
DROP TABLE IF EXISTS public.user_hospitals;
DROP SEQUENCE IF EXISTS public.user_groups_id_seq;
DROP TABLE IF EXISTS public.user_groups;
DROP TABLE IF EXISTS public.system_activities_default;
DROP SEQUENCE IF EXISTS public.system_activities_id_seq;
DROP TABLE IF EXISTS public.system_activities;
DROP SEQUENCE IF EXISTS public.study_retention_policies_id_seq;
DROP TABLE IF EXISTS public.study_retention_policies;
DROP TABLE IF EXISTS public.study_retention_delete_requests_default;
DROP SEQUENCE IF EXISTS public.study_retention_delete_requests_id_seq;
DROP TABLE IF EXISTS public.study_retention_delete_requests;
DROP SEQUENCE IF EXISTS public.roles_id_seq;
DROP TABLE IF EXISTS public.roles;
DROP SEQUENCE IF EXISTS public.role_module_details_id_seq;
DROP TABLE IF EXISTS public.role_module_details;
DROP SEQUENCE IF EXISTS public.revoked_tokens_id_seq;
DROP TABLE IF EXISTS public.revoked_tokens;
DROP SEQUENCE IF EXISTS public.refresh_tokens_id_seq;
DROP TABLE IF EXISTS public.refresh_tokens;
DROP SEQUENCE IF EXISTS public.patients_id_seq;
DROP TABLE IF EXISTS public.patients;
DROP SEQUENCE IF EXISTS public.partition_maintenance_configs_id_seq;
DROP TABLE IF EXISTS public.partition_maintenance_configs;
DROP TABLE IF EXISTS public.pacs_worklists_week_cache;
DROP TABLE IF EXISTS public.pacs_worklist_histories_default;
DROP SEQUENCE IF EXISTS public.pacs_visit_sequences_id_seq;
DROP TABLE IF EXISTS public.pacs_visit_sequences;
DROP SEQUENCE IF EXISTS public.pacs_viewer_states_id_seq;
DROP TABLE IF EXISTS public.pacs_viewer_states;
DROP TABLE IF EXISTS public.pacs_system_settings;
DROP TABLE IF EXISTS public.pacs_studies_week_cache;
DROP SEQUENCE IF EXISTS public.pacs_studies_id_seq;
DROP TABLE IF EXISTS public.pacs_studies;
DROP SEQUENCE IF EXISTS public.pacs_results_id_seq;
DROP TABLE IF EXISTS public.pacs_results;
DROP SEQUENCE IF EXISTS public.pacs_result_versions_id_seq;
DROP TABLE IF EXISTS public.pacs_result_versions;
DROP SEQUENCE IF EXISTS public.pacs_result_templates_id_seq;
DROP TABLE IF EXISTS public.pacs_result_templates;
DROP SEQUENCE IF EXISTS public.pacs_result_images_id_seq;
DROP TABLE IF EXISTS public.pacs_result_images;
DROP TABLE IF EXISTS public.pacs_realtime_notification_events_default;
DROP SEQUENCE IF EXISTS public.pacs_realtime_notification_events_id_seq;
DROP TABLE IF EXISTS public.pacs_realtime_notification_events;
DROP SEQUENCE IF EXISTS public.pacs_queue_study_links_id_seq;
DROP TABLE IF EXISTS public.pacs_worklist_study_links;
DROP SEQUENCE IF EXISTS public.pacs_patient_sequences_id_seq;
DROP TABLE IF EXISTS public.pacs_patient_sequences;
DROP SEQUENCE IF EXISTS public.pacs_patient_queue_id_seq;
DROP TABLE IF EXISTS public.pacs_worklists;
DROP SEQUENCE IF EXISTS public.pacs_patient_queue_histories_id_seq;
DROP TABLE IF EXISTS public.pacs_worklist_histories;
DROP TABLE IF EXISTS public.pacs_daily_stats;
DROP SEQUENCE IF EXISTS public.oauth2_clients_id_seq;
DROP TABLE IF EXISTS public.oauth2_clients;
DROP SEQUENCE IF EXISTS public.modulights_id_seq;
DROP SEQUENCE IF EXISTS public.modules_id_seq;
DROP TABLE IF EXISTS public.modules;
DROP SEQUENCE IF EXISTS public.module_types_id_seq;
DROP TABLE IF EXISTS public.module_types;
DROP SEQUENCE IF EXISTS public.module_details_id_seq;
DROP TABLE IF EXISTS public.module_details;
DROP TABLE IF EXISTS public.modalities;
DROP SEQUENCE IF EXISTS public.hospitals_id_seq;
DROP TABLE IF EXISTS public.hospitals;
DROP SEQUENCE IF EXISTS public.hospital_modulights_id_seq;
DROP SEQUENCE IF EXISTS public.hospital_modulight_server_routes_id_seq;
DROP TABLE IF EXISTS public.hospital_modality_server_routes;
DROP TABLE IF EXISTS public.hospital_modalities;
DROP SEQUENCE IF EXISTS public.hospital_dicom_servers_id_seq;
DROP TABLE IF EXISTS public.hospital_dicom_servers;
DROP SEQUENCE IF EXISTS public.hospital_dicom_routing_configs_id_seq;
DROP TABLE IF EXISTS public.hospital_dicom_routing_configs;
DROP SEQUENCE IF EXISTS public.hospital_dicom_machines_id_seq;
DROP TABLE IF EXISTS public.hospital_dicom_machines;
DROP TABLE IF EXISTS public.flyway_schema_history;
DROP SEQUENCE IF EXISTS public.endpoint_permissions_id_seq;
DROP TABLE IF EXISTS public.endpoint_permissions;
DROP SEQUENCE IF EXISTS public.dicom_server_unmatched_callback_log_id_seq;
DROP TABLE IF EXISTS public.dicom_server_unmatched_callback_log;
DROP TABLE IF EXISTS public.dicom_server_callback_log_default;
DROP SEQUENCE IF EXISTS public.dicom_server_callback_log_id_seq;
DROP TABLE IF EXISTS public.dicom_server_callback_log;
DROP SEQUENCE IF EXISTS public.countries_id_seq;
DROP TABLE IF EXISTS public.countries;
DROP FUNCTION IF EXISTS public.unschedule_monthly_partition_maintenance_pg_cron();
DROP FUNCTION IF EXISTS public.sync_pacs_worklist_week_cache(p_worklist_id bigint);
DROP FUNCTION IF EXISTS public.sync_pacs_study_week_cache(p_study_id bigint);
DROP FUNCTION IF EXISTS public.schedule_monthly_partition_maintenance_pg_cron();
DROP FUNCTION IF EXISTS public.run_partition_maintenance();
DROP FUNCTION IF EXISTS public.run_monthly_partition_maintenance();
DROP FUNCTION IF EXISTS public.refresh_pacs_week_cache();
DROP FUNCTION IF EXISTS public.pacs_worklist_week_cache_trigger();
DROP FUNCTION IF EXISTS public.pacs_sync_worklist_primary_study();
DROP FUNCTION IF EXISTS public.pacs_sync_primary_link_to_worklist();
DROP FUNCTION IF EXISTS public.pacs_sync_legacy_audit_timestamps();
DROP FUNCTION IF EXISTS public.pacs_study_week_cache_trigger();
DROP FUNCTION IF EXISTS public.pacs_set_result_image_scope();
DROP FUNCTION IF EXISTS public.pacs_refresh_daily_stats(target_date date, target_hospital_id bigint);
DROP FUNCTION IF EXISTS public.pacs_ensure_monthly_partition(parent_table regclass, month_start date);
DROP FUNCTION IF EXISTS public.pacs_capture_result_version();
DROP FUNCTION IF EXISTS public.pacs_archive_policy_rows(p_source regclass, p_archive regclass, p_where_sql text);
DROP FUNCTION IF EXISTS public.hospital_visit_code_token(raw_value text);
DROP FUNCTION IF EXISTS public.ensure_partition_child_indexes(p_parent_schema text, p_parent_table text, p_child_schema text, p_child_table text);
DROP FUNCTION IF EXISTS public.drop_policy_partitions_if_fully_expired(p_dry_run boolean);
DROP FUNCTION IF EXISTS public.drop_old_monthly_partitions();
DROP FUNCTION IF EXISTS public.drop_expired_fixed_partitions();
DROP FUNCTION IF EXISTS public.create_future_partitions();
DROP FUNCTION IF EXISTS public.create_future_monthly_partitions();
DROP FUNCTION IF EXISTS public.cleanup_policy_based_retention_data(p_dry_run boolean);
DROP FUNCTION IF EXISTS public.cleanup_pacs_week_cache();
DROP EXTENSION IF EXISTS pgcrypto;
DROP EXTENSION IF EXISTS pg_trgm;
--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: cleanup_pacs_week_cache(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.cleanup_pacs_week_cache() RETURNS text
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: FUNCTION cleanup_pacs_week_cache(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.cleanup_pacs_week_cache() IS 'Deletes expired or inactive rows from PACS weekly list caches and analyzes both cache tables.';


--
-- Name: cleanup_policy_based_retention_data(boolean); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.cleanup_policy_based_retention_data(p_dry_run boolean DEFAULT true) RETURNS TABLE(parent_table text, eligible_rows bigint, action_taken text)
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: FUNCTION cleanup_policy_based_retention_data(p_dry_run boolean); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.cleanup_policy_based_retention_data(p_dry_run boolean) IS 'Dry-runs or deletes only policy rows whose purge_after is set and older than current_date.';


--
-- Name: create_future_monthly_partitions(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_future_monthly_partitions() RETURNS integer
    LANGUAGE sql
    AS $$
    SELECT create_future_partitions();
$$;


--
-- Name: FUNCTION create_future_monthly_partitions(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.create_future_monthly_partitions() IS 'Creates current and configured future monthly partitions, plus default partitions, for active native partitioned parents.';


--
-- Name: create_future_partitions(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_future_partitions() RETURNS integer
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: FUNCTION create_future_partitions(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.create_future_partitions() IS 'Creates future MONTH or YEAR partitions from partition_maintenance_configs and always keeps default partitions.';


--
-- Name: drop_expired_fixed_partitions(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.drop_expired_fixed_partitions() RETURNS integer
    LANGUAGE plpgsql
    AS $_$
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
$_$;


--
-- Name: FUNCTION drop_expired_fixed_partitions(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.drop_expired_fixed_partitions() IS 'Drops only native fixed-retention monthly child partitions named parent_YYYYMM; never drops policy-based partitions.';


--
-- Name: drop_old_monthly_partitions(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.drop_old_monthly_partitions() RETURNS integer
    LANGUAGE sql
    AS $$
    SELECT drop_expired_fixed_partitions();
$$;


--
-- Name: FUNCTION drop_old_monthly_partitions(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.drop_old_monthly_partitions() IS 'Drops only native child partitions named parent_YYYYMM older than retention_months; never drops parent/default tables.';


--
-- Name: drop_policy_partitions_if_fully_expired(boolean); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.drop_policy_partitions_if_fully_expired(p_dry_run boolean DEFAULT true) RETURNS TABLE(parent_table text, partition_table text, total_rows bigint, protected_rows bigint, action_taken text)
    LANGUAGE plpgsql
    AS $_$
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
        SELECT
            config.parent_schema,
            config.parent_table
        FROM partition_maintenance_configs config
        WHERE config.is_active = 1
          AND config.parent_schema = 'public'
          AND config.parent_table IN (
              'pacs_worklist_histories',
              'study_retention_delete_requests'
          )
          AND config.retention_mode = 'POLICY_BASED'
          AND config.partition_granularity = 'YEAR'
          AND config.allow_auto_drop = FALSE
        ORDER BY config.parent_table
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
                SELECT
                    cfg.parent_table::TEXT,
                    child.child_table::TEXT,
                    0::BIGINT,
                    0::BIGINT,
                    'kept_current_or_future_year'::TEXT;
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
                SELECT
                    cfg.parent_table::TEXT,
                    child.child_table::TEXT,
                    total_count,
                    protected_count,
                    'kept'::TEXT;
                CONTINUE;
            END IF;

            IF p_dry_run THEN
                RETURN QUERY
                SELECT
                    cfg.parent_table::TEXT,
                    child.child_table::TEXT,
                    total_count,
                    protected_count,
                    'dry_run_drop_candidate'::TEXT;
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
                cfg.parent_table::TEXT,
                child.child_table::TEXT,
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
$_$;


--
-- Name: FUNCTION drop_policy_partitions_if_fully_expired(p_dry_run boolean); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.drop_policy_partitions_if_fully_expired(p_dry_run boolean) IS 'Dry-runs by default; only past yearly native partitions for the two approved policy tables can be archived and dropped when every row is purge eligible.';


--
-- Name: ensure_partition_child_indexes(text, text, text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.ensure_partition_child_indexes(p_parent_schema text, p_parent_table text, p_child_schema text, p_child_table text) RETURNS integer
    LANGUAGE plpgsql
    AS $$
DECLARE
    created_count INTEGER := 0;
    idx_name TEXT;
BEGIN
    IF p_parent_table = 'dicom_server_callback_log' THEN
        idx_name := 'ux_' || SUBSTRING(MD5(p_child_table || '_hospital_dedupe') FROM 1 FOR 24);
        IF TO_REGCLASS(FORMAT('%I.%I', p_child_schema, idx_name)) IS NULL THEN
            EXECUTE FORMAT(
                'CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I.%I (hospital_id, dedupe_key) WHERE dedupe_key IS NOT NULL',
                idx_name, p_child_schema, p_child_table
            );
            created_count := created_count + 1;
        END IF;
    ELSIF p_parent_table = 'pacs_realtime_notification_events' THEN
        idx_name := 'ux_' || SUBSTRING(MD5(p_child_table || '_hospital_dedupe') FROM 1 FOR 24);
        IF TO_REGCLASS(FORMAT('%I.%I', p_child_schema, idx_name)) IS NULL THEN
            EXECUTE FORMAT(
                'CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I.%I (hospital_id, dedupe_key)',
                idx_name, p_child_schema, p_child_table
            );
            created_count := created_count + 1;
        END IF;
    END IF;

    RETURN created_count;
END
$$;


--
-- Name: FUNCTION ensure_partition_child_indexes(p_parent_schema text, p_parent_table text, p_child_schema text, p_child_table text); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.ensure_partition_child_indexes(p_parent_schema text, p_parent_table text, p_child_schema text, p_child_table text) IS 'Ensures child-local dedupe indexes for partitioned callback/realtime event tables without noisy repeated CREATE INDEX notices.';


--
-- Name: hospital_visit_code_token(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.hospital_visit_code_token(raw_value text) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    AS $_$
DECLARE
    normalized TEXT;
    compact TEXT;
    token TEXT;
BEGIN
    normalized := UPPER(TRIM(COALESCE(raw_value, '')));
    IF normalized <> '' THEN
        FOREACH token IN ARRAY regexp_split_to_array(normalized, '[^A-Z0-9]+') LOOP
            IF token ~ '^[A-Z0-9]{2,20}$'
               AND token NOT IN ('HOSPITAL', 'HOSP', 'CLINIC', 'CENTER', 'CENTRE') THEN
                RETURN token;
            END IF;
        END LOOP;
    END IF;

    compact := regexp_replace(normalized, '[^A-Z0-9]', '', 'g');
    IF compact = '' THEN
        RETURN '';
    END IF;
    RETURN LEFT(compact, 20);
END;
$_$;


--
-- Name: pacs_archive_policy_rows(regclass, regclass, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.pacs_archive_policy_rows(p_source regclass, p_archive regclass, p_where_sql text DEFAULT 'TRUE'::text) RETURNS bigint
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: FUNCTION pacs_archive_policy_rows(p_source regclass, p_archive regclass, p_where_sql text); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.pacs_archive_policy_rows(p_source regclass, p_archive regclass, p_where_sql text) IS 'Archives the writable common columns from a policy source table before approved row deletion or partition drop.';


--
-- Name: pacs_capture_result_version(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.pacs_capture_result_version() RETURNS trigger
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


--
-- Name: pacs_ensure_monthly_partition(regclass, date); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.pacs_ensure_monthly_partition(parent_table regclass, month_start date) RETURNS regclass
    LANGUAGE plpgsql
    AS $$
DECLARE
    parent_schema text;
    parent_name text;
    partition_name text;
    start_date date := date_trunc('month', month_start)::date;
    end_date date := (date_trunc('month', month_start) + interval '1 month')::date;
BEGIN
    SELECT ns.nspname, cls.relname
    INTO parent_schema, parent_name
    FROM pg_class cls
    JOIN pg_namespace ns ON ns.oid = cls.relnamespace
    WHERE cls.oid = parent_table
      AND cls.relkind = 'p';

    IF parent_name IS NULL THEN
        RAISE EXCEPTION '% is not a partitioned table', parent_table;
    END IF;

    partition_name := parent_name || '_' || to_char(start_date, 'YYYYMM');
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
        parent_schema, partition_name, parent_table, start_date, end_date
    );
    RETURN to_regclass(format('%I.%I', parent_schema, partition_name));
END
$$;


--
-- Name: FUNCTION pacs_ensure_monthly_partition(parent_table regclass, month_start date); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.pacs_ensure_monthly_partition(parent_table regclass, month_start date) IS 'Idempotently creates the monthly partition covering month_start for a RANGE-partitioned table. Called by the application partition-maintenance scheduler.';


--
-- Name: pacs_refresh_daily_stats(date, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.pacs_refresh_daily_stats(target_date date, target_hospital_id bigint DEFAULT NULL::bigint) RETURNS integer
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


--
-- Name: pacs_set_result_image_scope(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.pacs_set_result_image_scope() RETURNS trigger
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


--
-- Name: pacs_study_week_cache_trigger(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.pacs_study_week_cache_trigger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: FUNCTION pacs_study_week_cache_trigger(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.pacs_study_week_cache_trigger() IS 'Synchronizes recent active Studies directly from NEW and removes stale same-study cache rows before insert.';


--
-- Name: pacs_sync_legacy_audit_timestamps(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.pacs_sync_legacy_audit_timestamps() RETURNS trigger
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


--
-- Name: pacs_sync_primary_link_to_worklist(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.pacs_sync_primary_link_to_worklist() RETURNS trigger
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


--
-- Name: pacs_sync_worklist_primary_study(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.pacs_sync_worklist_primary_study() RETURNS trigger
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


--
-- Name: pacs_worklist_week_cache_trigger(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.pacs_worklist_week_cache_trigger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: FUNCTION pacs_worklist_week_cache_trigger(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.pacs_worklist_week_cache_trigger() IS 'Synchronizes recent Worklists directly from NEW; historical inserts avoid cache SQL.';


--
-- Name: refresh_pacs_week_cache(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.refresh_pacs_week_cache() RETURNS text
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: FUNCTION refresh_pacs_week_cache(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.refresh_pacs_week_cache() IS 'Truncates and rebuilds PACS weekly list caches from pacs_worklists and pacs_studies, then analyzes both cache tables.';


--
-- Name: run_monthly_partition_maintenance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.run_monthly_partition_maintenance() RETURNS text
    LANGUAGE sql
    AS $$
    SELECT run_partition_maintenance();
$$;


--
-- Name: FUNCTION run_monthly_partition_maintenance(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.run_monthly_partition_maintenance() IS 'Runs create_future_monthly_partitions, drop_old_monthly_partitions, and ANALYZE for active partitioned parents.';


--
-- Name: run_partition_maintenance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.run_partition_maintenance() RETURNS text
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: FUNCTION run_partition_maintenance(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.run_partition_maintenance() IS 'Creates future partitions, drops expired fixed technical logs, dry-runs policy cleanup, and analyzes active partition parents.';


--
-- Name: schedule_monthly_partition_maintenance_pg_cron(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.schedule_monthly_partition_maintenance_pg_cron() RETURNS text
    LANGUAGE plpgsql
    AS $_$
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
$_$;


--
-- Name: FUNCTION schedule_monthly_partition_maintenance_pg_cron(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.schedule_monthly_partition_maintenance_pg_cron() IS 'Optionally schedules run_monthly_partition_maintenance with pg_cron when pg_cron is installed.';


--
-- Name: sync_pacs_study_week_cache(bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_pacs_study_week_cache(p_study_id bigint) RETURNS void
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: FUNCTION sync_pacs_study_week_cache(p_study_id bigint); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.sync_pacs_study_week_cache(p_study_id bigint) IS 'Synchronizes a recent active Study into the week cache, first removing stale cache rows for the same id/public_id/StudyInstanceUID.';


--
-- Name: sync_pacs_worklist_week_cache(bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_pacs_worklist_week_cache(p_worklist_id bigint) RETURNS void
    LANGUAGE plpgsql
    AS $$
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
$$;


--
-- Name: unschedule_monthly_partition_maintenance_pg_cron(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.unschedule_monthly_partition_maintenance_pg_cron() RETURNS text
    LANGUAGE plpgsql
    AS $_$
DECLARE
    unscheduled BOOLEAN;
BEGIN
    IF TO_REGNAMESPACE('cron') IS NULL THEN
        RETURN 'pg_cron schema is not installed.';
    END IF;

    BEGIN
        EXECUTE 'SELECT cron.unschedule($1)'
        INTO unscheduled
        USING 'emr-pacs-monthly-partition-maintenance';
    EXCEPTION
        WHEN undefined_function OR invalid_schema_name THEN
            RETURN 'pg_cron functions are not available.';
    END;

    RETURN FORMAT('pg_cron monthly partition maintenance unscheduled=%s', COALESCE(unscheduled, FALSE));
END
$_$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: countries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.countries (
    id bigint NOT NULL,
    name character varying(160) NOT NULL,
    status integer DEFAULT 1 NOT NULL,
    is_active integer DEFAULT 1 NOT NULL,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified timestamp with time zone
);


--
-- Name: countries_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.countries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: countries_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.countries_id_seq OWNED BY public.countries.id;


--
-- Name: dicom_server_callback_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dicom_server_callback_log (
    id bigint CONSTRAINT orthanc_callback_log_id_not_null NOT NULL,
    event character varying(120),
    payload jsonb DEFAULT '{}'::jsonb CONSTRAINT orthanc_callback_log_payload_not_null NOT NULL,
    success boolean DEFAULT false CONSTRAINT orthanc_callback_log_success_not_null NOT NULL,
    error_message text,
    warning_message text,
    received_at timestamp with time zone DEFAULT now() CONSTRAINT orthanc_callback_log_received_at_not_null NOT NULL,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT orthanc_callback_log_created_at_not_null NOT NULL,
    accession_number character varying(255),
    dicom_server_study_id character varying(255),
    dicom_server_patient_id character varying(255),
    dicom_server_series_ids jsonb DEFAULT '[]'::jsonb NOT NULL,
    hospital_id bigint NOT NULL,
    dicom_server_id bigint,
    dedupe_key character varying(64),
    payload_sha256 character(64),
    attempt_count integer DEFAULT 1 NOT NULL,
    last_received_at timestamp with time zone,
    CONSTRAINT chk_callback_log_attempt_count CHECK ((attempt_count > 0)),
    CONSTRAINT chk_callback_log_payload_sha256 CHECK (((payload_sha256 IS NULL) OR (payload_sha256 ~ '^[0-9a-f]{64}$'::text)))
)
PARTITION BY RANGE (received_at);


--
-- Name: COLUMN dicom_server_callback_log.hospital_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.dicom_server_callback_log.hospital_id IS 'Hospital scope is mandatory; unknown callbacks are stored in dicom_server_unmatched_callback_log.';


--
-- Name: dicom_server_callback_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dicom_server_callback_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dicom_server_callback_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dicom_server_callback_log_id_seq OWNED BY public.dicom_server_callback_log.id;


--
-- Name: dicom_server_callback_log_default; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dicom_server_callback_log_default (
    id bigint DEFAULT nextval('public.dicom_server_callback_log_id_seq'::regclass) CONSTRAINT orthanc_callback_log_id_not_null NOT NULL,
    event character varying(120),
    payload jsonb DEFAULT '{}'::jsonb CONSTRAINT orthanc_callback_log_payload_not_null NOT NULL,
    success boolean DEFAULT false CONSTRAINT orthanc_callback_log_success_not_null NOT NULL,
    error_message text,
    warning_message text,
    received_at timestamp with time zone DEFAULT now() CONSTRAINT orthanc_callback_log_received_at_not_null NOT NULL,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT orthanc_callback_log_created_at_not_null NOT NULL,
    accession_number character varying(255),
    dicom_server_study_id character varying(255),
    dicom_server_patient_id character varying(255),
    dicom_server_series_ids jsonb DEFAULT '[]'::jsonb CONSTRAINT dicom_server_callback_log_dicom_server_series_ids_not_null NOT NULL,
    hospital_id bigint CONSTRAINT dicom_server_callback_log_hospital_id_not_null NOT NULL,
    dicom_server_id bigint,
    dedupe_key character varying(64),
    payload_sha256 character(64),
    attempt_count integer DEFAULT 1 CONSTRAINT dicom_server_callback_log_attempt_count_not_null NOT NULL,
    last_received_at timestamp with time zone,
    CONSTRAINT chk_callback_log_attempt_count CHECK ((attempt_count > 0)),
    CONSTRAINT chk_callback_log_payload_sha256 CHECK (((payload_sha256 IS NULL) OR (payload_sha256 ~ '^[0-9a-f]{64}$'::text)))
);


--
-- Name: dicom_server_unmatched_callback_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dicom_server_unmatched_callback_log (
    id bigint NOT NULL,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    original_callback_log_id bigint,
    dicom_server_id bigint,
    dedupe_key character varying(64),
    payload_sha256 character(64),
    event character varying(120),
    accession_number character varying(255),
    dicom_server_study_id character varying(255),
    dicom_server_patient_id character varying(255),
    dicom_server_series_ids jsonb DEFAULT '[]'::jsonb CONSTRAINT dicom_server_unmatched_callbac_dicom_server_series_ids_not_null NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    success boolean DEFAULT false NOT NULL,
    error_message text,
    warning_message text,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    last_received_at timestamp with time zone,
    attempt_count integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_unmatched_callback_attempt_count CHECK ((attempt_count > 0)),
    CONSTRAINT chk_unmatched_callback_payload_sha256 CHECK (((payload_sha256 IS NULL) OR (payload_sha256 ~ '^[0-9a-f]{64}$'::text)))
);


--
-- Name: TABLE dicom_server_unmatched_callback_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.dicom_server_unmatched_callback_log IS 'Quarantine table for DICOM server callbacks that arrive before the API can resolve a hospital scope.';


--
-- Name: dicom_server_unmatched_callback_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dicom_server_unmatched_callback_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dicom_server_unmatched_callback_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dicom_server_unmatched_callback_log_id_seq OWNED BY public.dicom_server_unmatched_callback_log.id;


--
-- Name: endpoint_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.endpoint_permissions (
    id bigint NOT NULL,
    http_method character varying(20) NOT NULL,
    endpoint_pattern character varying(255) NOT NULL,
    permission_code character varying(200) NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    created timestamp with time zone DEFAULT now() NOT NULL,
    required_scope character varying(150)
);


--
-- Name: endpoint_permissions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.endpoint_permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: endpoint_permissions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.endpoint_permissions_id_seq OWNED BY public.endpoint_permissions.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: hospital_dicom_machines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hospital_dicom_machines (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    modality_id bigint NOT NULL,
    machine_name character varying(160) NOT NULL,
    machine_ae_title character varying(64) NOT NULL,
    machine_host character varying(255) NOT NULL,
    machine_port integer DEFAULT 104 NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    modified_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    modified_at timestamp with time zone DEFAULT now() NOT NULL,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT chk_hdm_is_active CHECK ((is_active = ANY (ARRAY[1, 2]))),
    CONSTRAINT chk_hdm_machine_port_range CHECK (((machine_port > 0) AND (machine_port <= 65535)))
);


--
-- Name: TABLE hospital_dicom_machines; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.hospital_dicom_machines IS 'Reusable physical modality machines and rooms per hospital. A machine is identified by hospital, modality, AE title, host, and DICOM port.';


--
-- Name: hospital_dicom_machines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hospital_dicom_machines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospital_dicom_machines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.hospital_dicom_machines_id_seq OWNED BY public.hospital_dicom_machines.id;


--
-- Name: hospital_dicom_routing_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hospital_dicom_routing_configs (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    modified_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    modified_at timestamp with time zone DEFAULT now() NOT NULL,
    dicom_server_id bigint,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    package_built_at timestamp with time zone,
    package_built_by bigint
);


--
-- Name: COLUMN hospital_dicom_routing_configs.dicom_server_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_routing_configs.dicom_server_id IS 'Destination DICOM server for this routing configuration. Route rows inherit this server.';


--
-- Name: COLUMN hospital_dicom_routing_configs.package_built_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_routing_configs.package_built_at IS 'First/current time a UDAYA_DICOM_SERVER deployment package was generated. Hospitals with any built package keep identity fields locked.';


--
-- Name: COLUMN hospital_dicom_routing_configs.package_built_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_routing_configs.package_built_by IS 'User id that most recently generated the UDAYA_DICOM_SERVER deployment package.';


--
-- Name: hospital_dicom_routing_configs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hospital_dicom_routing_configs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospital_dicom_routing_configs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.hospital_dicom_routing_configs_id_seq OWNED BY public.hospital_dicom_routing_configs.id;


--
-- Name: hospital_dicom_servers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hospital_dicom_servers (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    ip_address character varying(255) NOT NULL,
    port integer NOT NULL,
    ae_title character varying(64),
    username character varying(150),
    password character varying(255),
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    modified_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    modified_at timestamp with time zone DEFAULT now() NOT NULL,
    viewer_base_url text,
    dicom_port integer DEFAULT 4242,
    storage_directory text DEFAULT '/var/lib/udaya_dicom_server/db'::text,
    index_directory text DEFAULT '/var/lib/udaya_dicom_server/db'::text,
    maximum_storage_size bigint DEFAULT 0,
    maximum_patient_count bigint DEFAULT 0,
    remote_access_allowed boolean DEFAULT true,
    http_server_enabled boolean DEFAULT true,
    enable_http_compression boolean DEFAULT true,
    ssl_enabled boolean DEFAULT false,
    authentication_enabled boolean DEFAULT true,
    authorization_enabled boolean DEFAULT true,
    authorization_root character varying(255) DEFAULT '/authorization'::character varying,
    authorization_checked_level character varying(64) DEFAULT 'studies'::character varying,
    dicom_always_allow_echo boolean DEFAULT true,
    dicom_always_allow_find boolean DEFAULT true,
    dicom_always_allow_get boolean DEFAULT true,
    dicom_always_allow_move boolean DEFAULT true,
    dicom_always_allow_store boolean DEFAULT true,
    dicom_check_called_aet boolean DEFAULT false,
    dicom_tls_enabled boolean DEFAULT false,
    dicom_scp_timeout integer DEFAULT 30,
    dicom_peers_json text DEFAULT '{}'::text,
    worklists_enabled boolean DEFAULT true,
    worklists_database text DEFAULT '/var/lib/udaya_dicom_server/worklists'::text,
    plugins_paths text DEFAULT (('/usr/share/udaya_dicom_server/plugins'::text || chr(10)) || '/usr/local/share/udaya_dicom_server/plugins'::text),
    pacs_api_callback_base_url text,
    pacs_result_api_key_hash text,
    dicomweb_path text DEFAULT '/dicom-web'::text,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    public_health_check_url character varying(1024),
    CONSTRAINT chk_hds_dicom_port_range CHECK (((dicom_port IS NULL) OR ((dicom_port > 0) AND (dicom_port <= 65535)))),
    CONSTRAINT chk_hds_is_active CHECK ((is_active = ANY (ARRAY[1, 2]))),
    CONSTRAINT chk_hds_port_positive CHECK ((port > 0)),
    CONSTRAINT chk_hds_port_range CHECK (((port > 0) AND (port <= 65535))),
    CONSTRAINT chk_hospital_dicom_servers_dicom_port_positive CHECK (((dicom_port IS NULL) OR (dicom_port > 0))),
    CONSTRAINT chk_hospital_dicom_servers_patient_limit CHECK (((maximum_patient_count IS NULL) OR (maximum_patient_count >= 0))),
    CONSTRAINT chk_hospital_dicom_servers_scp_timeout CHECK (((dicom_scp_timeout IS NULL) OR (dicom_scp_timeout > 0))),
    CONSTRAINT chk_hospital_dicom_servers_storage_limit CHECK (((maximum_storage_size IS NULL) OR (maximum_storage_size >= 0)))
);


--
-- Name: COLUMN hospital_dicom_servers.port; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.port IS 'DICOMweb / Orthanc REST HTTP port, for example 8042.';


--
-- Name: COLUMN hospital_dicom_servers.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.username IS 'Orthanc HTTP Basic Auth username used by PACS_API and generated Orthanc deployment packages.';


--
-- Name: COLUMN hospital_dicom_servers.password; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.password IS 'Orthanc HTTP Basic Auth password used by PACS_API and generated Orthanc deployment packages. Keep response DTOs redacted.';


--
-- Name: COLUMN hospital_dicom_servers.viewer_base_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.viewer_base_url IS 'Public URL for the single OHIF viewer. One viewer can serve all hospitals through the PACS API DICOMweb proxy.';


--
-- Name: COLUMN hospital_dicom_servers.dicom_port; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.dicom_port IS 'Native DICOM C-FIND/C-STORE port, for example 4242.';


--
-- Name: COLUMN hospital_dicom_servers.storage_directory; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.storage_directory IS 'Orthanc StorageDirectory.';


--
-- Name: COLUMN hospital_dicom_servers.index_directory; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.index_directory IS 'Orthanc IndexDirectory.';


--
-- Name: COLUMN hospital_dicom_servers.maximum_storage_size; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.maximum_storage_size IS 'Orthanc MaximumStorageSize. 0 means unlimited.';


--
-- Name: COLUMN hospital_dicom_servers.maximum_patient_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.maximum_patient_count IS 'Orthanc MaximumPatientCount. 0 means unlimited.';


--
-- Name: COLUMN hospital_dicom_servers.remote_access_allowed; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.remote_access_allowed IS 'Orthanc RemoteAccessAllowed.';


--
-- Name: COLUMN hospital_dicom_servers.http_server_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.http_server_enabled IS 'Orthanc HttpServerEnabled.';


--
-- Name: COLUMN hospital_dicom_servers.enable_http_compression; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.enable_http_compression IS 'Orthanc EnableHttpCompression.';


--
-- Name: COLUMN hospital_dicom_servers.ssl_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.ssl_enabled IS 'Orthanc SslEnabled for HTTP.';


--
-- Name: COLUMN hospital_dicom_servers.authentication_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.authentication_enabled IS 'Orthanc AuthenticationEnabled.';


--
-- Name: COLUMN hospital_dicom_servers.authorization_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.authorization_enabled IS 'Orthanc Authorization.Enabled.';


--
-- Name: COLUMN hospital_dicom_servers.authorization_root; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.authorization_root IS 'Orthanc Authorization.Root.';


--
-- Name: COLUMN hospital_dicom_servers.authorization_checked_level; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.authorization_checked_level IS 'Orthanc Authorization.CheckedLevel.';


--
-- Name: COLUMN hospital_dicom_servers.dicom_peers_json; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.dicom_peers_json IS 'Orthanc DicomPeers JSON object. DicomModalities are managed by DICOM Routing.';


--
-- Name: COLUMN hospital_dicom_servers.worklists_database; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.worklists_database IS 'Orthanc Worklists.Database.';


--
-- Name: COLUMN hospital_dicom_servers.plugins_paths; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.plugins_paths IS 'Orthanc Plugins paths, one per line.';


--
-- Name: COLUMN hospital_dicom_servers.pacs_api_callback_base_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.pacs_api_callback_base_url IS 'PACS API base URL reachable from Orthanc for stable-study callbacks and viewer token authorization.';


--
-- Name: COLUMN hospital_dicom_servers.pacs_result_api_key_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.pacs_result_api_key_hash IS 'BCrypt hash of the server-side PACS Result proxy API key generated per DICOM server. The raw key is written only to private deployment .env files and is never returned by API list/find responses.';


--
-- Name: COLUMN hospital_dicom_servers.dicomweb_path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.dicomweb_path IS 'DICOMweb path appended to the derived DICOM server HTTP base URL. Full public URLs are derived from ssl_enabled, ip_address, port, and this path.';


--
-- Name: COLUMN hospital_dicom_servers.public_health_check_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_dicom_servers.public_health_check_url IS 'Public or API-reachable URL used only for DICOM server health checks. If only host:port is configured, use /system.';


--
-- Name: hospital_dicom_servers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hospital_dicom_servers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospital_dicom_servers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.hospital_dicom_servers_id_seq OWNED BY public.hospital_dicom_servers.id;


--
-- Name: hospital_modalities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hospital_modalities (
    id bigint CONSTRAINT hospital_modulights_id_not_null NOT NULL,
    hospital_id bigint CONSTRAINT hospital_modulights_hospital_id_not_null NOT NULL,
    modality_id bigint CONSTRAINT hospital_modulights_modulight_id_not_null NOT NULL,
    is_active smallint DEFAULT 1 CONSTRAINT hospital_modulights_is_active_not_null NOT NULL,
    created_by bigint,
    modified_by bigint,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT hospital_modulights_created_at_not_null NOT NULL,
    modified_at timestamp with time zone DEFAULT now()
);


--
-- Name: hospital_modality_server_routes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hospital_modality_server_routes (
    id bigint CONSTRAINT hospital_modulight_server_routes_id_not_null NOT NULL,
    hospital_id bigint CONSTRAINT hospital_modulight_server_routes_hospital_id_not_null NOT NULL,
    modality_id bigint CONSTRAINT hospital_modulight_server_routes_modulight_id_not_null NOT NULL,
    is_active smallint DEFAULT 1 CONSTRAINT hospital_modulight_server_routes_is_active_not_null NOT NULL,
    created_by bigint,
    modified_by bigint,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT hospital_modulight_server_routes_created_at_not_null NOT NULL,
    modified_at timestamp with time zone DEFAULT now() CONSTRAINT hospital_modulight_server_routes_modified_at_not_null NOT NULL,
    routing_config_id bigint,
    machine_id bigint,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT chk_hmsr_active_machine_required CHECK (((is_active = 2) OR (machine_id IS NOT NULL))),
    CONSTRAINT chk_hmsr_is_active CHECK ((is_active = ANY (ARRAY[1, 2])))
);


--
-- Name: COLUMN hospital_modality_server_routes.routing_config_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_modality_server_routes.routing_config_id IS 'Parent routing configuration. The parent owns the destination DICOM server.';


--
-- Name: COLUMN hospital_modality_server_routes.machine_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.hospital_modality_server_routes.machine_id IS 'Required machine/room relation for active routing rows.';


--
-- Name: hospital_modulight_server_routes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hospital_modulight_server_routes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospital_modulight_server_routes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.hospital_modulight_server_routes_id_seq OWNED BY public.hospital_modality_server_routes.id;


--
-- Name: hospital_modulights_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hospital_modulights_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospital_modulights_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.hospital_modulights_id_seq OWNED BY public.hospital_modalities.id;


--
-- Name: hospitals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hospitals (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(255) NOT NULL,
    name_other character varying(255),
    dicomweb_base_url text,
    timezone character varying(80) DEFAULT 'Asia/Phnom_Penh'::character varying,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified_by bigint,
    modified timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    modified_at timestamp with time zone,
    abbr character varying(20),
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    logo_path text,
    logo_file_name character varying(255),
    logo_file_type character varying(80),
    logo_file_size bigint,
    logo_updated_at timestamp with time zone
);


--
-- Name: hospitals_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hospitals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospitals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.hospitals_id_seq OWNED BY public.hospitals.id;


--
-- Name: modalities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.modalities (
    id bigint CONSTRAINT modulights_id_not_null NOT NULL,
    name character varying(255) CONSTRAINT modulights_name_not_null NOT NULL,
    is_active smallint DEFAULT 1 CONSTRAINT modulights_is_active_not_null NOT NULL,
    created_by bigint,
    modified_by bigint,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT modulights_created_at_not_null NOT NULL,
    modified_at timestamp with time zone DEFAULT now(),
    abbr character varying(20),
    public_id uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: module_details; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.module_details (
    id bigint NOT NULL,
    module_id bigint NOT NULL,
    code character varying(200) NOT NULL,
    name character varying(255) NOT NULL,
    name_other character varying(255),
    type character varying(80) NOT NULL,
    action_key character varying(100),
    display_order integer DEFAULT 0,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified_by bigint,
    modified timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    modified_at timestamp with time zone,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: module_details_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.module_details_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: module_details_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.module_details_id_seq OWNED BY public.module_details.id;


--
-- Name: module_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.module_types (
    id bigint NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    name_other character varying(255),
    display_order integer DEFAULT 0,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified_by bigint,
    modified timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    modified_at timestamp with time zone,
    menu_group_code character varying(50) DEFAULT 'SETTING'::character varying,
    menu_group_name character varying(120) DEFAULT 'Setting'::character varying,
    menu_group_order smallint DEFAULT 99,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: module_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.module_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: module_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.module_types_id_seq OWNED BY public.module_types.id;


--
-- Name: modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.modules (
    id bigint NOT NULL,
    module_type_id bigint NOT NULL,
    code character varying(150) NOT NULL,
    name character varying(255) NOT NULL,
    name_other character varying(255),
    icon character varying(100),
    display_order integer DEFAULT 0,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified_by bigint,
    modified timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    modified_at timestamp with time zone,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: modules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.modules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: modules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.modules_id_seq OWNED BY public.modules.id;


--
-- Name: modulights_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.modulights_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: modulights_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.modulights_id_seq OWNED BY public.modalities.id;


--
-- Name: oauth2_clients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.oauth2_clients (
    id bigint NOT NULL,
    client_id character varying(150) NOT NULL,
    client_name character varying(255) NOT NULL,
    client_secret_hash character varying(128),
    client_type character varying(20) DEFAULT 'PUBLIC'::character varying NOT NULL,
    allowed_grant_types text NOT NULL,
    allowed_scopes text NOT NULL,
    access_token_lifetime_ms bigint DEFAULT 900000 NOT NULL,
    refresh_token_lifetime_ms bigint DEFAULT '2592000000'::bigint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified timestamp with time zone,
    dicom_server_id bigint,
    CONSTRAINT chk_oauth2_clients_access_lifetime_final CHECK ((access_token_lifetime_ms > 0)),
    CONSTRAINT chk_oauth2_clients_dicom_server_confidential CHECK (((dicom_server_id IS NULL) OR ((client_type)::text = 'CONFIDENTIAL'::text))),
    CONSTRAINT chk_oauth2_clients_refresh_lifetime_final CHECK ((refresh_token_lifetime_ms > 0)),
    CONSTRAINT chk_oauth2_clients_type CHECK (((client_type)::text = ANY (ARRAY[('PUBLIC'::character varying)::text, ('CONFIDENTIAL'::character varying)::text])))
);


--
-- Name: COLUMN oauth2_clients.dicom_server_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.oauth2_clients.dicom_server_id IS 'Optional DICOM server identity for generated DICOM server callback clients. Multiple active client credentials can exist so an older running DICOM server remains valid after a new config build rotates credentials.';


--
-- Name: oauth2_clients_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.oauth2_clients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: oauth2_clients_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.oauth2_clients_id_seq OWNED BY public.oauth2_clients.id;


--
-- Name: pacs_daily_stats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_daily_stats (
    hospital_id bigint NOT NULL,
    stat_date date NOT NULL,
    modality_id bigint DEFAULT 0 NOT NULL,
    waiting_count bigint DEFAULT 0 NOT NULL,
    in_progress_count bigint DEFAULT 0 NOT NULL,
    cancelled_count bigint DEFAULT 0 NOT NULL,
    failed_count bigint DEFAULT 0 NOT NULL,
    received_study_count bigint DEFAULT 0 NOT NULL,
    completed_result_count bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_pacs_daily_stats_nonnegative CHECK (((waiting_count >= 0) AND (in_progress_count >= 0) AND (cancelled_count >= 0) AND (failed_count >= 0) AND (received_study_count >= 0) AND (completed_result_count >= 0)))
);


--
-- Name: TABLE pacs_daily_stats; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_daily_stats IS 'Hospital daily summary used by dashboards instead of live counts on large clinical tables.';


--
-- Name: pacs_worklist_histories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_worklist_histories (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    worklist_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    from_status smallint NOT NULL,
    to_status smallint NOT NULL,
    action character varying(100) NOT NULL,
    reason text,
    created timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    created_at timestamp with time zone,
    retention_policy_id bigint,
    retain_until date,
    archive_after date,
    purge_after date
)
PARTITION BY RANGE (created);


--
-- Name: pacs_patient_queue_histories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_patient_queue_histories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_patient_queue_histories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_patient_queue_histories_id_seq OWNED BY public.pacs_worklist_histories.id;


--
-- Name: pacs_worklists; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_worklists (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    notes text,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified timestamp with time zone,
    modality_id bigint,
    visit_code character varying(80),
    created_by bigint,
    modified_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    modified_at timestamp with time zone DEFAULT now(),
    dicom_server_worklist_id character varying(255),
    dicom_server_worklist_path character varying(255),
    sent_at timestamp with time zone,
    received_at timestamp with time zone,
    study_description text,
    scheduled_date date,
    scheduled_time time without time zone,
    error_message text,
    started_at timestamp with time zone,
    image_received_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    study_id bigint,
    dicom_route_id bigint,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT chk_pacs_worklists_status CHECK (((status >= 1) AND (status <= 4))),
    CONSTRAINT chk_pacs_worklists_status_final CHECK ((status = ANY (ARRAY[1, 2, 3, 4])))
);


--
-- Name: COLUMN pacs_worklists.study_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pacs_worklists.study_id IS 'Compatibility pointer to the primary study. pacs_worklist_study_links is the canonical relation.';


--
-- Name: COLUMN pacs_worklists.dicom_route_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pacs_worklists.dicom_route_id IS 'Selected DICOM machine route relation. DICOM server is derived from hospital_modality_server_routes.';


--
-- Name: CONSTRAINT chk_pacs_worklists_status_final ON pacs_worklists; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT chk_pacs_worklists_status_final ON public.pacs_worklists IS 'Worklist status map: 1=WAITING, 2=IN_PROGRESS, 3=CANCELLED, 4=FAILED.';


--
-- Name: pacs_patient_queue_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_patient_queue_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_patient_queue_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_patient_queue_id_seq OWNED BY public.pacs_worklists.id;


--
-- Name: pacs_patient_sequences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_patient_sequences (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    sequence_year character varying(2) NOT NULL,
    last_sequence bigint DEFAULT 0 NOT NULL,
    modified_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: pacs_patient_sequences_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_patient_sequences_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_patient_sequences_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_patient_sequences_id_seq OWNED BY public.pacs_patient_sequences.id;


--
-- Name: pacs_worklist_study_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_worklist_study_links (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    worklist_id bigint NOT NULL,
    study_id bigint NOT NULL,
    is_primary smallint DEFAULT 1 NOT NULL,
    linked_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint
);


--
-- Name: pacs_queue_study_links_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_queue_study_links_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_queue_study_links_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_queue_study_links_id_seq OWNED BY public.pacs_worklist_study_links.id;


--
-- Name: pacs_realtime_notification_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_realtime_notification_events (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    source character varying(40) NOT NULL,
    event_type character varying(80) NOT NULL,
    severity character varying(20) DEFAULT 'info'::character varying NOT NULL,
    title character varying(255) NOT NULL,
    message text,
    worklist_id bigint,
    study_id bigint,
    worklist_public_key character varying(100),
    study_public_key character varying(100),
    patient_name character varying(255),
    visit_code character varying(100),
    accession_number character varying(100),
    dedupe_key character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
)
PARTITION BY RANGE (created_at);


--
-- Name: TABLE pacs_realtime_notification_events; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_realtime_notification_events IS 'Durable hospital-scoped outbox used to replay callback and upload events over authenticated SSE.';


--
-- Name: pacs_realtime_notification_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_realtime_notification_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_realtime_notification_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_realtime_notification_events_id_seq OWNED BY public.pacs_realtime_notification_events.id;


--
-- Name: pacs_realtime_notification_events_default; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_realtime_notification_events_default (
    id bigint DEFAULT nextval('public.pacs_realtime_notification_events_id_seq'::regclass) CONSTRAINT pacs_realtime_notification_events_id_not_null NOT NULL,
    hospital_id bigint CONSTRAINT pacs_realtime_notification_events_hospital_id_not_null NOT NULL,
    source character varying(40) CONSTRAINT pacs_realtime_notification_events_source_not_null NOT NULL,
    event_type character varying(80) CONSTRAINT pacs_realtime_notification_events_event_type_not_null NOT NULL,
    severity character varying(20) DEFAULT 'info'::character varying CONSTRAINT pacs_realtime_notification_events_severity_not_null NOT NULL,
    title character varying(255) CONSTRAINT pacs_realtime_notification_events_title_not_null NOT NULL,
    message text,
    worklist_id bigint,
    study_id bigint,
    worklist_public_key character varying(100),
    study_public_key character varying(100),
    patient_name character varying(255),
    visit_code character varying(100),
    accession_number character varying(100),
    dedupe_key character varying(255) CONSTRAINT pacs_realtime_notification_events_dedupe_key_not_null NOT NULL,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT pacs_realtime_notification_events_created_at_not_null NOT NULL
);


--
-- Name: pacs_result_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_result_images (
    id bigint NOT NULL,
    result_id bigint NOT NULL,
    image_path text NOT NULL,
    original_file_name character varying(255),
    file_type character varying(80),
    file_size bigint,
    sort_order integer DEFAULT 0 NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    image_public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    hospital_id bigint NOT NULL,
    modality_id bigint,
    study_id bigint,
    worklist_id bigint,
    file_sha256 character(64),
    CONSTRAINT chk_pacs_result_images_active CHECK ((is_active = ANY (ARRAY[1, 2]))),
    CONSTRAINT chk_pacs_result_images_file_size CHECK (((file_size IS NULL) OR (file_size >= 0))),
    CONSTRAINT chk_pacs_result_images_relative_path CHECK (((image_path IS NOT NULL) AND (btrim(image_path) <> ''::text) AND (image_path !~* '^[a-z][a-z0-9+.-]*://'::text) AND (image_path !~ '^//'::text) AND (image_path !~ '^[A-Za-z]:[\\/]'::text))),
    CONSTRAINT chk_pacs_result_images_sha256 CHECK (((file_sha256 IS NULL) OR (file_sha256 ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_pacs_result_images_sort_order CHECK ((sort_order >= 0))
);


--
-- Name: pacs_result_images_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_result_images_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_result_images_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_result_images_id_seq OWNED BY public.pacs_result_images.id;


--
-- Name: pacs_result_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_result_templates (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    modality_id bigint NOT NULL,
    template_name character varying(150) NOT NULL,
    template_content text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    modified_at timestamp with time zone DEFAULT now() NOT NULL,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_by bigint,
    modified_by bigint,
    CONSTRAINT chk_pacs_result_templates_is_active CHECK ((is_active = ANY (ARRAY[1, 2])))
);


--
-- Name: COLUMN pacs_result_templates.is_active; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pacs_result_templates.is_active IS 'Standard active flag: 1 active, 2 inactive.';


--
-- Name: pacs_result_templates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_result_templates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_result_templates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_result_templates_id_seq OWNED BY public.pacs_result_templates.id;


--
-- Name: pacs_result_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_result_versions (
    id bigint NOT NULL,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    hospital_id bigint NOT NULL,
    result_id bigint NOT NULL,
    version_no integer NOT NULL,
    modality_id bigint NOT NULL,
    study_id bigint,
    worklist_id bigint,
    patient_id bigint,
    result_date date NOT NULL,
    template_id bigint,
    result_text text,
    status character varying(30) NOT NULL,
    completed boolean NOT NULL,
    is_active smallint NOT NULL,
    changed_by bigint,
    change_reason character varying(255),
    changed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_pacs_result_versions_active CHECK ((is_active = ANY (ARRAY[1, 2]))),
    CONSTRAINT chk_pacs_result_versions_version CHECK ((version_no > 0))
);


--
-- Name: TABLE pacs_result_versions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_result_versions IS 'Immutable snapshots captured before meaningful PACS result updates.';


--
-- Name: pacs_result_versions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_result_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_result_versions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_result_versions_id_seq OWNED BY public.pacs_result_versions.id;


--
-- Name: pacs_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_results (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    modality_id bigint NOT NULL,
    study_id bigint,
    worklist_id bigint,
    patient_id bigint,
    result_date date DEFAULT CURRENT_DATE NOT NULL,
    template_id bigint,
    result_text text,
    status character varying(30) DEFAULT 'IMAGE_RECEIVED'::character varying NOT NULL,
    completed boolean DEFAULT false NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    modified_at timestamp with time zone DEFAULT now() NOT NULL,
    result_public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT chk_pacs_results_active_final CHECK ((is_active = ANY (ARRAY[1, 2]))),
    CONSTRAINT chk_pacs_results_has_parent CHECK (((study_id IS NOT NULL) OR (worklist_id IS NOT NULL))),
    CONSTRAINT chk_pacs_results_status_final CHECK (((status)::text = ANY (ARRAY[('IMAGE_RECEIVED'::character varying)::text, ('DRAFT'::character varying)::text, ('PRELIMINARY'::character varying)::text, ('FINAL'::character varying)::text, ('CANCELLED'::character varying)::text])))
);


--
-- Name: COLUMN pacs_results.modified_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pacs_results.modified_at IS 'Last modification timestamp for this PACS result.';


--
-- Name: CONSTRAINT chk_pacs_results_status_final ON pacs_results; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT chk_pacs_results_status_final ON public.pacs_results IS 'PACS result status values: IMAGE_RECEIVED, DRAFT, PRELIMINARY, FINAL, CANCELLED.';


--
-- Name: pacs_results_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_results_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_results_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_results_id_seq OWNED BY public.pacs_results.id;


--
-- Name: pacs_studies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_studies (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    study_instance_uid character varying(200) NOT NULL,
    accession_number character varying(100),
    modality character varying(20),
    study_date date,
    study_description text,
    status smallint DEFAULT 1,
    is_active smallint DEFAULT 1 NOT NULL,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified timestamp with time zone,
    dicom_server_study_id character varying(255),
    dicom_server_patient_id character varying(255),
    dicom_server_series_id character varying(255),
    received_at timestamp with time zone,
    dicom_server_id bigint,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    reference_visit_code character varying(120),
    source_type character varying(30),
    uploaded_by bigint,
    image_received_at timestamp with time zone,
    modality_id bigint,
    instance_count integer,
    institution_name character varying(255),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    CONSTRAINT chk_pacs_studies_active_final CHECK ((is_active = ANY (ARRAY[1, 2]))),
    CONSTRAINT chk_pacs_studies_source_type CHECK (((source_type IS NULL) OR ((source_type)::text = ANY (ARRAY[('WORKLIST'::character varying)::text, ('UPLOAD'::character varying)::text, ('MANUAL'::character varying)::text])))),
    CONSTRAINT chk_pacs_studies_status_final CHECK ((status = ANY (ARRAY[1, 2])))
);


--
-- Name: COLUMN pacs_studies.dicom_server_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pacs_studies.dicom_server_id IS 'Source DICOM server relation for received studies. Viewer URLs are generated dynamically from this server.';


--
-- Name: pacs_studies_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_studies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_studies_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_studies_id_seq OWNED BY public.pacs_studies.id;


--
-- Name: pacs_studies_week_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_studies_week_cache (
    id bigint NOT NULL,
    public_id uuid NOT NULL,
    hospital_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    modality_id bigint,
    study_instance_uid character varying(200) NOT NULL,
    accession_number character varying(100),
    reference_visit_code character varying(120),
    modality character varying(20),
    study_date date,
    received_at timestamp with time zone,
    image_received_at timestamp with time zone,
    study_description text,
    status smallint,
    is_active smallint DEFAULT 1 NOT NULL,
    dicom_server_id bigint,
    dicom_server_study_id character varying(255),
    dicom_server_patient_id character varying(255),
    dicom_server_series_id character varying(255),
    instance_count integer,
    institution_name character varying(255),
    created timestamp with time zone,
    modified timestamp with time zone,
    created_at timestamp with time zone,
    cached_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_pacs_studies_week_cache_active CHECK ((is_active = ANY (ARRAY[1, 2]))),
    CONSTRAINT chk_pacs_studies_week_cache_status CHECK (((status IS NULL) OR (status = ANY (ARRAY[1, 2]))))
);


--
-- Name: TABLE pacs_studies_week_cache; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_studies_week_cache IS 'Seven-day rebuildable cache for default Study list screens. IDs match pacs_studies; main table remains source of truth.';


--
-- Name: pacs_system_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_system_settings (
    setting_key character varying(160) NOT NULL,
    setting_value text NOT NULL,
    modified_by bigint,
    modified_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE pacs_system_settings; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_system_settings IS 'Small API runtime settings that can change without rebuilding containers.';


--
-- Name: COLUMN pacs_system_settings.setting_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pacs_system_settings.setting_key IS 'Stable system setting key.';


--
-- Name: COLUMN pacs_system_settings.setting_value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pacs_system_settings.setting_value IS 'Setting value stored as text and parsed by the owning service.';


--
-- Name: pacs_viewer_states; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_viewer_states (
    id bigint NOT NULL,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    hospital_id bigint NOT NULL,
    modality_id bigint,
    study_id bigint,
    worklist_id bigint,
    patient_id bigint,
    study_instance_uid character varying(255),
    accession_number character varying(255),
    patient_code character varying(255),
    state_type character varying(64) DEFAULT 'OHIF_VIEWER_STATE'::character varying NOT NULL,
    schema_version integer DEFAULT 1 NOT NULL,
    viewer_state jsonb DEFAULT '{}'::jsonb NOT NULL,
    measurements jsonb DEFAULT '[]'::jsonb NOT NULL,
    annotations jsonb DEFAULT '[]'::jsonb NOT NULL,
    segmentations jsonb DEFAULT '[]'::jsonb NOT NULL,
    additional_findings jsonb DEFAULT '[]'::jsonb NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    created_by bigint,
    modified_by bigint,
    is_active integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    modified_at timestamp with time zone DEFAULT now() NOT NULL,
    labelmap_segmentations jsonb DEFAULT '[]'::jsonb NOT NULL,
    contour_segmentations jsonb DEFAULT '[]'::jsonb NOT NULL,
    surface_segmentations jsonb DEFAULT '[]'::jsonb NOT NULL,
    presentation_state jsonb DEFAULT '{}'::jsonb NOT NULL,
    tool_state jsonb DEFAULT '{}'::jsonb NOT NULL,
    payload_size_bytes bigint DEFAULT 0 NOT NULL,
    payload_sha256 character(64),
    deleted_by bigint,
    deleted_at timestamp with time zone,
    CONSTRAINT ck_pacs_viewer_states_active_status CHECK ((is_active = ANY (ARRAY[1, 2]))),
    CONSTRAINT ck_pacs_viewer_states_payload_sha256 CHECK (((payload_sha256 IS NULL) OR (payload_sha256 ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT ck_pacs_viewer_states_payload_size CHECK (((payload_size_bytes >= 0) AND (payload_size_bytes <= 10485760))),
    CONSTRAINT ck_pacs_viewer_states_schema_version CHECK (((schema_version >= 1) AND (schema_version <= 1000))),
    CONSTRAINT ck_pacs_viewer_states_state_type CHECK (((state_type)::text ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$'::text)),
    CONSTRAINT ck_pacs_viewer_states_version CHECK ((version > 0))
);


--
-- Name: pacs_viewer_states_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_viewer_states_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_viewer_states_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_viewer_states_id_seq OWNED BY public.pacs_viewer_states.id;


--
-- Name: pacs_visit_sequences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_visit_sequences (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    sequence_date character varying(32) NOT NULL,
    last_sequence bigint DEFAULT 0 NOT NULL,
    modified_at timestamp with time zone DEFAULT now()
);


--
-- Name: pacs_visit_sequences_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_visit_sequences_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_visit_sequences_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pacs_visit_sequences_id_seq OWNED BY public.pacs_visit_sequences.id;


--
-- Name: pacs_worklist_histories_default; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_worklist_histories_default (
    id bigint DEFAULT nextval('public.pacs_patient_queue_histories_id_seq'::regclass) CONSTRAINT pacs_worklist_histories_id_not_null NOT NULL,
    hospital_id bigint CONSTRAINT pacs_worklist_histories_hospital_id_not_null NOT NULL,
    worklist_id bigint CONSTRAINT pacs_worklist_histories_worklist_id_not_null NOT NULL,
    patient_id bigint CONSTRAINT pacs_worklist_histories_patient_id_not_null NOT NULL,
    from_status smallint CONSTRAINT pacs_worklist_histories_from_status_not_null NOT NULL,
    to_status smallint CONSTRAINT pacs_worklist_histories_to_status_not_null NOT NULL,
    action character varying(100) CONSTRAINT pacs_worklist_histories_action_not_null NOT NULL,
    reason text,
    created timestamp with time zone DEFAULT now() CONSTRAINT pacs_worklist_histories_created_not_null NOT NULL,
    created_by bigint,
    created_at timestamp with time zone,
    retention_policy_id bigint,
    retain_until date,
    archive_after date,
    purge_after date
);


--
-- Name: pacs_worklists_week_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pacs_worklists_week_cache (
    id bigint NOT NULL,
    public_id uuid NOT NULL,
    hospital_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    modality_id bigint,
    dicom_route_id bigint,
    study_id bigint,
    visit_code character varying(80),
    status smallint NOT NULL,
    scheduled_date date,
    scheduled_time time without time zone,
    study_description text,
    dicom_server_worklist_id character varying(255),
    dicom_server_worklist_path text,
    sent_at timestamp with time zone,
    received_at timestamp with time zone,
    image_received_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    started_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    modified_at timestamp with time zone,
    created timestamp with time zone,
    cached_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    CONSTRAINT chk_pacs_worklists_week_cache_status CHECK ((status = ANY (ARRAY[1, 2, 3, 4])))
);


--
-- Name: TABLE pacs_worklists_week_cache; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_worklists_week_cache IS 'Seven-day rebuildable cache for default Worklist list screens. IDs match pacs_worklists; main table remains source of truth.';


--
-- Name: partition_maintenance_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.partition_maintenance_configs (
    id bigint NOT NULL,
    parent_schema character varying(80) DEFAULT 'public'::character varying NOT NULL,
    parent_table character varying(160) NOT NULL,
    partition_column character varying(160) NOT NULL,
    retention_months integer DEFAULT 12,
    is_active smallint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    partition_granularity character varying(20) DEFAULT 'MONTH'::character varying NOT NULL,
    retention_mode character varying(40) DEFAULT 'FIXED_MONTHS'::character varying NOT NULL,
    future_partitions integer DEFAULT 3 NOT NULL,
    allow_auto_drop boolean DEFAULT true NOT NULL,
    CONSTRAINT chk_partition_config_active CHECK ((is_active = ANY (ARRAY[1, 2]))),
    CONSTRAINT chk_partition_config_future CHECK ((future_partitions >= 1)),
    CONSTRAINT chk_partition_config_granularity CHECK (((partition_granularity)::text = ANY (ARRAY[('MONTH'::character varying)::text, ('YEAR'::character varying)::text]))),
    CONSTRAINT chk_partition_config_retention CHECK (((((retention_mode)::text = 'FIXED_MONTHS'::text) AND (retention_months IS NOT NULL) AND (retention_months > 0) AND ((partition_granularity)::text = 'MONTH'::text) AND (allow_auto_drop = true)) OR (((retention_mode)::text = 'POLICY_BASED'::text) AND ((partition_granularity)::text = 'YEAR'::text) AND (allow_auto_drop = false)))),
    CONSTRAINT chk_partition_config_retention_mode CHECK (((retention_mode)::text = ANY (ARRAY[('FIXED_MONTHS'::character varying)::text, ('POLICY_BASED'::character varying)::text])))
);


--
-- Name: TABLE partition_maintenance_configs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.partition_maintenance_configs IS 'Configures six native partitioned parents using one future_partitions setting: fixed monthly technical logs and policy-based yearly medical/audit tables.';


--
-- Name: partition_maintenance_configs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.partition_maintenance_configs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: partition_maintenance_configs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.partition_maintenance_configs_id_seq OWNED BY public.partition_maintenance_configs.id;


--
-- Name: patients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patients (
    id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    patient_uid character varying(100) NOT NULL,
    gender character varying(10),
    date_of_birth date,
    is_active smallint DEFAULT 1 NOT NULL,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified timestamp with time zone,
    phone_number character varying(50),
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    first_name character varying(255) DEFAULT ''::character varying NOT NULL,
    last_name character varying(255) DEFAULT ''::character varying NOT NULL,
    patient_hn character varying(100),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    CONSTRAINT chk_patients_is_active CHECK ((is_active = ANY (ARRAY[1, 2])))
);


--
-- Name: patients_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.patients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patients_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.patients_id_seq OWNED BY public.patients.id;


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    client_id character varying(150) NOT NULL,
    client_name character varying(255),
    token_hash character varying(128) NOT NULL,
    rotated_from_id bigint,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    revoked_reason character varying(255),
    ip_address character varying(80),
    user_agent text,
    created timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.refresh_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.refresh_tokens_id_seq OWNED BY public.refresh_tokens.id;


--
-- Name: revoked_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.revoked_tokens (
    id bigint NOT NULL,
    jti character varying(150) NOT NULL,
    user_id bigint,
    hospital_id bigint,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone DEFAULT now() NOT NULL,
    reason character varying(255)
);


--
-- Name: revoked_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.revoked_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: revoked_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.revoked_tokens_id_seq OWNED BY public.revoked_tokens.id;


--
-- Name: role_module_details; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_module_details (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    module_detail_id bigint NOT NULL,
    created_by bigint,
    created timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    modified_at timestamp with time zone
);


--
-- Name: role_module_details_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.role_module_details_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: role_module_details_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.role_module_details_id_seq OWNED BY public.role_module_details.id;


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    code character varying(100),
    name character varying(255) NOT NULL,
    description text,
    is_system_role boolean DEFAULT false NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified_by bigint,
    modified timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    modified_at timestamp with time zone,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: study_retention_delete_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.study_retention_delete_requests (
    id bigint NOT NULL,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    hospital_id bigint,
    study_id bigint,
    policy_id bigint,
    dicom_server_id bigint,
    modality_id bigint,
    status character varying(40) DEFAULT 'PENDING_APPROVAL'::character varying NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    near_expiry_at timestamp with time zone,
    study_instance_uid character varying(200),
    dicom_server_study_id character varying(255),
    accession_number character varying(100),
    reference_visit_code character varying(120),
    patient_mrn character varying(100),
    patient_name character varying(255),
    requested_by bigint,
    requested_at timestamp with time zone DEFAULT now() NOT NULL,
    approved_by bigint,
    approved_at timestamp with time zone,
    rejected_by bigint,
    rejected_at timestamp with time zone,
    deleted_at timestamp with time zone,
    decision_note text,
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    retain_until date,
    archive_after date,
    purge_after date,
    CONSTRAINT ck_study_retention_delete_requests_status CHECK (((status)::text = ANY (ARRAY[('PENDING_APPROVAL'::character varying)::text, ('APPROVED'::character varying)::text, ('DELETE_FAILED'::character varying)::text, ('DELETED'::character varying)::text, ('REJECTED'::character varying)::text, ('KEEP_PERMANENT'::character varying)::text])))
)
PARTITION BY RANGE (created_at);


--
-- Name: study_retention_delete_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.study_retention_delete_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: study_retention_delete_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.study_retention_delete_requests_id_seq OWNED BY public.study_retention_delete_requests.id;


--
-- Name: study_retention_delete_requests_default; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.study_retention_delete_requests_default (
    id bigint DEFAULT nextval('public.study_retention_delete_requests_id_seq'::regclass) CONSTRAINT study_retention_delete_requests_id_not_null NOT NULL,
    public_id uuid DEFAULT gen_random_uuid() CONSTRAINT study_retention_delete_requests_public_id_not_null NOT NULL,
    hospital_id bigint,
    study_id bigint,
    policy_id bigint,
    dicom_server_id bigint,
    modality_id bigint,
    status character varying(40) DEFAULT 'PENDING_APPROVAL'::character varying CONSTRAINT study_retention_delete_requests_status_not_null NOT NULL,
    expires_at timestamp with time zone CONSTRAINT study_retention_delete_requests_expires_at_not_null NOT NULL,
    near_expiry_at timestamp with time zone,
    study_instance_uid character varying(200),
    dicom_server_study_id character varying(255),
    accession_number character varying(100),
    reference_visit_code character varying(120),
    patient_mrn character varying(100),
    patient_name character varying(255),
    requested_by bigint,
    requested_at timestamp with time zone DEFAULT now() CONSTRAINT study_retention_delete_requests_requested_at_not_null NOT NULL,
    approved_by bigint,
    approved_at timestamp with time zone,
    rejected_by bigint,
    rejected_at timestamp with time zone,
    deleted_at timestamp with time zone,
    decision_note text,
    error_message text,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT study_retention_delete_requests_created_at_not_null NOT NULL,
    updated_at timestamp with time zone DEFAULT now() CONSTRAINT study_retention_delete_requests_updated_at_not_null NOT NULL,
    retain_until date,
    archive_after date,
    purge_after date,
    CONSTRAINT ck_study_retention_delete_requests_status CHECK (((status)::text = ANY (ARRAY[('PENDING_APPROVAL'::character varying)::text, ('APPROVED'::character varying)::text, ('DELETE_FAILED'::character varying)::text, ('DELETED'::character varying)::text, ('REJECTED'::character varying)::text, ('KEEP_PERMANENT'::character varying)::text])))
);


--
-- Name: study_retention_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.study_retention_policies (
    id bigint NOT NULL,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    hospital_id bigint,
    dicom_server_id bigint,
    modality_id bigint,
    retention_days integer NOT NULL,
    notify_before_days integer DEFAULT 14 NOT NULL,
    require_approval boolean DEFAULT true NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    notes text,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    modified_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    modified_at timestamp with time zone DEFAULT now() NOT NULL,
    retention_value integer NOT NULL,
    retention_unit character varying(20) NOT NULL,
    auto_delete boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_retention_days_positive CHECK ((retention_days > 0)),
    CONSTRAINT chk_retention_notify_before_nonnegative CHECK ((notify_before_days >= 0)),
    CONSTRAINT chk_retention_value_positive_final CHECK ((retention_value > 0)),
    CONSTRAINT ck_study_retention_policies_active CHECK ((is_active = ANY (ARRAY[1, 2]))),
    CONSTRAINT ck_study_retention_policies_notify_days CHECK (((notify_before_days >= 0) AND (notify_before_days <= 365))),
    CONSTRAINT ck_study_retention_policies_retention_days CHECK (((retention_days >= 1) AND (retention_days <= 3650))),
    CONSTRAINT ck_study_retention_policies_retention_unit CHECK (((retention_unit)::text = ANY (ARRAY[('DAY'::character varying)::text, ('MONTH'::character varying)::text, ('YEAR'::character varying)::text]))),
    CONSTRAINT ck_study_retention_policies_retention_value CHECK (((retention_value >= 1) AND (retention_value <= 3650)))
);


--
-- Name: COLUMN study_retention_policies.retention_value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.study_retention_policies.retention_value IS 'Retention amount configured by UI, for example 5 with retention_unit MONTH.';


--
-- Name: COLUMN study_retention_policies.retention_unit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.study_retention_policies.retention_unit IS 'Retention unit configured by UI. Supported values: DAY, MONTH, YEAR.';


--
-- Name: COLUMN study_retention_policies.auto_delete; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.study_retention_policies.auto_delete IS 'When true, expired matching studies can be deleted automatically in chunked retention cleanup without Super Admin approval.';


--
-- Name: study_retention_policies_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.study_retention_policies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: study_retention_policies_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.study_retention_policies_id_seq OWNED BY public.study_retention_policies.id;


--
-- Name: system_activities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_activities (
    id bigint NOT NULL,
    endpoint character varying(255) NOT NULL,
    module character varying(120),
    module_id bigint,
    description text,
    bug text,
    line_code bigint,
    browser character varying(120),
    operating_system character varying(120),
    ip character varying(80),
    host_name character varying(255),
    duration bigint,
    created_by bigint,
    created timestamp with time zone DEFAULT now() NOT NULL,
    status integer DEFAULT 1 NOT NULL,
    action character varying(120),
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone
)
PARTITION BY RANGE (created);


--
-- Name: system_activities_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_activities_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_activities_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_activities_id_seq OWNED BY public.system_activities.id;


--
-- Name: system_activities_default; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_activities_default (
    id bigint DEFAULT nextval('public.system_activities_id_seq'::regclass) CONSTRAINT system_activities_id_not_null NOT NULL,
    endpoint character varying(255) CONSTRAINT system_activities_endpoint_not_null NOT NULL,
    module character varying(120),
    module_id bigint,
    description text,
    bug text,
    line_code bigint,
    browser character varying(120),
    operating_system character varying(120),
    ip character varying(80),
    host_name character varying(255),
    duration bigint,
    created_by bigint,
    created timestamp with time zone DEFAULT now() CONSTRAINT system_activities_created_not_null NOT NULL,
    status integer DEFAULT 1 CONSTRAINT system_activities_status_not_null NOT NULL,
    action character varying(120),
    public_id uuid DEFAULT gen_random_uuid() CONSTRAINT system_activities_public_id_not_null NOT NULL,
    created_at timestamp with time zone
);


--
-- Name: user_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_groups (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified_by bigint,
    modified timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    modified_at timestamp with time zone
);


--
-- Name: user_groups_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_groups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_groups_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_groups_id_seq OWNED BY public.user_groups.id;


--
-- Name: user_hospitals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_hospitals (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    hospital_id bigint NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified_by bigint,
    modified timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    modified_at timestamp with time zone
);


--
-- Name: user_hospitals_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_hospitals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_hospitals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_hospitals_id_seq OWNED BY public.user_hospitals.id;


--
-- Name: user_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_logs (
    id bigint NOT NULL,
    user_id bigint,
    type character varying(120) NOT NULL,
    http_user_agent text,
    remote_addr character varying(80),
    created timestamp with time zone DEFAULT now() NOT NULL,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone
)
PARTITION BY RANGE (created);


--
-- Name: user_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_logs_id_seq OWNED BY public.user_logs.id;


--
-- Name: user_logs_default; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_logs_default (
    id bigint DEFAULT nextval('public.user_logs_id_seq'::regclass) CONSTRAINT user_logs_id_not_null NOT NULL,
    user_id bigint,
    type character varying(120) CONSTRAINT user_logs_type_not_null NOT NULL,
    http_user_agent text,
    remote_addr character varying(80),
    created timestamp with time zone DEFAULT now() CONSTRAINT user_logs_created_not_null NOT NULL,
    public_id uuid DEFAULT gen_random_uuid() CONSTRAINT user_logs_public_id_not_null NOT NULL,
    created_at timestamp with time zone
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    username character varying(100) NOT NULL,
    email character varying(255),
    password character varying(255) NOT NULL,
    first_name character varying(150),
    last_name character varying(150),
    telephone character varying(50),
    signature_photo text,
    user_type smallint DEFAULT 1 NOT NULL,
    expire_date date,
    account_locked boolean DEFAULT false NOT NULL,
    permission_version bigint DEFAULT 1 NOT NULL,
    is_active smallint DEFAULT 1 NOT NULL,
    created_by bigint,
    created timestamp with time zone DEFAULT now() NOT NULL,
    modified_by bigint,
    modified timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    modified_at timestamp with time zone,
    public_id uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: dicom_server_callback_log_default; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_server_callback_log ATTACH PARTITION public.dicom_server_callback_log_default DEFAULT;


--
-- Name: pacs_realtime_notification_events_default; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_realtime_notification_events ATTACH PARTITION public.pacs_realtime_notification_events_default DEFAULT;


--
-- Name: pacs_worklist_histories_default; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklist_histories ATTACH PARTITION public.pacs_worklist_histories_default DEFAULT;


--
-- Name: study_retention_delete_requests_default; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_delete_requests ATTACH PARTITION public.study_retention_delete_requests_default DEFAULT;


--
-- Name: system_activities_default; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_activities ATTACH PARTITION public.system_activities_default DEFAULT;


--
-- Name: user_logs_default; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_logs ATTACH PARTITION public.user_logs_default DEFAULT;


--
-- Name: countries id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries ALTER COLUMN id SET DEFAULT nextval('public.countries_id_seq'::regclass);


--
-- Name: dicom_server_callback_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_server_callback_log ALTER COLUMN id SET DEFAULT nextval('public.dicom_server_callback_log_id_seq'::regclass);


--
-- Name: dicom_server_unmatched_callback_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_server_unmatched_callback_log ALTER COLUMN id SET DEFAULT nextval('public.dicom_server_unmatched_callback_log_id_seq'::regclass);


--
-- Name: endpoint_permissions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.endpoint_permissions ALTER COLUMN id SET DEFAULT nextval('public.endpoint_permissions_id_seq'::regclass);


--
-- Name: hospital_dicom_machines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_machines ALTER COLUMN id SET DEFAULT nextval('public.hospital_dicom_machines_id_seq'::regclass);


--
-- Name: hospital_dicom_routing_configs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_routing_configs ALTER COLUMN id SET DEFAULT nextval('public.hospital_dicom_routing_configs_id_seq'::regclass);


--
-- Name: hospital_dicom_servers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_servers ALTER COLUMN id SET DEFAULT nextval('public.hospital_dicom_servers_id_seq'::regclass);


--
-- Name: hospital_modalities id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modalities ALTER COLUMN id SET DEFAULT nextval('public.hospital_modulights_id_seq'::regclass);


--
-- Name: hospital_modality_server_routes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modality_server_routes ALTER COLUMN id SET DEFAULT nextval('public.hospital_modulight_server_routes_id_seq'::regclass);


--
-- Name: hospitals id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospitals ALTER COLUMN id SET DEFAULT nextval('public.hospitals_id_seq'::regclass);


--
-- Name: modalities id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modalities ALTER COLUMN id SET DEFAULT nextval('public.modulights_id_seq'::regclass);


--
-- Name: module_details id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_details ALTER COLUMN id SET DEFAULT nextval('public.module_details_id_seq'::regclass);


--
-- Name: module_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_types ALTER COLUMN id SET DEFAULT nextval('public.module_types_id_seq'::regclass);


--
-- Name: modules id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modules ALTER COLUMN id SET DEFAULT nextval('public.modules_id_seq'::regclass);


--
-- Name: oauth2_clients id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth2_clients ALTER COLUMN id SET DEFAULT nextval('public.oauth2_clients_id_seq'::regclass);


--
-- Name: pacs_patient_sequences id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_patient_sequences ALTER COLUMN id SET DEFAULT nextval('public.pacs_patient_sequences_id_seq'::regclass);


--
-- Name: pacs_realtime_notification_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_realtime_notification_events ALTER COLUMN id SET DEFAULT nextval('public.pacs_realtime_notification_events_id_seq'::regclass);


--
-- Name: pacs_result_images id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_images ALTER COLUMN id SET DEFAULT nextval('public.pacs_result_images_id_seq'::regclass);


--
-- Name: pacs_result_templates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_templates ALTER COLUMN id SET DEFAULT nextval('public.pacs_result_templates_id_seq'::regclass);


--
-- Name: pacs_result_versions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_versions ALTER COLUMN id SET DEFAULT nextval('public.pacs_result_versions_id_seq'::regclass);


--
-- Name: pacs_results id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_results ALTER COLUMN id SET DEFAULT nextval('public.pacs_results_id_seq'::regclass);


--
-- Name: pacs_studies id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_studies ALTER COLUMN id SET DEFAULT nextval('public.pacs_studies_id_seq'::regclass);


--
-- Name: pacs_viewer_states id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_viewer_states ALTER COLUMN id SET DEFAULT nextval('public.pacs_viewer_states_id_seq'::regclass);


--
-- Name: pacs_visit_sequences id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_visit_sequences ALTER COLUMN id SET DEFAULT nextval('public.pacs_visit_sequences_id_seq'::regclass);


--
-- Name: pacs_worklist_histories id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklist_histories ALTER COLUMN id SET DEFAULT nextval('public.pacs_patient_queue_histories_id_seq'::regclass);


--
-- Name: pacs_worklist_study_links id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklist_study_links ALTER COLUMN id SET DEFAULT nextval('public.pacs_queue_study_links_id_seq'::regclass);


--
-- Name: pacs_worklists id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklists ALTER COLUMN id SET DEFAULT nextval('public.pacs_patient_queue_id_seq'::regclass);


--
-- Name: partition_maintenance_configs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partition_maintenance_configs ALTER COLUMN id SET DEFAULT nextval('public.partition_maintenance_configs_id_seq'::regclass);


--
-- Name: patients id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patients ALTER COLUMN id SET DEFAULT nextval('public.patients_id_seq'::regclass);


--
-- Name: refresh_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens ALTER COLUMN id SET DEFAULT nextval('public.refresh_tokens_id_seq'::regclass);


--
-- Name: revoked_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revoked_tokens ALTER COLUMN id SET DEFAULT nextval('public.revoked_tokens_id_seq'::regclass);


--
-- Name: role_module_details id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_module_details ALTER COLUMN id SET DEFAULT nextval('public.role_module_details_id_seq'::regclass);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: study_retention_delete_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_delete_requests ALTER COLUMN id SET DEFAULT nextval('public.study_retention_delete_requests_id_seq'::regclass);


--
-- Name: study_retention_policies id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_policies ALTER COLUMN id SET DEFAULT nextval('public.study_retention_policies_id_seq'::regclass);


--
-- Name: system_activities id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_activities ALTER COLUMN id SET DEFAULT nextval('public.system_activities_id_seq'::regclass);


--
-- Name: user_groups id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_groups ALTER COLUMN id SET DEFAULT nextval('public.user_groups_id_seq'::regclass);


--
-- Name: user_hospitals id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_hospitals ALTER COLUMN id SET DEFAULT nextval('public.user_hospitals_id_seq'::regclass);


--
-- Name: user_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_logs ALTER COLUMN id SET DEFAULT nextval('public.user_logs_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Data for Name: countries; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.countries (id, name, status, is_active, created, modified) FROM stdin;
1	Singapore	1	1	2026-05-10 23:13:03.939118+07	\N
2	Cambodia	1	1	2026-05-10 23:13:03.939118+07	\N
3	Viet Nam	1	1	2026-05-10 23:13:03.939118+07	\N
4	Lao PDR	1	1	2026-05-10 23:13:03.939118+07	\N
\.


--
-- Data for Name: dicom_server_callback_log_default; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.dicom_server_callback_log_default (id, event, payload, success, error_message, warning_message, received_at, created_at, accession_number, dicom_server_study_id, dicom_server_patient_id, dicom_server_series_ids, hospital_id, dicom_server_id, dedupe_key, payload_sha256, attempt_count, last_received_at) FROM stdin;
\.


--
-- Data for Name: dicom_server_unmatched_callback_log; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.dicom_server_unmatched_callback_log (id, public_id, original_callback_log_id, dicom_server_id, dedupe_key, payload_sha256, event, accession_number, dicom_server_study_id, dicom_server_patient_id, dicom_server_series_ids, payload, success, error_message, warning_message, received_at, last_received_at, attempt_count, created_at) FROM stdin;
\.


--
-- Data for Name: endpoint_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.endpoint_permissions (id, http_method, endpoint_pattern, permission_code, is_active, created, required_scope) FROM stdin;
223	POST	/worklist/worklist-find	pacs.worklist.view	1	2026-05-23 08:58:37.319323+07	pacs.api
224	POST	/worklist/worklist-update	pacs.worklist.assign	1	2026-05-23 08:58:37.319323+07	pacs.api
226	POST	/worklist/worklist-sync-result	pacs.worklist.receive	1	2026-05-23 08:58:37.319323+07	pacs.api
228	GET	/worklist/*/viewer-info	pacs.worklist.view_study	1	2026-05-28 00:16:46.97737+07	pacs.api
229	POST	/dicom-routing/dicom-routing-build-config/*	dicom.routing.view	1	2026-05-30 10:46:18.535345+07	pacs.api
230	POST	/worklist/worklist-machine-routes	pacs.worklist.send	1	2026-05-30 15:22:09.821922+07	pacs.api
236	POST	/dicom-machine/dicom-machine-list	dicom.machine.view	1	2026-05-30 21:53:37.133411+07	pacs.api
237	POST	/dicom-machine/dicom-machine-find/*	dicom.machine.view	1	2026-05-30 21:53:37.133411+07	pacs.api
238	POST	/dicom-machine/dicom-machine-create	dicom.machine.add	1	2026-05-30 21:53:37.133411+07	pacs.api
239	POST	/dicom-machine/dicom-machine-update	dicom.machine.edit	1	2026-05-30 21:53:37.133411+07	pacs.api
240	POST	/dicom-machine/dicom-machine-delete/*	dicom.machine.delete	1	2026-05-30 21:53:37.133411+07	pacs.api
44	POST	/hospital/hospital-list	hospital.view	1	2026-05-10 23:13:02.230335+07	pacs.api
80	POST	/user/list	user.view	0	2026-05-10 23:13:06.270649+07	user.read
64	POST	/modulight/modulight-list	modulight.view	1	2026-05-10 23:13:04.989306+07	\N
65	POST	/modulight/modulight-find/*	modulight.view	1	2026-05-10 23:13:04.989306+07	\N
66	POST	/modulight/modulight-create	modulight.add	1	2026-05-10 23:13:04.989306+07	\N
67	POST	/modulight/modulight-update	modulight.edit	1	2026-05-10 23:13:04.989306+07	\N
68	POST	/modulight/modulight-delete/*	modulight.delete	1	2026-05-10 23:13:04.989306+07	\N
69	POST	/hospital-modulight	hospital.modulight.view	1	2026-05-10 23:13:04.989306+07	\N
81	POST	/user/find/*	user.view	1	2026-05-10 23:13:06.270649+07	user.read
82	POST	/user/add	user.add	0	2026-05-10 23:13:06.270649+07	user.write
104	POST	/role/role-menu	role.view	1	2026-05-19 21:33:36.980341+07	user.read
58	POST	/study/study-assign	pacs.study.assign	0	2026-05-10 23:13:02.230335+07	pacs.study.read
86	POST	/role/find/*	role.view	2	2026-05-10 23:13:06.270649+07	user.read
83	POST	/user/update	user.edit	0	2026-05-10 23:13:06.270649+07	user.write
84	POST	/user/delete/*	user.delete	0	2026-05-10 23:13:06.270649+07	user.write
36	POST	/role/role-create	role.add	0	2026-05-10 23:13:02.230335+07	user.write
85	POST	/role/list	role.view	0	2026-05-10 23:13:06.270649+07	user.read
42	POST	/module-list	role.assign_permission	0	2026-05-10 23:13:02.230335+07	user.write
43	POST	/module-detail-list	role.assign_permission	0	2026-05-10 23:13:02.230335+07	user.write
41	POST	/module-type-list	role.assign_permission	0	2026-05-10 23:13:02.230335+07	user.write
45	POST	/hospital/hospital-find/*	hospital.view	1	2026-05-10 23:13:02.230335+07	pacs.api
46	POST	/hospital/hospital-create	hospital.add	1	2026-05-10 23:13:02.230335+07	user.write
47	POST	/hospital/hospital-update	hospital.edit	1	2026-05-10 23:13:02.230335+07	user.write
92	POST	/module-type/find/*	role.assign_permission	1	2026-05-10 23:13:06.270649+07	user.write
227	GET	/worklist/worklist-view-study-preview/*	pacs.worklist.view_study	1	2026-05-23 10:57:38.30198+07	pacs.api
25	POST	/worklist/worklist-list	pacs.worklist.view	1	2026-05-10 23:13:02.051113+07	pacs.api
75	POST	/worklist/worklist-assign	pacs.worklist.assign	1	2026-05-10 23:13:05.380935+07	pacs.api
76	POST	/worklist/worklist-send-to-pacs	pacs.worklist.send	1	2026-05-10 23:13:05.380935+07	pacs.api
77	POST	/worklist/worklist-received-study	pacs.worklist.receive	1	2026-05-10 23:13:05.380935+07	pacs.api
78	POST	/worklist/worklist-view-study	pacs.worklist.view_study	1	2026-05-10 23:13:05.380935+07	pacs.api
26	POST	/worklist/worklist-return	pacs.worklist.return	1	2026-05-10 23:13:02.051113+07	pacs.api
54	POST	/worklist/worklist-cancel	pacs.worklist.cancel	1	2026-05-10 23:13:02.230335+07	pacs.api
114	POST	/user-group/user-group-role-find/*	role.view	1	2026-05-21 09:44:48.126712+07	user.read
117	POST	/user-group/user-group-role-delete/*	role.delete	1	2026-05-21 09:44:48.126712+07	user.write
231	POST	/dicom-routing/dicom-machine-list	pacs.dicom-routing.view	2	2026-05-30 16:42:37.530347+07	pacs.api
232	POST	/dicom-routing/dicom-machine-find/{id}	pacs.dicom-routing.view	2	2026-05-30 16:42:37.530347+07	pacs.api
140	POST	/role/user-group-list	role.view	1	2026-05-21 09:46:57.073574+07	user.read
233	POST	/dicom-routing/dicom-machine-create	pacs.dicom-routing.manage	2	2026-05-30 16:42:37.530347+07	pacs.api
234	POST	/dicom-routing/dicom-machine-update	pacs.dicom-routing.manage	2	2026-05-30 16:42:37.530347+07	pacs.api
235	POST	/dicom-routing/dicom-machine-delete/{id}	pacs.dicom-routing.manage	2	2026-05-30 16:42:37.530347+07	pacs.api
91	POST	/module-type/list	role.assign_permission	0	2026-05-10 23:13:06.270649+07	user.write
111	POST	/user-group/list	role.view	0	2026-05-21 09:44:48.079124+07	user.read
112	POST	/user-group/add	role.add	0	2026-05-21 09:44:48.079124+07	user.write
113	POST	/user-group/user-group-role-list	role.view	0	2026-05-21 09:44:48.126712+07	user.read
115	POST	/user-group/user-group-role-add	role.add	0	2026-05-21 09:44:48.126712+07	user.write
116	POST	/user-group/user-group-role-update	role.edit	0	2026-05-21 09:44:48.126712+07	user.write
90	POST	/role/menu	role.view	0	2026-05-10 23:13:06.270649+07	user.read
87	POST	/role/add	role.add	0	2026-05-10 23:13:06.270649+07	user.write
88	POST	/role/update	role.edit	0	2026-05-10 23:13:06.270649+07	user.write
89	POST	/role/delete/*	role.delete	0	2026-05-10 23:13:06.270649+07	user.write
105	POST	/role/user-groupl-list	role.view	0	2026-05-19 21:38:47.362047+07	user.read
143	POST	/dropdown/dropdown-nationality	pacs.patient.view	1	2026-05-21 09:46:57.143808+07	pacs.patient.read
144	POST	/dropdown/dropdown-hospital	hospital.view	1	2026-05-21 09:46:57.143808+07	pacs.api
147	POST	/dropdown/dropdown-modality	modality.view	1	2026-05-21 09:46:57.143808+07	pacs.api
148	POST	/dropdown/dropdown-user-group-member	user.view	1	2026-05-21 09:46:57.143808+07	user.read
149	POST	/dropdown/dropdown-user	user.view	1	2026-05-21 09:46:57.143808+07	user.read
150	POST	/dropdown/dropdown-patient	pacs.patient.view	1	2026-05-21 09:46:57.143808+07	pacs.patient.read
151	POST	/dropdown/dropdown-user-group	role.view	1	2026-05-21 09:46:57.143808+07	user.read
156	POST	/hospital-modality	hospital.modality.view	1	2026-05-21 09:46:57.143808+07	pacs.api
157	POST	/modality/modality-list	modality.view	1	2026-05-21 09:46:57.143808+07	pacs.api
158	POST	/modality/modality-find/*	modality.view	1	2026-05-21 09:46:57.143808+07	pacs.api
159	POST	/modality/modality-create	modality.add	1	2026-05-21 09:46:57.143808+07	user.write
160	POST	/modality/modality-update	modality.edit	1	2026-05-21 09:46:57.143808+07	user.write
161	POST	/modality/modality-delete/*	modality.delete	1	2026-05-21 09:46:57.143808+07	user.write
106	POST	/module-type/module-type-list	role.assign_permission	1	2026-05-19 21:54:57.205862+07	user.write
39	POST	/permission/permission-tree	role.assign_permission	1	2026-05-10 23:13:02.230335+07	user.write
40	POST	/permission/permission-save-role-permissions	role.assign_permission	1	2026-05-10 23:13:02.230335+07	user.write
23	POST	/patient/patient-list	pacs.patient.view	1	2026-05-10 23:13:02.051113+07	pacs.patient.read
49	POST	/patient/patient-find/*	pacs.patient.view	1	2026-05-10 23:13:02.230335+07	pacs.patient.read
24	POST	/patient/patient-create	pacs.patient.create	1	2026-05-10 23:13:02.051113+07	pacs.patient.write
51	POST	/patient/patient-update	pacs.patient.edit	1	2026-05-10 23:13:02.230335+07	pacs.patient.write
27	POST	/study/study-list	pacs.study.view	1	2026-05-10 23:13:02.051113+07	pacs.study.read
57	POST	/study/study-find/*	pacs.study.view	1	2026-05-10 23:13:02.230335+07	pacs.study.read
194	POST	/dicom-server/dicom-server-list	dicom.server.view	1	2026-05-21 09:46:57.143808+07	pacs.api
195	POST	/dicom-server/dicom-server-find/*	dicom.server.view	1	2026-05-21 09:46:57.143808+07	pacs.api
196	POST	/dicom-server/dicom-server-create	dicom.server.add	1	2026-05-21 09:46:57.143808+07	pacs.api
197	POST	/dicom-server/dicom-server-update	dicom.server.edit	1	2026-05-21 09:46:57.143808+07	pacs.api
198	POST	/dicom-server/dicom-server-delete/*	dicom.server.delete	1	2026-05-21 09:46:57.143808+07	pacs.api
199	POST	/dicom-routing/dicom-routing-list	dicom.routing.view	1	2026-05-21 09:46:57.143808+07	pacs.api
200	POST	/dicom-routing/dicom-routing-find/*	dicom.routing.view	1	2026-05-21 09:46:57.143808+07	pacs.api
201	POST	/dicom-routing/dicom-routing-create	dicom.routing.add	1	2026-05-21 09:46:57.143808+07	pacs.api
202	POST	/dicom-routing/dicom-routing-update	dicom.routing.edit	1	2026-05-21 09:46:57.143808+07	pacs.api
203	POST	/dicom-routing/dicom-routing-delete/*	dicom.routing.delete	1	2026-05-21 09:46:57.143808+07	pacs.api
204	GET	/file/file-upload/*	file.view	1	2026-05-21 09:46:57.143808+07	pacs.api
205	POST	/file/file-upload	file.add	1	2026-05-21 09:46:57.143808+07	pacs.api
206	DELETE	/file/file-delete	file.delete	1	2026-05-21 09:46:57.143808+07	pacs.api
207	POST	/system-activity/system-activity-list	system.activity.view	1	2026-05-21 09:46:57.143808+07	user.read
208	POST	/system-activity/system-activity-find/*	system.activity.view	1	2026-05-21 09:46:57.143808+07	user.read
209	POST	/report/user-log/user-log-list	report.user_log.view	1	2026-05-21 09:46:57.143808+07	user.read
210	POST	/report/user-log/user-log-find/*	report.user_log.view	1	2026-05-21 09:46:57.143808+07	user.read
15	POST	/user/user-list	user.view	1	2026-05-10 23:13:02.051113+07	user.read
30	POST	/user/user-find/*	user.view	1	2026-05-10 23:13:02.230335+07	user.read
31	POST	/user/user-create	user.add	1	2026-05-10 23:13:02.230335+07	user.write
32	POST	/user/user-update	user.edit	1	2026-05-10 23:13:02.230335+07	user.write
33	POST	/user/user-delete/*	user.delete	1	2026-05-10 23:13:02.230335+07	user.write
216	POST	/user/user-change-password	user.edit	1	2026-05-21 09:46:57.143808+07	user.write
34	POST	/role/role-list	role.view	1	2026-05-10 23:13:02.230335+07	user.read
35	POST	/role/role-find/*	role.view	1	2026-05-10 23:13:02.230335+07	user.read
101	POST	/role/role-add	role.add	1	2026-05-19 21:33:36.980341+07	user.write
37	POST	/role/role-update	role.edit	1	2026-05-10 23:13:02.230335+07	user.write
38	POST	/role/role-delete/*	role.delete	1	2026-05-10 23:13:02.230335+07	user.write
258	POST	/dicom-uploads	pacs.study.upload	1	2026-06-14 08:43:31.80903+07	pacs.study.read
242	POST	/notification/notification-list	pacs.worklist.view	1	2026-06-03 22:43:16.068726+07	pacs.api
241	POST	/notification/notification-list	system.activity.view	2	2026-06-03 22:05:13.606458+07	user.read
243	POST	/worklist/worklist-route-availability	pacs.worklist.view	1	2026-06-04 14:44:23.426999+07	pacs.api
244	POST	/dashboard/dashboard-overview	home.view	1	2026-06-04 21:39:19.489654+07	pacs.api
245	POST	/user/user-group-list	role.view	1	2026-06-04 21:44:10.825399+07	user.read
246	POST	/dropdown/dropdown-dicom-server	dicom.server.view	1	2026-06-04 21:44:10.825399+07	pacs.api
247	POST	/worklist/worklist-routed-modality-list	pacs.worklist.view	1	2026-06-06 15:03:00.993067+07	pacs.api
248	POST	/pacs-result-template/pacs-result-template-list	pacs.result.template.view	1	2026-06-07 15:37:33.953806+07	pacs.api
249	POST	/pacs-result-template/pacs-result-template-find/*	pacs.result.template.view	1	2026-06-07 15:37:33.953806+07	pacs.api
250	POST	/pacs-result-template/pacs-result-template-create	pacs.result.template.add	1	2026-06-07 15:37:33.953806+07	pacs.api
251	POST	/pacs-result-template/pacs-result-template-update	pacs.result.template.edit	1	2026-06-07 15:37:33.953806+07	pacs.api
252	POST	/pacs-result-template/pacs-result-template-delete/*	pacs.result.template.delete	1	2026-06-07 15:37:33.953806+07	pacs.api
253	POST	/dicom-server/dicom-server-health-list	dicom.server.view	1	2026-06-09 22:18:08.03978+07	pacs.api
254	POST	/dicom-server/dicom-server-health-summary	dicom.server.view	1	2026-06-09 23:57:37.306672+07	pacs.api
255	POST	/dicom-server/dicom-server-health-settings-get	dicom.server.view	1	2026-06-09 23:57:37.306672+07	pacs.api
256	POST	/dicom-server/dicom-server-health-settings-update	dicom.server.edit	1	2026-06-09 23:57:37.306672+07	pacs.api
257	POST	/dropdown/dropdown-modality-catalog	modality.view	1	2026-06-11 20:09:35.810412+07	pacs.api
259	GET	/study/*/viewer-info	pacs.study.view	1	2026-06-14 08:43:31.80903+07	pacs.study.read
260	POST	/study-retention/policy-list	study.retention.policy.view	1	2026-06-14 18:15:08.769886+07	pacs.api
261	POST	/study-retention/policy-save	study.retention.policy.edit	1	2026-06-14 18:15:08.769886+07	pacs.api
262	POST	/study-retention/policy-delete/*	study.retention.policy.delete	1	2026-06-14 18:15:08.769886+07	pacs.api
263	POST	/study-retention/review-list	study.retention.approval.view	1	2026-06-14 18:15:08.769886+07	pacs.api
264	POST	/study-retention/summary	study.retention.approval.view	1	2026-06-14 18:15:08.769886+07	pacs.api
265	POST	/study-retention/approve-delete/*	study.retention.approval.approve	1	2026-06-14 18:15:08.769886+07	pacs.api
266	POST	/study-retention/reject-delete/*	study.retention.approval.approve	1	2026-06-14 18:15:08.769886+07	pacs.api
267	POST	/study-retention/bulk-delete	study.retention.approval.approve	1	2026-06-15 10:32:15.434488+07	pacs.api
268	POST	/study-retention/auto-delete-run	study.retention.approval.approve	1	2026-06-15 10:32:15.434488+07	pacs.api
269	GET	/notification/notification-stream	pacs.worklist.view	1	2026-06-15 20:30:10.557779+07	pacs.api
270	POST	/study-retention/policy-find/*	study.retention.policy.view	1	2026-06-17 00:40:55.518302+07	pacs.api
272	POST	/dicom-uploads/chunk/init	pacs.study.upload	1	2026-06-19 15:00:57.743363+07	pacs.study.read
273	POST	/dicom-uploads/chunk/*	pacs.study.upload	1	2026-06-19 15:00:57.743363+07	pacs.study.read
274	GET	/dicom-uploads/chunk/*/status	pacs.study.upload	1	2026-06-19 15:00:57.743363+07	pacs.study.read
275	POST	/dicom-uploads/chunk/*/complete	pacs.study.upload	1	2026-06-19 15:00:57.743363+07	pacs.study.read
276	DELETE	/dicom-uploads/chunk/*/abort	pacs.study.upload	1	2026-06-19 15:00:57.743363+07	pacs.study.read
277	GET	/dicom-routing/dicom-routing-base-image-download	dicom.routing.view	1	2026-06-23 20:04:17.118399+07	pacs.api
278	GET	/dicom-routing/dicom-routing-build-config-download/*	dicom.routing.view	1	2026-06-23 20:31:40.457298+07	pacs.api
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	60	<< Flyway Baseline >>	BASELINE	<< Flyway Baseline >>	\N	pacs_app_local_rw	2026-05-18 22:47:36.170944	0	t
2	61	large scale routing and search indexes	SQL	V61__large_scale_routing_and_search_indexes.sql	-587723066	pacs_app_local_rw	2026-05-18 22:51:41.800023	44	t
3	62	queue dicom server hospital integrity	SQL	V62__queue_dicom_server_hospital_integrity.sql	305149881	pacs_app_local_rw	2026-05-18 22:51:41.8931	28	t
4	63	add user group unified endpoint permissions	SQL	V63__add_user_group_unified_endpoint_permissions.sql	1271612677	pacs_app_local_rw	2026-05-21 09:44:48.057682	27	t
5	64	add user group role alias endpoint permissions	SQL	V64__add_user_group_role_alias_endpoint_permissions.sql	1166563068	pacs_app_local_rw	2026-05-21 09:44:48.118665	4	t
6	65	rename role endpoints to emr style	SQL	V65__rename_role_endpoints_to_emr_style.sql	-1354797446	pacs_app_local_rw	2026-05-21 09:44:48.136725	7	t
7	66	remove hospital scope from user groups and role permissions	SQL	V66__remove_hospital_scope_from_user_groups_and_role_permissions.sql	-9688152	pacs_app_local_rw	2026-05-21 09:46:56.765463	113	t
8	67	rename role endpoints to role alias style	SQL	V67__rename_role_endpoints_to_role_alias_style.sql	1838246677	pacs_app_local_rw	2026-05-21 09:46:56.911182	3	t
9	68	deduplicate role endpoint permissions	SQL	V68__deduplicate_role_endpoint_permissions.sql	2008864157	pacs_app_local_rw	2026-05-21 09:46:56.925521	3	t
10	69	add role user groupl list endpoint permission	SQL	V69__add_role_user_groupl_list_endpoint_permission.sql	-1097903161	pacs_app_local_rw	2026-05-21 09:46:56.941939	2	t
11	70	remove module controller endpoint permissions	SQL	V70__remove_module_controller_endpoint_permissions.sql	-355283939	pacs_app_local_rw	2026-05-21 09:46:56.95361	4	t
12	71	deduplicate module type list endpoint	SQL	V71__deduplicate_module_type_list_endpoint.sql	-1897511177	pacs_app_local_rw	2026-05-21 09:46:56.965183	4	t
13	72	add module type menu group fields	SQL	V72__add_module_type_menu_group_fields.sql	-394435602	pacs_app_local_rw	2026-05-21 09:46:56.981445	9	t
14	73	normalize menu group code spelling	SQL	V73__normalize_menu_group_code_spelling.sql	864033098	pacs_app_local_rw	2026-05-21 09:46:57.001775	3	t
15	74	remove hospital scope from roles and user groups	SQL	V74__remove_hospital_scope_from_roles_and_user_groups.sql	1892571117	pacs_app_local_rw	2026-05-21 09:46:57.015204	20	t
16	75	rename module type list endpoint to nested path	SQL	V75__rename_module_type_list_endpoint_to_nested_path.sql	-815144688	pacs_app_local_rw	2026-05-21 09:46:57.04541	3	t
17	76	rename system activities act to action	SQL	V76__rename_system_activities_act_to_action.sql	166269071	pacs_app_local_rw	2026-05-21 09:46:57.055545	8	t
18	77	add role user group list endpoint permission	SQL	V77__add_role_user_group_list_endpoint_permission.sql	-1460602683	pacs_app_local_rw	2026-05-21 09:46:57.070774	2	t
19	78	migrate role user groupl to user group list permission	SQL	V78__migrate_role_user_groupl_to_user_group_list_permission.sql	-885450914	pacs_app_local_rw	2026-05-21 09:46:57.079625	3	t
20	79	cleanup unused legacy endpoint permissions	SQL	V79__cleanup_unused_legacy_endpoint_permissions.sql	-831947997	pacs_app_local_rw	2026-05-21 09:46:57.090824	3	t
21	80	retire study assign endpoint permission	SQL	V80__retire_study_assign_endpoint_permission.sql	126446655	pacs_app_local_rw	2026-05-21 09:46:57.103229	9	t
22	81	ensure system activities action column	SQL	V81__ensure_system_activities_action_column.sql	-187111155	pacs_app_local_rw	2026-05-21 09:46:57.120213	7	t
23	82	standardize role permission catalog	SQL	V82__standardize_role_permission_catalog.sql	2011529516	pacs_app_local_rw	2026-05-21 09:46:57.135069	25	t
24	83	enforce module type menu group metadata	SQL	V83__enforce_module_type_menu_group_metadata.sql	640067256	pacs_app_local_rw	2026-05-21 09:46:57.174035	8	t
25	84	split role permission modules	SQL	V84__split_role_permission_modules.sql	-939859932	pacs_app_local_rw	2026-05-21 09:46:57.189028	10	t
26	85	remove route priority weight default	SQL	V85__remove_route_priority_weight_default.sql	2039007356	pacs_app_local_rw	2026-05-21 10:24:35.828745	76	t
27	86	add queue worklist schedule fields	SQL	V86__add_queue_worklist_schedule_fields.sql	-1582675436	pacs_app_local_rw	2026-05-22 11:01:28.991418	31	t
113	172	add dicom upload flow	SQL	V172__add_dicom_upload_flow.sql	-1175855039	pacs_app_local_rw	2026-06-14 08:43:31.787775	101	t
29	88	add queue combined endpoint permissions	SQL	V88__add_queue_combined_endpoint_permissions.sql	-1561696764	pacs_app_local_rw	2026-05-23 08:58:37.314896	8	t
30	89	remove queue start endpoint permission	SQL	V89__remove_queue_start_endpoint_permission.sql	-8919917	pacs_app_local_rw	2026-05-23 10:15:38.030096	22	t
31	90	add queue view study preview permission	SQL	V90__add_queue_view_study_preview_permission.sql	-8732024	pacs_app_local_rw	2026-05-23 10:57:38.28785	11	t
116	175	normalize study instance count	SQL	V175__normalize_study_instance_count.sql	374049864	pacs_app_local_rw	2026-06-14 10:57:27.561878	70	t
119	178	replace fixed retention expiry with duration policy	SQL	V178__replace_fixed_retention_expiry_with_duration_policy.sql	-339592287	pacs_app_local_rw	2026-06-14 19:12:50.529893	71	t
120	179	add auto delete to study retention	SQL	V179__add_auto_delete_to_study_retention.sql	-1996285770	pacs_app_local_rw	2026-06-15 10:32:15.404426	91	t
121	180	add dicom patient hn and institution	SQL	V180__add_dicom_patient_hn_and_institution.sql	2128181910	pacs_app_local_rw	2026-06-15 19:49:39.976609	30	t
36	95	group dicom routing by hospital config	SQL	V95__group_dicom_routing_by_hospital_config.sql	1409361537	pacs_app_local_rw	2026-05-25 09:35:57.825378	116	t
123	182	add study retention policy find permission	SQL	V182__add_study_retention_policy_find_permission.sql	-937077199	pacs_app_local_rw	2026-06-17 00:40:55.500183	44	t
124	183	repair study retention policy find permission	SQL	V183__repair_study_retention_policy_find_permission.sql	662089012	pacs_app_local_rw	2026-06-17 00:40:55.57391	11	t
125	184	lock hospital identity after dicom package build	SQL	V184__lock_hospital_identity_after_dicom_package_build.sql	-1665112357	pacs_app_local_rw	2026-06-17 16:02:09.895357	69	t
40	99	remove service module	SQL	V99__remove_service_module.sql	-324790453	pacs_app_local_rw	2026-05-26 17:42:43.27936	95	t
126	185	scope identifiers and remove redundant indexes	SQL	V185__scope_identifiers_and_remove_redundant_indexes.sql	-158810559	pacs_app_local_rw	2026-06-18 18:22:41.101434	55	t
43	102	remove unused schema residue	SQL	V102__remove_unused_schema_residue.sql	-732045127	pacs_app_local_rw	2026-05-26 20:29:54.157864	26	t
44	103	hospital scoped patient codes	SQL	V103__hospital_scoped_patient_codes.sql	1443808817	pacs_app_local_rw	2026-05-26 22:03:22.258648	47	t
45	104	optimize operational queue list	SQL	V104__optimize_operational_queue_list.sql	-183579886	pacs_app_local_rw	2026-05-27 00:29:22.488708	21	t
46	105	normalize queue and study status to four state model	SQL	V105__normalize_queue_and_study_status_to_four_state_model.sql	1982285052	pacs_app_local_rw	2026-05-27 19:02:30.195853	80	t
47	106	separate queue and study status models	SQL	V106__separate_queue_and_study_status_models.sql	-497763880	pacs_app_local_rw	2026-05-27 22:33:01.29624	70	t
48	107	add queue viewer info permission	SQL	V107__add_queue_viewer_info_permission.sql	1748811877	pacs_app_local_rw	2026-05-28 00:16:46.964669	17	t
140	199	add pacs week cache	SQL	V199__add_pacs_week_cache.sql	-2023267914	pacs_app_local_rw	2026-06-18 22:48:22.528593	62	t
49	108	large data performance indexes	SQL	V108__large_data_performance_indexes.sql	1611804856	pacs_app_local_rw	2026-05-28 00:46:58.487563	803	t
51	110	drop unused legacy queue study columns	SQL	V110__drop_unused_legacy_queue_study_columns.sql	-1011706045	pacs_app_local_rw	2026-05-28 10:38:25.090701	57	t
52	111	drop legacy viewer proxy flow	SQL	V111__drop_legacy_viewer_proxy_flow.sql	512283052	pacs_app_local_rw	2026-05-28 11:07:40.892389	291	t
53	112	create standard pacs results	SQL	V112__create_standard_pacs_results.sql	-315065339	pacs_app_local_rw	2026-05-29 10:33:41.395681	91	t
54	113	rename queue to worklist	SQL	V113__rename_queue_to_worklist.sql	-102274433	pacs_app_local_rw	2026-05-29 19:47:43.964999	83	t
56	115	rename remaining worklist status index	SQL	V115__rename_remaining_worklist_status_index.sql	1639037119	pacs_app_local_rw	2026-05-29 20:23:44.996194	12	t
58	117	add dicom routing build config permission	SQL	V117__add_dicom_routing_build_config_permission.sql	-192199173	pacs_app_local_rw	2026-05-30 10:46:18.518726	24	t
59	118	support multi machine dicom routing	SQL	V118__support_multi_machine_dicom_routing.sql	337492186	pacs_app_local_rw	2026-05-30 15:22:09.802481	54	t
60	119	split dicom machine library from routing	SQL	V119__split_dicom_machine_library_from_routing.sql	1179486461	pacs_app_local_rw	2026-05-30 16:42:37.50654	112	t
61	120	remove modality description column	SQL	V120__remove_modality_description_column.sql	1583900636	pacs_app_local_rw	2026-05-30 21:12:25.353341	37	t
62	121	normalize dicom machine routing relation	SQL	V121__normalize_dicom_machine_routing_relation.sql	-1613210188	pacs_app_local_rw	2026-05-30 21:53:37.104224	148	t
63	122	remove dicom machine code	SQL	V122__remove_dicom_machine_code.sql	1735600045	pacs_app_local_rw	2026-05-30 22:04:04.576196	21	t
66	125	drop legacy worklist result storage	SQL	V125__drop_legacy_worklist_result_storage.sql	-619683960	pacs_app_local_rw	2026-05-31 00:43:55.499181	192	t
67	126	remove legacy worklist result permission aliases	SQL	V126__remove_legacy_worklist_result_permission_aliases.sql	2006800141	pacs_app_local_rw	2026-05-31 00:51:57.416039	10	t
68	127	remove copied dicom server from worklists	SQL	V127__remove_copied_dicom_server_from_worklists.sql	-774826020	pacs_app_local_rw	2026-05-31 01:04:21.341554	24	t
69	128	enforce dicom route integrity	SQL	V128__enforce_dicom_route_integrity.sql	-622819848	pacs_app_local_rw	2026-05-31 13:45:27.629919	44	t
114	173	add study modality relation	SQL	V173__add_study_modality_relation.sql	1735389754	pacs_app_local_rw	2026-06-14 09:43:18.258035	193	t
117	176	add study retention policy and approval	SQL	V176__add_study_retention_policy_and_approval.sql	2037377167	pacs_app_local_rw	2026-06-14 18:15:08.736114	306	t
75	134	add dicom server result proxy api key	SQL	V134__add_dicom_server_result_proxy_api_key.sql	-724384809	pacs_app_local_rw	2026-05-31 23:52:24.307119	57	t
122	181	add durable realtime notification events	SQL	V181__add_durable_realtime_notification_events.sql	-1990073004	pacs_app_local_rw	2026-06-15 20:30:10.541087	49	t
127	186	add large scale compatibility structure	SQL	V186__add_large_scale_compatibility_structure.sql	1208135841	pacs_app_local_rw	2026-06-18 18:22:41.121693	45	t
128	187	add large scale indexes	SQL	V187__add_large_scale_indexes.sql	-1053849618	pacs_app_local_rw	2026-06-18 18:22:41.252535	68	t
28	87	refactor queue status flow to dicom server lifecycle	SQL	V87__refactor_queue_status_flow_to_orthanc_lifecycle.sql	-1218765190	pacs_app_local_rw	2026-05-23 08:58:37.223101	65	t
32	91	add direct queue study link for large scale flow	SQL	V91__add_direct_queue_study_link_for_large_scale_flow.sql	-792612038	pacs_app_local_rw	2026-05-24 15:20:55.753416	63	t
33	92	backfill direct queue study links	SQL	V92__backfill_direct_queue_study_links.sql	-1525626094	pacs_app_local_rw	2026-05-24 15:23:26.132292	15	t
34	93	seed study archive from legacy received queues	SQL	V93__seed_study_archive_from_legacy_received_queues.sql	-126652423	pacs_app_local_rw	2026-05-24 15:25:37.925984	32	t
35	94	add public viewer and dicomweb urls to dicom servers	SQL	V94__add_public_viewer_and_dicomweb_urls_to_dicom_servers.sql	-214014627	pacs_app_local_rw	2026-05-24 20:40:46.969181	13	t
37	96	add dicom server callback log table	SQL	V96__add_orthanc_callback_log_table.sql	-1373205868	pacs_app_local_rw	2026-05-25 17:57:22.187494	98	t
38	97	add dicom port to dicom servers	SQL	V97__add_dicom_port_to_dicom_servers.sql	1637436504	pacs_app_local_rw	2026-05-26 15:27:15.964429	65	t
39	98	add dicom server config to dicom servers	SQL	V98__add_orthanc_config_to_dicom_servers.sql	-1951876738	pacs_app_local_rw	2026-05-26 16:04:57.106402	66	t
41	100	add machine modality fields to dicom routing	SQL	V100__add_machine_modality_fields_to_dicom_routing.sql	1404958888	pacs_app_local_rw	2026-05-26 19:29:30.543568	43	t
42	101	backfill route machine fields	SQL	V101__backfill_route_machine_fields.sql	271624134	pacs_app_local_rw	2026-05-26 19:43:02.228566	14	t
55	114	cleanup remaining queue object names	SQL	V114__cleanup_remaining_queue_object_names.sql	-1326676313	pacs_app_local_rw	2026-05-29 20:22:06.497833	97	t
57	116	optimize active worklist visibility	SQL	V116__optimize_active_worklist_visibility.sql	1789672518	pacs_app_local_rw	2026-05-30 09:11:15.161822	31	t
64	123	normalize study result reference data	SQL	V123__normalize_study_result_reference_data.sql	1448677786	pacs_app_local_rw	2026-05-30 23:55:38.185239	99	t
65	124	remove remaining duplicate pacs reference fields	SQL	V124__remove_remaining_duplicate_pacs_reference_fields.sql	-563524151	pacs_app_local_rw	2026-05-31 00:13:01.947362	90	t
70	129	store study dicom server reference	SQL	V129__store_study_dicom_server_reference.sql	863822849	pacs_app_local_rw	2026-05-31 14:13:27.93888	21	t
71	130	provision dicom server callback clients	SQL	V130__provision_dicom_server_callback_clients.sql	1291714056	pacs_app_local_rw	2026-05-31 15:07:36.810089	41	t
72	131	normalize dicom server external urls	SQL	V131__normalize_dicom_server_external_urls.sql	-1936521174	pacs_app_local_rw	2026-05-31 16:25:40.375546	54	t
50	109	large data exact filter indexes	SQL	V109__large_data_exact_filter_indexes.sql	237426387	pacs_app_local_rw	2026-05-28 10:20:14.214043	586	t
129	188	enforce hospital safe medical relations	SQL	V188__enforce_hospital_safe_medical_relations.sql	-551820926	pacs_app_local_rw	2026-06-18 18:22:41.26249	27	t
136	195	add monthly partition maintenance	SQL	V195__add_monthly_partition_maintenance.sql	-1409734197	pacs_app_local_rw	2026-06-18 21:22:23.393516	32	t
138	197	correct partition retention rules	SQL	V197__correct_partition_retention_rules.sql	1629416733	pacs_app_local_rw	2026-06-18 22:35:00.254221	283	t
141	200	resolve callback log hospital scope	SQL	V200__resolve_callback_log_hospital_scope.sql	-525701607	pacs_app_local_rw	2026-06-18 22:55:06.560966	106	t
142	201	add worklist week cache created by	SQL	V201__add_worklist_week_cache_created_by.sql	-1756977123	pacs_app_local_rw	2026-06-18 23:04:42.075093	20	t
143	202	complete partition cache and uuid hardening	SQL	V202__complete_partition_cache_and_uuid_hardening.sql	467207263	pacs_app_local_rw	2026-06-18 23:21:13.513642	91	t
144	203	fix policy partition dry run	SQL	V203__fix_policy_partition_dry_run.sql	-490842424	pacs_app_local_rw	2026-06-18 23:23:56.261908	8	t
73	132	secure dicom server http access defaults	SQL	V132__secure_orthanc_http_access_defaults.sql	443161665	pacs_app_local_rw	2026-05-31 17:00:49.567596	20	t
74	133	add dicom server callback api base url	SQL	V133__add_dicom_server_callback_api_base_url.sql	-1292083111	pacs_app_local_rw	2026-05-31 17:35:07.081276	26	t
76	135	document dicom server url roles	SQL	V135__document_dicom_server_url_roles.sql	-268647704	pacs_app_local_rw	2026-06-02 11:46:48.003622	22	t
77	136	document dicom server archive brand	SQL	V136__rename_archive_brand_to_udaya_pacs.sql	132959984	pacs_app_local_rw	2026-06-02 15:54:48.217676	118	t
78	137	normalize dicom server archive brand	SQL	V137__rename_udaya_pacs_to_udaya_dicom_server.sql	-7153308	pacs_app_local_rw	2026-06-02 16:31:10.18507	210	t
79	138	normalize dicom server db names	SQL	V138__normalize_dicom_server_db_names.sql	-53461212	pacs_app_local_rw	2026-06-03 08:54:06.44078	45	t
80	139	normalize visit code format	SQL	V139__normalize_visit_code_format.sql	2018993089	pacs_app_local_rw	2026-06-03 09:54:04.144575	36	t
115	174	add study instance count	SQL	V174__add_study_instance_count.sql	-1542753700	pacs_app_local_rw	2026-06-14 10:17:32.519385	84	t
81	140	normalize remaining dicom server db object names	SQL	V140__normalize_remaining_dicom_server_db_object_names.sql	1306089569	pacs_app_local_rw	2026-06-03 10:14:04.436305	8	t
118	177	add fixed expiry to study retention policies	SQL	V177__add_fixed_expiry_to_study_retention_policies.sql	1660439581	pacs_app_local_rw	2026-06-14 18:52:49.911866	48	t
82	141	enforce hospital visit code abbr token	SQL	V141__enforce_hospital_visit_code_abbr_token.sql	636541456	pacs_app_local_rw	2026-06-03 10:25:21.580758	14	t
83	142	remove dicom machine room field	SQL	V142__remove_dicom_machine_room_field.sql	57757194	pacs_app_local_rw	2026-06-03 10:52:03.171666	36	t
84	143	scope dicom routing config by destination server	SQL	V143__scope_dicom_routing_config_by_destination_server.sql	1555664311	pacs_app_local_rw	2026-06-03 11:31:29.69626	112	t
85	144	remove route level dicom server reference	SQL	V144__remove_route_level_dicom_server_reference.sql	1342994985	pacs_app_local_rw	2026-06-03 11:53:39.173903	86	t
86	145	add notification endpoint permission	SQL	V145__add_notification_endpoint_permission.sql	-2025303740	pacs_app_local_rw	2026-06-03 22:05:13.580024	34	t
87	146	scope notifications to worklist and study events	SQL	V146__scope_notifications_to_worklist_and_study_events.sql	1715530789	pacs_app_local_rw	2026-06-03 22:43:16.048585	54	t
88	147	derive dicom server urls from endpoint fields	SQL	V147__derive_dicom_server_urls_from_endpoint_fields.sql	1151348000	pacs_app_local_rw	2026-06-04 10:00:29.229496	37	t
89	148	add worklist route availability permission	SQL	V148__add_worklist_route_availability_permission.sql	-1504078486	pacs_app_local_rw	2026-06-04 14:44:23.410663	40	t
90	149	add dashboard overview endpoint permission	SQL	V149__add_dashboard_overview_endpoint_permission.sql	-399292342	pacs_app_local_rw	2026-06-04 21:39:19.471077	28	t
91	150	add missing smoke endpoint permissions	SQL	V150__add_missing_smoke_endpoint_permissions.sql	1306735321	pacs_app_local_rw	2026-06-04 21:44:10.810072	9	t
92	151	seed standard dicom modalities	SQL	V151__seed_standard_dicom_modalities.sql	-75102155	pacs_app_local_rw	2026-06-06 10:50:44.014817	56	t
93	152	add worklist routed modality permission	SQL	V152__add_worklist_routed_modality_permission.sql	-1515172396	pacs_app_local_rw	2026-06-06 15:03:00.9767	41	t
94	153	add public keys to pacs results	SQL	V153__add_public_keys_to_pacs_results.sql	693760995	pacs_app_local_rw	2026-06-06 22:32:15.068732	69	t
95	154	add public keys to core crud entities	SQL	V154__add_public_keys_to_core_crud_entities.sql	-564353079	pacs_app_local_rw	2026-06-07 07:34:54.336189	112	t
96	155	add public keys to admin catalog and audit	SQL	V155__add_public_keys_to_admin_catalog_and_audit.sql	-485711299	pacs_app_local_rw	2026-06-07 12:27:00.584082	29339	t
97	156	add public key to pacs result templates	SQL	V156__add_public_key_to_pacs_result_templates.sql	-307706967	pacs_app_local_rw	2026-06-07 12:53:10.02542	46	t
98	157	add pacs result template crud	SQL	V157__add_pacs_result_template_crud.sql	-551562996	pacs_app_local_rw	2026-06-07 15:37:33.929923	105	t
99	158	add hospital logo metadata	SQL	V158__add_hospital_logo_metadata.sql	-2132820067	pacs_app_local_rw	2026-06-07 21:11:09.135174	39	t
100	159	add dicom server health endpoint permission	SQL	V159__add_dicom_server_health_endpoint_permission.sql	863647127	pacs_app_local_rw	2026-06-09 22:18:08.015417	15	t
101	160	add public dicom server health check url	SQL	V160__add_public_dicom_server_health_check_url.sql	1503121411	pacs_app_local_rw	2026-06-09 22:18:08.061988	30	t
102	161	recreate system activity exact filter index	SQL	V161__recreate_system_activity_exact_filter_index.sql	1293804610	pacs_app_local_rw	2026-06-09 22:18:08.101795	358	t
103	162	add dicom server health settings	SQL	V162__add_dicom_server_health_settings.sql	-344987703	pacs_app_local_rw	2026-06-09 23:57:37.291933	51	t
104	163	add modality catalog dropdown permission	SQL	V163__add_modality_catalog_dropdown_permission.sql	782863536	pacs_app_local_rw	2026-06-11 20:09:35.797452	13	t
105	164	ensure primary dicom modalities	SQL	V164__ensure_primary_dicom_modalities.sql	586896533	pacs_app_local_rw	2026-06-11 20:09:35.831752	18	t
106	165	split patient name into first and last	SQL	V165__split_patient_name_into_first_and_last.sql	-1856799811	pacs_app_local_rw	2026-06-11 20:13:33.032837	47	t
107	166	allow multiple dicom callback clients	SQL	V166__allow_multiple_dicom_callback_clients.sql	46205230	pacs_app_local_rw	2026-06-12 00:11:55.307421	57	t
108	167	optimize join heavy mapper paths	SQL	V167__optimize_join_heavy_mapper_paths.sql	779557860	pacs_app_local_rw	2026-06-12 21:04:18.651477	15	t
109	168	add pacs viewer state storage	SQL	V168__add_pacs_viewer_state_storage.sql	-1219821672	pacs_app_local_rw	2026-06-12 23:13:03.677639	64	t
110	169	add typed pacs viewer segmentation state	SQL	V169__add_typed_pacs_viewer_segmentation_state.sql	-1063183706	pacs_app_local_rw	2026-06-12 23:47:00.934222	49	t
111	170	harden pacs viewer state integrity	SQL	V170__harden_pacs_viewer_state_integrity.sql	391069814	pacs_app_local_rw	2026-06-13 00:20:39.298784	85	t
112	171	optimize large pacs viewer state storage	SQL	V171__optimize_large_pacs_viewer_state_storage.sql	353195540	pacs_app_local_rw	2026-06-13 00:50:18.723079	109	t
130	189	enable pgcrypto and uuid defaults	SQL	V189__enable_pgcrypto_and_uuid_defaults.sql	-2048489509	pacs_app_local_rw	2026-06-18 19:39:12.558171	30	t
131	190	validate hospital safe constraints	SQL	V190__validate_hospital_safe_constraints.sql	-823650299	pacs_app_local_rw	2026-06-18 19:39:12.602642	3	t
132	191	trim redundant hot table indexes	SQL	V191__trim_redundant_hot_table_indexes.sql	-243219218	pacs_app_local_rw	2026-06-18 19:39:12.654483	36	t
133	192	partition append only log tables	SQL	V192__partition_append_only_log_tables.sql	361270560	pacs_app_local_rw	2026-06-18 19:39:12.66164	1983	t
135	194	final hot table index cleanup	SQL	V194__final_hot_table_index_cleanup.sql	-611527317	pacs_app_local_rw	2026-06-18 21:01:42.555882	363	t
137	196	hospital scope callbacks and trim final indexes	SQL	V196__hospital_scope_callbacks_and_trim_final_indexes.sql	982706272	pacs_app_local_rw	2026-06-18 21:46:20.867435	41	t
139	198	quiet partition child index maintenance	SQL	V198__quiet_partition_child_index_maintenance.sql	297806111	pacs_app_local_rw	2026-06-18 22:38:03.139907	7	t
145	204	optimize week cache triggers	SQL	V204__optimize_week_cache_triggers.sql	-1199033868	pacs_app_local_rw	2026-06-19 08:03:43.954575	10	t
146	205	finalize partition config and constraint cleanup	SQL	V205__finalize_partition_config_and_constraint_cleanup.sql	-670995353	pacs_app_local_rw	2026-06-19 08:42:31.86445	31	t
147	206	add dicom chunk upload endpoint permissions	SQL	V206__add_dicom_chunk_upload_endpoint_permissions.sql	1571121461	pacs_app_local_rw	2026-06-19 15:00:57.743363	15	t
134	193	final dev schema hardening	SQL	V193__final_dev_schema_hardening.sql	1700206063	pacs_app_local_rw	2026-06-18 20:57:00.518078	75	t
148	207	make study week cache idempotent	SQL	V207__make_study_week_cache_idempotent.sql	1791425468	pacs_app_local_rw	2026-06-23 00:45:37.214616	48	t
149	208	add dicom routing base image download permission	SQL	V208__add_dicom_routing_base_image_download_permission.sql	-2026338229	pacs_app_local_rw	2026-06-23 20:04:17.118399	43	t
150	209	add dicom routing config zip download permission	SQL	V209__add_dicom_routing_config_zip_download_permission.sql	470140459	pacs_app_local_rw	2026-06-23 20:31:40.457298	10	t
\.


--
-- Data for Name: hospital_dicom_machines; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.hospital_dicom_machines (id, hospital_id, modality_id, machine_name, machine_ae_title, machine_host, machine_port, is_active, created_by, modified_by, created_at, modified_at, public_id) FROM stdin;
1	1	1	UDAYA CR Room 1	UDAYA_CR01	127.0.0.1	10401	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	6180a2b9-5c8f-43a0-8fde-2990f74d6b65
2	1	2	UDAYA CT Room 1	UDAYA_CT01	127.0.0.1	10402	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	a0f6cfdc-8fce-499a-9139-c3f5298d41d1
3	1	3	UDAYA DR Room 1	UDAYA_DR01	127.0.0.1	10403	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	3ba79155-3d45-4e87-9670-0b6ff7ea3a27
4	1	4	UDAYA DX Room 1	UDAYA_DX01	127.0.0.1	10404	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	2068f8ef-41e8-4747-b397-08fca7dbc973
5	1	5	UDAYA MG Room 1	UDAYA_MG01	127.0.0.1	10405	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	65c299c9-8c63-41f6-b63f-e843ba11924f
6	1	6	UDAYA MR Room 1	UDAYA_MR01	127.0.0.1	10406	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	d782e055-d28e-482a-848a-8c3c75fa54df
7	1	7	UDAYA OT Room 1	UDAYA_OT01	127.0.0.1	10407	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	7bd2d102-a5f5-4f5d-99b0-b1a906b66b8a
8	1	8	UDAYA PT Room 1	UDAYA_PT01	127.0.0.1	10408	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	24b68d27-2b3e-49fe-9c56-0b7683d29955
9	1	9	UDAYA US Room 1	UDAYA_US01	127.0.0.1	10409	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	d402229c-f71f-43d7-b139-d32927cb65fb
10	1	10	UDAYA XA Room 1	UDAYA_XA01	127.0.0.1	10410	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	8c40d6d4-e247-4877-bd0d-5a550047c1f4
11	1	11	UDAYA XC Room 1	UDAYA_XC01	127.0.0.1	10411	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	92598562-03e3-4ca0-ba24-d5f4a06b75a1
\.


--
-- Data for Name: hospital_dicom_routing_configs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.hospital_dicom_routing_configs (id, hospital_id, is_active, created_by, modified_by, created_at, modified_at, dicom_server_id, public_id, package_built_at, package_built_by) FROM stdin;
1	1	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	760c462c-317f-4fdc-93e4-8f4c9e56f59b	\N	\N
\.


--
-- Data for Name: hospital_dicom_servers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.hospital_dicom_servers (id, hospital_id, name, ip_address, port, ae_title, username, password, is_active, created_by, modified_by, created_at, modified_at, viewer_base_url, dicom_port, storage_directory, index_directory, maximum_storage_size, maximum_patient_count, remote_access_allowed, http_server_enabled, enable_http_compression, ssl_enabled, authentication_enabled, authorization_enabled, authorization_root, authorization_checked_level, dicom_always_allow_echo, dicom_always_allow_find, dicom_always_allow_get, dicom_always_allow_move, dicom_always_allow_store, dicom_check_called_aet, dicom_tls_enabled, dicom_scp_timeout, dicom_peers_json, worklists_enabled, worklists_database, plugins_paths, pacs_api_callback_base_url, pacs_result_api_key_hash, dicomweb_path, public_id, public_health_check_url) FROM stdin;
1	1	UDAYA DICOM Server	127.0.0.1	8042	UDAYA_DCM_SERVER	\N	\N	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	http://127.0.0.1:3005	4242	/var/lib/udaya_dicom_server/db	/var/lib/udaya_dicom_server/db	0	0	t	t	t	f	t	t	/authorization	studies	t	t	t	t	t	f	f	30	{}	t	/var/lib/udaya_dicom_server/worklists	/usr/share/udaya_dicom_server/plugins\n/usr/local/share/udaya_dicom_server/plugins	\N	\N	/dicom-web	754d3832-b4a8-40b6-a411-db38c3c576b4	http://127.0.0.1:8042/system
\.


--
-- Data for Name: hospital_modalities; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.hospital_modalities (id, hospital_id, modality_id, is_active, created_by, modified_by, created_at, modified_at) FROM stdin;
1	1	1	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
2	1	2	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
3	1	3	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
4	1	4	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
5	1	5	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
6	1	6	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
7	1	7	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
8	1	8	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
9	1	9	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
10	1	10	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
11	1	11	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
\.


--
-- Data for Name: hospital_modality_server_routes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.hospital_modality_server_routes (id, hospital_id, modality_id, is_active, created_by, modified_by, created_at, modified_at, routing_config_id, machine_id, public_id) FROM stdin;
1	1	1	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	1	faacc9dd-7861-4b7a-8a59-e8ed20d496fd
2	1	2	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	2	f037bf4d-b8a7-42d1-b7ed-19dfeaaeecf9
3	1	3	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	3	72d34863-e2f4-4ea0-ae37-d9092a7d9908
4	1	4	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	4	3e10cabe-0499-4a66-afc3-d4b4308edee9
5	1	5	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	5	09a01616-94c5-4ef4-af18-91599597a839
6	1	6	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	6	d4c6ab2d-9672-434e-8779-c88fe3007f4d
7	1	7	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	7	5b0b7f50-2e1c-404d-8fe5-0e30fb8c325c
8	1	8	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	8	d9f40c26-8462-481a-be15-eae86a6fbcfc
9	1	9	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	9	ca81f762-2b0c-41d7-8481-49d96cbfaab8
10	1	10	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	10	b6a39f70-fe81-44e8-a3f9-5d538453def9
11	1	11	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1	11	caa5e426-516d-497c-bbb3-0d5d185f072b
\.


--
-- Data for Name: hospitals; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.hospitals (id, code, name, name_other, dicomweb_base_url, timezone, is_active, created_by, created, modified_by, modified, created_at, modified_at, abbr, public_id, logo_path, logo_file_name, logo_file_type, logo_file_size, logo_updated_at) FROM stdin;
1	UDAYA	UDAYA Hospital	\N	\N	Asia/Phnom_Penh	1	1	2026-06-24 14:01:57.131908+07	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	UDAYA	cb657923-fceb-4cb1-8ae4-5b4e54196f56	\N	\N	\N	\N	\N
\.


--
-- Data for Name: modalities; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.modalities (id, name, is_active, created_by, modified_by, created_at, modified_at, abbr, public_id) FROM stdin;
1	Computed Radiography	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	CR	8aa0f9c6-fd93-4e41-a60c-8a95712e23ad
2	Computed Tomography	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	CT	8bb5d1e3-bb63-4218-a767-d07c0d9bdcbf
3	Digital Radiography	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	DR	e32b948e-a64c-4307-8a03-32460e810ff8
4	Diagnostic X-Ray	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	DX	84e07a14-6f72-4ff3-a753-05f0ee8be0f6
5	Mammography	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	MG	2cac89ff-1766-4e3f-80be-b8fac36d975c
6	Magnetic Resonance Imaging	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	MR	54dd8737-9dd5-4a8a-ac4b-c0dec90cb13c
7	Other	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	OT	ed45b977-af8e-4596-b626-bea77dd6bf7e
8	Positron Emission Tomography	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	PT	7bdffb49-4f58-4f1b-8917-bae9c4e78f33
9	Ultrasound	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	US	f2fc420d-6cae-4c95-931a-8d2aeba8b00c
10	X-Ray Angiography	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	XA	93840fdc-8611-437d-80f8-6b6210aa0e63
11	External-Camera Photography	1	1	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	XC	bed379a7-3245-423f-b6f1-4651c82ec001
\.


--
-- Data for Name: module_details; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.module_details (id, module_id, code, name, name_other, type, action_key, display_order, is_active, created_by, created, modified_by, modified, created_at, modified_at, public_id) FROM stdin;
156	49	study.retention.approval.approve	Study Retention Approval (Approve Delete)	\N	APPROVE	APPROVE	6	1	\N	2026-06-14 18:15:08.769886+07	\N	\N	2026-06-14 18:15:08.769886+07	\N	cd566648-f133-f906-c780-549a5d66f996
157	49	study.retention.approval.view	Study Retention Approval (View)	\N	VIEW	VIEW	5	1	\N	2026-06-14 18:15:08.769886+07	\N	\N	2026-06-14 18:15:08.769886+07	\N	2e9ddd41-fff9-e843-5db3-470ff0289e4c
158	49	study.retention.policy.delete	Study Retention Policy (Delete)	\N	DELETE	DELETE	4	1	\N	2026-06-14 18:15:08.769886+07	\N	\N	2026-06-14 18:15:08.769886+07	\N	216d3842-576b-0a2f-6a2b-54a10c4d0a0a
159	49	study.retention.policy.edit	Study Retention Policy (Edit)	\N	EDIT	EDIT	3	1	\N	2026-06-14 18:15:08.769886+07	\N	\N	2026-06-14 18:15:08.769886+07	\N	ac5936ca-525a-2546-03e2-0c3071e0004b
160	49	study.retention.policy.add	Study Retention Policy (Add)	\N	ADD	ADD	2	1	\N	2026-06-14 18:15:08.769886+07	\N	\N	2026-06-14 18:15:08.769886+07	\N	ab333dad-be1f-d882-5fe7-407cf0fdea99
161	49	study.retention.policy.view	Study Retention Policy (View)	\N	VIEW	VIEW	1	1	\N	2026-06-14 18:15:08.769886+07	\N	\N	2026-06-14 18:15:08.769886+07	\N	2c1ad4cd-85b1-86d7-ed11-9607ac7099d0
18	6	pacs.worklist.return	Worklist (Return)	\N	ACTION	RETURN	7	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:04.661287+07	2026-05-29 19:47:43.988217+07	b9ffe568-d5b5-d074-d139-d5caacb50453
19	6	pacs.worklist.cancel	Worklist (Cancel)	\N	ACTION	CANCEL	8	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:04.661287+07	2026-05-29 19:47:43.988217+07	dd7503a9-178f-0821-c3b4-fdb7ead6e43a
20	6	pacs.worklist.complete	Worklist (Complete)	\N	ACTION	COMPLETE	9	2	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:04.661287+07	2026-05-29 19:47:43.988217+07	b91383c5-b5e1-7d43-0785-f9d39aeb43de
17	6	pacs.worklist.view	Worklist (View)	\N	VIEW	VIEW	1	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:04.661287+07	2026-05-29 19:47:43.988217+07	9b0a670a-fce8-b68b-d358-998f4c0324c6
14	5	pacs.patient.view	Patient (View)	\N	VIEW	VIEW	1	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	61514bb3-fad7-bdbf-9d6e-c3b0bbe99d9e
15	5	pacs.patient.create	Patient (Create)	\N	ADD	CREATE	2	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	902d0bb8-b86d-8090-909e-648d9d828eae
16	5	pacs.patient.edit	Patient (Edit)	\N	EDIT	EDIT	3	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	ce6a26db-b57e-fc49-0311-95814f2f5c2a
21	7	pacs.study.view	Study (View)	\N	VIEW	VIEW	1	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	3c936bdf-e809-3740-9bca-a6dc77a10609
2	2	user.view	User (View)	\N	VIEW	VIEW	1	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	a1763187-f34b-9d89-c750-df7a08c8c826
3	2	user.add	User (Add)	\N	ADD	ADD	2	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	f8db8d48-d1b0-edda-30bf-a20a88c80bb3
4	2	user.edit	User (Edit)	\N	EDIT	EDIT	3	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	2dab2857-882d-a7d5-8a6c-2f3bf643da9a
5	2	user.delete	User (Delete)	\N	DELETE	DELETE	4	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	f7efa268-7c72-4862-1f97-c72f75bcff87
6	3	role.view	Role (View)	\N	VIEW	VIEW	1	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	7069aaf5-2289-fb45-7469-66504e648d16
7	3	role.add	Role (Add)	\N	ADD	ADD	2	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	0630073d-a14a-22da-87c9-3e2b1a679597
8	3	role.edit	Role (Edit)	\N	EDIT	EDIT	3	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	371b1b76-f4e2-e8c8-696f-50674221dc53
9	3	role.delete	Role (Delete)	\N	DELETE	DELETE	4	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	3cf5c7b6-5f07-e53e-01f8-4b735be7bdb2
10	3	role.assign_permission	Role Permission (Assign)	\N	ASSIGN	ASSIGN_PERMISSION	5	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	d21eccd4-477e-3240-e481-9a70d0474367
11	4	hospital.view	Hospital (View)	\N	VIEW	VIEW	1	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	5ccaa6d8-5718-da54-1c41-3cfbe3d276d5
12	4	hospital.add	Hospital (Add)	\N	ADD	ADD	2	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	c2ebaf84-8cb9-a0b8-b923-229c990e47b9
13	4	hospital.edit	Hospital (Edit)	\N	EDIT	EDIT	3	1	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	\N	08ce0b90-8ea4-e60e-a4e2-1205d0aa3122
22	7	pacs.study.assign	PACS Study (Assign)	\N	ACTION	ASSIGN	2	2	\N	2026-05-10 23:13:04.661287+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.661287+07	2026-05-21 09:46:57.106663+07	e53a87bf-509d-d12d-8832-9e341283f36e
24	9	modulight.view	Modulight (View)	\N	VIEW	VIEW	1	2	\N	2026-05-10 23:13:04.988002+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.988002+07	\N	3bbda815-8007-6ceb-f801-6e0cd737b4a0
25	9	modulight.add	Modulight (Add)	\N	ADD	ADD	2	2	\N	2026-05-10 23:13:04.988002+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.988002+07	\N	692c4d0e-1431-bd1e-6ac3-21532fa46655
26	9	modulight.edit	Modulight (Edit)	\N	EDIT	EDIT	3	2	\N	2026-05-10 23:13:04.988002+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.988002+07	\N	94fd33d6-b200-7b1b-c92d-0d6e21beace4
27	9	modulight.delete	Modulight (Delete)	\N	DELETE	DELETE	4	2	\N	2026-05-10 23:13:04.988002+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.988002+07	\N	1f025cd9-de0a-3aa7-2209-48b7d1dd24b8
28	4	hospital.modulight.view	Hospital Modulight (View)	\N	VIEW	VIEW	4	2	\N	2026-05-10 23:13:04.988002+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.988002+07	\N	ab492145-6053-915c-9549-ab22785bf79b
52	16	hospital.modality.view	Hospital Modality (View)	\N	VIEW	VIEW	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	3eea5239-c73b-172d-a983-675cf8d6296f
53	17	modality.view	Modality (View)	\N	VIEW	VIEW	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	2b8c5c96-a6b2-33f4-8d7d-4be41a497485
54	17	modality.add	Modality (Add)	\N	ADD	ADD	2	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	fa60dfb3-f44f-d56c-9e08-b2a5fb83c789
55	17	modality.edit	Modality (Edit)	\N	EDIT	EDIT	3	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	5c229b10-a163-219a-2490-defc09c5dbd4
56	17	modality.delete	Modality (Delete)	\N	DELETE	DELETE	4	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	59d21cdc-71ca-62bf-fa7f-c3acf9db3423
82	25	dicom.server.view	DICOM Server (View)	\N	VIEW	VIEW	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	4989c6ae-0c4d-6428-e97a-471de9004e4a
37	6	pacs.worklist.translate	Worklist (Translate)	\N	ACTION	TRANSLATE	6	2	\N	2026-05-10 23:13:05.37918+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:05.37918+07	2026-05-29 19:47:43.988217+07	ae3f20f3-7f0b-6390-5ee8-5161eb9d4723
33	6	pacs.worklist.assign	Worklist (Assign)	\N	ACTION	ASSIGN	2	1	\N	2026-05-10 23:13:05.37918+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:05.37918+07	2026-05-29 19:47:43.988217+07	bf950439-2ceb-0450-4385-180764d15151
34	6	pacs.worklist.send	Worklist (Send To DICOM Server)	\N	ACTION	SEND_TO_PACS	3	1	\N	2026-05-10 23:13:05.37918+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:05.37918+07	2026-05-29 19:47:43.988217+07	5c90e580-399f-f632-cc85-d15499535424
35	6	pacs.worklist.receive	Worklist (Receive Study)	\N	ACTION	RECEIVE_STUDY	4	1	\N	2026-05-10 23:13:05.37918+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:05.37918+07	2026-05-29 19:47:43.988217+07	04c5b788-af1b-0bac-0587-a72df3560e02
36	6	pacs.worklist.view_study	Worklist (View Study)	\N	VIEW	VIEW_STUDY	5	1	\N	2026-05-10 23:13:05.37918+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:05.37918+07	2026-05-29 19:47:43.988217+07	1901a2e6-6b14-b259-5a0d-cf7ea7056bcb
77	22	file.view	File Upload (View)	\N	VIEW	VIEW	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	31222de9-bba5-29a1-e82e-32845d2c37ad
78	22	file.add	File Upload (Add)	\N	ADD	ADD	2	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	5673eb0d-3b7b-bb22-9129-deed91576cd5
79	22	file.delete	File Upload (Delete)	\N	DELETE	DELETE	3	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	07521bd7-d1ec-ae5c-4b53-74684845c527
90	27	system.activity.view	System Activity (View)	\N	VIEW	VIEW	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	70ce3ace-0956-1229-e53c-54789d3705d0
91	28	report.user_log.view	User Log (View)	\N	VIEW	VIEW	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	1d929125-f06e-622f-a379-aaaa916fa871
83	25	dicom.server.add	DICOM Server (Add)	\N	ADD	ADD	2	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	20e795c6-39df-0072-4d79-60fb1f489454
84	25	dicom.server.edit	DICOM Server (Edit)	\N	EDIT	EDIT	3	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	4d881692-3e1f-4a5b-4368-ee9c6b73eb17
85	25	dicom.server.delete	DICOM Server (Delete)	\N	DELETE	DELETE	4	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	86ef49de-33d4-f9f4-573c-471c4315e293
86	26	dicom.routing.view	DICOM Routing (View)	\N	VIEW	VIEW	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	58b12416-776f-68d7-0813-f5d037cfb852
87	26	dicom.routing.add	DICOM Routing (Add)	\N	ADD	ADD	2	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	62754e1f-ad37-a45b-fdb9-7aed5b467b74
88	26	dicom.routing.edit	DICOM Routing (Edit)	\N	EDIT	EDIT	3	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	ff7b86fd-cde3-b0d4-50ce-ccf3f78f6c75
89	26	dicom.routing.delete	DICOM Routing (Delete)	\N	DELETE	DELETE	4	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	6dfbc273-24cc-136b-174c-faaf2d1ed1a0
145	46	dicom.machine.view	DICOM Machine (View)	\N	VIEW	VIEW	1	1	\N	2026-05-30 21:53:37.133411+07	\N	2026-06-03 10:52:03.192999+07	2026-05-30 21:53:37.133411+07	\N	93e476f3-d43d-95fc-ee21-268388ba913d
146	46	dicom.machine.add	DICOM Machine (Add)	\N	ADD	ADD	2	1	\N	2026-05-30 21:53:37.133411+07	\N	2026-06-03 10:52:03.192999+07	2026-05-30 21:53:37.133411+07	\N	7037494d-9d1b-6e50-b2ff-fcd03ed741e3
147	46	dicom.machine.edit	DICOM Machine (Edit)	\N	EDIT	EDIT	3	1	\N	2026-05-30 21:53:37.133411+07	\N	2026-06-03 10:52:03.192999+07	2026-05-30 21:53:37.133411+07	\N	de79040b-f9a3-9ee7-385f-71f701b0bf41
148	46	dicom.machine.delete	DICOM Machine (Delete)	\N	DELETE	DELETE	4	1	\N	2026-05-30 21:53:37.133411+07	\N	2026-06-03 10:52:03.192999+07	2026-05-30 21:53:37.133411+07	\N	69717d2a-62a8-accf-5579-c2b861bce133
1	1	home.view	Home (View)	\N	VIEW	VIEW	1	1	\N	2026-05-10 23:13:01.857582+07	\N	2026-06-04 21:39:19.489654+07	2026-05-10 23:13:01.857582+07	\N	ce4c9c1f-f7cc-823c-0a6e-e8c4df8bf91d
150	48	pacs.result.template.delete	PACS Result Template (Delete)	\N	DELETE	DELETE	4	1	\N	2026-06-07 15:37:33.953806+07	\N	\N	2026-06-07 15:37:33.953806+07	\N	25b0b0bd-8495-155f-cfe1-3691a1a7e364
151	48	pacs.result.template.edit	PACS Result Template (Edit)	\N	EDIT	EDIT	3	1	\N	2026-06-07 15:37:33.953806+07	\N	\N	2026-06-07 15:37:33.953806+07	\N	5a037df1-50a3-a0d4-488b-76e6e0f1ddde
152	48	pacs.result.template.add	PACS Result Template (Add)	\N	ADD	ADD	2	1	\N	2026-06-07 15:37:33.953806+07	\N	\N	2026-06-07 15:37:33.953806+07	\N	92fc125f-3676-4a29-aa84-6cd5ace3bb68
153	48	pacs.result.template.view	PACS Result Template (View)	\N	VIEW	VIEW	1	1	\N	2026-06-07 15:37:33.953806+07	\N	\N	2026-06-07 15:37:33.953806+07	\N	9a9006f5-eb96-bc73-5279-7f7b76529d53
155	7	pacs.study.upload	Study (DICOM Upload)	\N	ADD	UPLOAD	2	1	\N	2026-06-14 08:43:31.80903+07	\N	\N	2026-06-14 08:43:31.80903+07	\N	ce04ae25-7f21-7f0a-ddbc-70844e970cb3
\.


--
-- Data for Name: module_types; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.module_types (id, code, name, name_other, display_order, is_active, created_by, created, modified_by, modified, created_at, modified_at, menu_group_code, menu_group_name, menu_group_order, public_id) FROM stdin;
9	PACS_REPORT	Reports	\N	14	2	\N	2026-05-10 23:13:01.853973+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:01.853973+07	\N	REPORT	Report	3	6fe63856-194b-5160-bd51-5ebdb1695e26
23	PACS_SERVICE	Service	\N	13	2	\N	2026-05-21 09:46:57.194734+07	\N	\N	2026-05-21 09:46:57.194734+07	2026-05-26 17:42:43.294793+07	PATIENT	Patient	2	26d0a6a8-d27f-06f0-fd40-31d0943769ec
6	PACS_WORKLIST	Worklist	\N	11	1	\N	2026-05-10 23:13:01.853973+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:01.853973+07	2026-05-29 19:47:43.988217+07	PATIENT	Patient	2	d3963d08-d149-3db6-bc92-4c680bcb65aa
22	PACS_WORKLIST_RESULT	Worklist Result	\N	12	1	\N	2026-05-21 09:46:57.194734+07	\N	2026-05-29 19:47:43.988217+07	2026-05-21 09:46:57.194734+07	2026-05-29 19:47:43.988217+07	PATIENT	Patient	2	5db9dacd-509f-27df-c61f-d0094a393433
5	PACS_PATIENT	Patient	\N	10	1	\N	2026-05-10 23:13:01.853973+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:01.853973+07	\N	PATIENT	Patient	2	a2535107-4e91-9fbd-15db-a5ed90b61e11
24	FILE_UPLOAD	File Upload	\N	14	1	\N	2026-05-21 09:46:57.194734+07	\N	\N	2026-05-21 09:46:57.194734+07	\N	PATIENT	Patient	2	3a245448-2307-0cd9-3538-94015d851df0
7	PACS_STUDY	Study	\N	15	1	\N	2026-05-10 23:13:01.853973+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:01.853973+07	\N	PATIENT	Patient	2	5d9c3c79-20d7-6a9f-1be8-b177d8ecc57f
26	SYSTEM_ACTIVITY	System Activity	\N	20	1	\N	2026-05-21 09:46:57.194734+07	\N	\N	2026-05-21 09:46:57.194734+07	\N	REPORT	Report	3	a1c6d2ba-94e2-ad64-8aee-6e1b8816d76e
27	USER_LOG	User Log	\N	21	1	\N	2026-05-21 09:46:57.194734+07	\N	\N	2026-05-21 09:46:57.194734+07	\N	REPORT	Report	3	76e3d9d3-5e0c-9420-d6f1-22390beb70cb
2	USER	User	\N	30	1	\N	2026-05-10 23:13:01.853973+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:01.853973+07	\N	SETTING	Setting	4	a8703935-b8d5-717d-29f9-fd1eca89c021
3	ROLE	Role	\N	31	1	\N	2026-05-10 23:13:01.853973+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:01.853973+07	\N	SETTING	Setting	4	49070892-efbf-735e-8ffd-9109f405a507
4	HOSPITAL	Hospital	\N	32	1	\N	2026-05-10 23:13:01.853973+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:01.853973+07	\N	SETTING	Setting	4	6ecfc08b-d8a2-5639-d114-b08c5f0d28f2
31	HOSPITAL_MODALITY	Hospital Modality	\N	33	1	\N	2026-05-21 09:46:57.194734+07	\N	\N	2026-05-21 09:46:57.194734+07	\N	SETTING	Setting	4	abd911a5-2de7-d2ee-9cd1-ea7ed1445afd
32	MODALITY	Modality	\N	34	1	\N	2026-05-21 09:46:57.194734+07	\N	\N	2026-05-21 09:46:57.194734+07	\N	SETTING	Setting	4	3401a90a-375c-a824-16d2-7e7131c21a6e
34	DICOM_SERVER	DICOM Server	\N	41	1	\N	2026-05-21 09:46:57.194734+07	\N	\N	2026-05-21 09:46:57.194734+07	\N	DICOMCONFIG	DICOM Config	5	6facea73-1d99-be23-a3f1-01200762933d
35	DICOM_ROUTING	DICOM Routing	\N	42	1	\N	2026-05-21 09:46:57.194734+07	\N	\N	2026-05-21 09:46:57.194734+07	\N	DICOMCONFIG	DICOM Config	5	04e7bcc8-3547-c8f5-ed03-a21948d07e8f
36	DICOM_MACHINE	DICOM Machines	\N	43	1	\N	2026-05-30 21:53:37.133411+07	\N	2026-06-03 10:52:03.192999+07	2026-05-30 21:53:37.133411+07	\N	DICOMCONFIG	DICOM Config	5	9a2bcc4b-f155-e33a-3d8b-69c4faa47d44
1	HOME	Home	\N	1	1	\N	2026-05-10 23:13:01.853973+07	\N	2026-06-04 21:39:19.489654+07	2026-05-10 23:13:01.853973+07	\N	DASHBOARD	Dashboard	1	f1bcbf88-29f6-3cdc-8d69-aadc89cb4184
\.


--
-- Data for Name: modules; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.modules (id, module_type_id, code, name, name_other, icon, display_order, is_active, created_by, created, modified_by, modified, created_at, modified_at, public_id) FROM stdin;
49	4	study-retention	Study Retention	\N	\N	7	1	\N	2026-06-14 18:15:08.769886+07	\N	\N	2026-06-14 18:15:08.769886+07	\N	52f1b48f-95b8-f879-46fc-6ff6fff24921
5	5	pacs-patient	Patient	\N	\N	1	1	\N	2026-05-10 23:13:04.655159+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.655159+07	\N	a37c616a-d3a8-3e13-76bc-11f8c8375dcc
10	23	pacs-service	Service	\N	\N	1	1	\N	2026-05-10 23:13:05.373387+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:05.373387+07	\N	b2801e61-ff4c-7d57-a5d0-9da37e0735d2
22	24	file-upload	File Upload	\N	\N	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	5ebc3c3b-f5f3-fb83-6ca1-c2574dc6a0b3
7	7	pacs-study	Study	\N	\N	1	1	\N	2026-05-10 23:13:04.658083+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.658083+07	\N	a4adf734-29f6-1c31-9225-94f202ae408d
27	26	system-activity	System Activity	\N	\N	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	588810d8-9768-a1ce-0059-70e3a98eaed3
28	27	user-log	User Log	\N	\N	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	aaf1d087-1463-7956-719b-1bd5592bde10
2	2	user	User	\N	\N	1	1	\N	2026-05-10 23:13:04.651678+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.651678+07	\N	89c29625-093c-e4db-8359-713d1dc8b4a1
3	3	role	Role	\N	\N	1	1	\N	2026-05-10 23:13:04.653645+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.653645+07	\N	b3e71d81-80e5-e182-4a43-6664713bfb15
4	4	hospital	Hospital	\N	\N	1	1	\N	2026-05-10 23:13:04.654331+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.654331+07	\N	2bc8016b-3323-e2e2-e055-5aadf1b944aa
16	31	hospital-modality	Hospital Modality	\N	\N	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	b6647d8c-6c61-e7e9-3fd6-6be475f9c33c
17	32	modality	Modality	\N	\N	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	9914827a-674b-e169-8387-9d270d866299
25	34	dicom-server	DICOM Server	\N	\N	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	267665f9-9243-6eab-82a0-2717ec436829
26	35	dicom-routing	DICOM Routing	\N	\N	1	1	\N	2026-05-21 09:46:57.143808+07	\N	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.143808+07	\N	edf78496-fe01-2e07-7df7-16480815d598
9	4	modulight	Modulight	\N	\N	2	2	\N	2026-05-10 23:13:04.982758+07	\N	2026-05-21 09:46:57.194734+07	2026-05-10 23:13:04.982758+07	\N	d7873d6f-cd54-80bf-d35f-7fd22011731d
6	6	pacs-worklist	Worklist	\N	\N	1	1	\N	2026-05-10 23:13:04.656567+07	\N	2026-05-29 19:47:43.988217+07	2026-05-10 23:13:04.656567+07	2026-05-29 19:47:43.988217+07	ee84c29f-1729-9f25-6e8a-645c4ac80a13
46	36	dicom-machine	DICOM Machines	\N	\N	1	1	\N	2026-05-30 21:53:37.133411+07	\N	2026-06-03 10:52:03.192999+07	2026-05-30 21:53:37.133411+07	\N	fbcf14fe-112a-d969-d153-72b963aaba06
1	1	home	Home	\N	\N	1	1	\N	2026-05-10 23:13:01.855039+07	\N	2026-06-04 21:39:19.489654+07	2026-05-10 23:13:01.855039+07	\N	4272a74f-e224-6d07-d023-90acf57f0f16
48	4	pacs-result-template	PACS Result Templates	\N	\N	6	1	\N	2026-06-07 15:37:33.953806+07	\N	\N	2026-06-07 15:37:33.953806+07	\N	b663b9b4-25ff-bec5-3f15-cdab253cca4e
\.


--
-- Data for Name: oauth2_clients; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.oauth2_clients (id, client_id, client_name, client_secret_hash, client_type, allowed_grant_types, allowed_scopes, access_token_lifetime_ms, refresh_token_lifetime_ms, is_active, created, modified, dicom_server_id) FROM stdin;
1	udaya-dicom-server	UDAYA DICOM Server Callback	\N	CONFIDENTIAL	client_credentials	pacs.api	900000	2592000000	t	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	1
\.


--
-- Data for Name: pacs_daily_stats; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_daily_stats (hospital_id, stat_date, modality_id, waiting_count, in_progress_count, cancelled_count, failed_count, received_study_count, completed_result_count, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: pacs_patient_sequences; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_patient_sequences (id, hospital_id, sequence_year, last_sequence, modified_at) FROM stdin;
\.


--
-- Data for Name: pacs_realtime_notification_events_default; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_realtime_notification_events_default (id, hospital_id, source, event_type, severity, title, message, worklist_id, study_id, worklist_public_key, study_public_key, patient_name, visit_code, accession_number, dedupe_key, created_at) FROM stdin;
\.


--
-- Data for Name: pacs_result_images; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_result_images (id, result_id, image_path, original_file_name, file_type, file_size, sort_order, is_active, created_at, image_public_id, hospital_id, modality_id, study_id, worklist_id, file_sha256) FROM stdin;
\.


--
-- Data for Name: pacs_result_templates; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_result_templates (id, hospital_id, modality_id, template_name, template_content, created_at, is_active, modified_at, public_id, created_by, modified_by) FROM stdin;
\.


--
-- Data for Name: pacs_result_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_result_versions (id, public_id, hospital_id, result_id, version_no, modality_id, study_id, worklist_id, patient_id, result_date, template_id, result_text, status, completed, is_active, changed_by, change_reason, changed_at) FROM stdin;
\.


--
-- Data for Name: pacs_results; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_results (id, hospital_id, modality_id, study_id, worklist_id, patient_id, result_date, template_id, result_text, status, completed, is_active, created_by, created_at, modified_at, result_public_id) FROM stdin;
\.


--
-- Data for Name: pacs_studies; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_studies (id, hospital_id, patient_id, study_instance_uid, accession_number, modality, study_date, study_description, status, is_active, created, modified, dicom_server_study_id, dicom_server_patient_id, dicom_server_series_id, received_at, dicom_server_id, public_id, reference_visit_code, source_type, uploaded_by, image_received_at, modality_id, instance_count, institution_name, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: pacs_studies_week_cache; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_studies_week_cache (id, public_id, hospital_id, patient_id, modality_id, study_instance_uid, accession_number, reference_visit_code, modality, study_date, received_at, image_received_at, study_description, status, is_active, dicom_server_id, dicom_server_study_id, dicom_server_patient_id, dicom_server_series_id, instance_count, institution_name, created, modified, created_at, cached_at) FROM stdin;
\.


--
-- Data for Name: pacs_system_settings; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_system_settings (setting_key, setting_value, modified_by, modified_at) FROM stdin;
dicom.server.health.enabled	true	\N	2026-06-09 23:57:37.306672
dicom.server.health.poll_interval_seconds	5	\N	2026-06-09 23:57:37.306672
\.


--
-- Data for Name: pacs_viewer_states; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_viewer_states (id, public_id, hospital_id, modality_id, study_id, worklist_id, patient_id, study_instance_uid, accession_number, patient_code, state_type, schema_version, viewer_state, measurements, annotations, segmentations, additional_findings, metadata, version, created_by, modified_by, is_active, created_at, modified_at, labelmap_segmentations, contour_segmentations, surface_segmentations, presentation_state, tool_state, payload_size_bytes, payload_sha256, deleted_by, deleted_at) FROM stdin;
\.


--
-- Data for Name: pacs_visit_sequences; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_visit_sequences (id, hospital_id, sequence_date, last_sequence, modified_at) FROM stdin;
\.


--
-- Data for Name: pacs_worklist_histories_default; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_worklist_histories_default (id, hospital_id, worklist_id, patient_id, from_status, to_status, action, reason, created, created_by, created_at, retention_policy_id, retain_until, archive_after, purge_after) FROM stdin;
\.


--
-- Data for Name: pacs_worklist_study_links; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_worklist_study_links (id, hospital_id, worklist_id, study_id, is_primary, linked_at, created_by) FROM stdin;
\.


--
-- Data for Name: pacs_worklists; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_worklists (id, hospital_id, patient_id, status, notes, created, modified, modality_id, visit_code, created_by, modified_by, created_at, modified_at, dicom_server_worklist_id, dicom_server_worklist_path, sent_at, received_at, study_description, scheduled_date, scheduled_time, error_message, started_at, image_received_at, cancelled_at, study_id, dicom_route_id, public_id) FROM stdin;
\.


--
-- Data for Name: pacs_worklists_week_cache; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pacs_worklists_week_cache (id, public_id, hospital_id, patient_id, modality_id, dicom_route_id, study_id, visit_code, status, scheduled_date, scheduled_time, study_description, dicom_server_worklist_id, dicom_server_worklist_path, sent_at, received_at, image_received_at, cancelled_at, started_at, created_at, modified_at, created, cached_at, created_by) FROM stdin;
\.


--
-- Data for Name: partition_maintenance_configs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.partition_maintenance_configs (id, parent_schema, parent_table, partition_column, retention_months, is_active, created_at, updated_at, partition_granularity, retention_mode, future_partitions, allow_auto_drop) FROM stdin;
1	public	user_logs	created	12	1	2026-06-18 21:22:23.393516+07	2026-06-19 08:42:31.86445+07	MONTH	FIXED_MONTHS	3	t
2	public	system_activities	created	12	1	2026-06-18 21:22:23.393516+07	2026-06-19 08:42:31.86445+07	MONTH	FIXED_MONTHS	3	t
3	public	dicom_server_callback_log	received_at	12	1	2026-06-18 21:22:23.393516+07	2026-06-19 08:42:31.86445+07	MONTH	FIXED_MONTHS	3	t
4	public	pacs_realtime_notification_events	created_at	12	1	2026-06-18 21:22:23.393516+07	2026-06-19 08:42:31.86445+07	MONTH	FIXED_MONTHS	3	t
5	public	pacs_worklist_histories	created	\N	1	2026-06-18 21:22:23.393516+07	2026-06-19 08:42:31.86445+07	YEAR	POLICY_BASED	2	f
6	public	study_retention_delete_requests	created_at	\N	1	2026-06-18 21:22:23.393516+07	2026-06-19 08:42:31.86445+07	YEAR	POLICY_BASED	2	f
\.


--
-- Data for Name: patients; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.patients (id, hospital_id, patient_uid, gender, date_of_birth, is_active, created, modified, phone_number, public_id, first_name, last_name, patient_hn, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: refresh_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.refresh_tokens (id, user_id, hospital_id, client_id, client_name, token_hash, rotated_from_id, expires_at, revoked_at, revoked_reason, ip_address, user_agent, created) FROM stdin;
\.


--
-- Data for Name: revoked_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.revoked_tokens (id, jti, user_id, hospital_id, expires_at, revoked_at, reason) FROM stdin;
\.


--
-- Data for Name: role_module_details; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.role_module_details (id, role_id, module_detail_id, created_by, created, created_at, modified_at) FROM stdin;
1519	1	145	1	2026-05-30 21:53:37.133411+07	2026-05-30 21:53:37.133411+07	\N
1520	1	146	1	2026-05-30 21:53:37.133411+07	2026-05-30 21:53:37.133411+07	\N
1521	1	147	1	2026-05-30 21:53:37.133411+07	2026-05-30 21:53:37.133411+07	\N
1522	1	148	1	2026-05-30 21:53:37.133411+07	2026-05-30 21:53:37.133411+07	\N
1590	1	155	1	2026-06-14 08:43:31.80903+07	2026-06-14 08:43:31.80903+07	\N
1592	70	155	1	2026-06-14 08:43:31.80903+07	2026-06-14 08:43:31.80903+07	\N
807	1	1	1	2026-05-19 23:49:18.278136+07	2026-05-19 23:49:18.278136+07	\N
808	1	2	1	2026-05-19 23:49:18.278136+07	2026-05-19 23:49:18.278136+07	\N
809	1	3	1	2026-05-19 23:49:18.278136+07	2026-05-19 23:49:18.278136+07	\N
237	4	14	1	2026-05-10 23:18:13.220955+07	2026-05-10 23:18:13.220955+07	\N
238	4	17	1	2026-05-10 23:18:13.220955+07	2026-05-10 23:18:13.220955+07	\N
239	4	21	1	2026-05-10 23:18:13.220955+07	2026-05-10 23:18:13.220955+07	\N
242	4	28	1	2026-05-10 23:18:13.220955+07	2026-05-10 23:18:13.220955+07	\N
243	4	1	1	2026-05-10 23:18:13.220955+07	2026-05-10 23:18:13.220955+07	\N
244	4	35	1	2026-05-10 23:18:13.220955+07	2026-05-10 23:18:13.220955+07	\N
245	4	36	1	2026-05-10 23:18:13.220955+07	2026-05-10 23:18:13.220955+07	\N
247	5	14	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
248	5	15	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
249	5	16	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
250	5	17	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
251	5	18	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
252	5	19	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
253	5	28	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
255	5	1	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
256	5	33	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
257	5	34	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
258	5	36	1	2026-05-10 23:18:13.223314+07	2026-05-10 23:18:13.223314+07	\N
283	70	1	1	2026-05-16 23:00:52.063316+07	2026-05-16 23:00:52.063316+07	\N
1203	4	77	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1208	4	78	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1213	4	79	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1223	1	4	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1225	1	5	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1227	1	6	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1229	1	7	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1231	1	8	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1233	1	9	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1235	1	10	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1237	1	11	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1239	1	12	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1241	1	13	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1249	1	14	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1251	1	15	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1253	1	16	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1255	1	17	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1257	1	18	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1259	1	19	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1263	1	21	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1267	1	24	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1269	1	25	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1271	1	26	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1273	1	27	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1275	1	28	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1277	1	52	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1279	1	53	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1281	1	54	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1283	1	55	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1285	1	56	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1289	1	33	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1291	1	34	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1293	1	35	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1295	1	36	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1307	1	77	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1309	1	78	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1311	1	79	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1313	1	82	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1315	1	83	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1317	1	84	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1319	1	85	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1321	1	86	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1323	1	87	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1325	1	88	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1327	1	89	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1329	1	90	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1331	1	91	1	2026-05-21 09:46:57.143808+07	2026-05-21 09:46:57.143808+07	\N
1348	4	52	1	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.194734+07	\N
1355	5	52	1	2026-05-21 09:46:57.194734+07	2026-05-21 09:46:57.194734+07	\N
1601	110	1	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1602	110	14	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1603	110	15	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1604	110	16	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1605	110	17	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1606	110	33	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1607	110	34	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1608	110	35	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1609	110	36	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1610	110	18	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1611	110	19	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1612	110	77	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1613	110	78	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1614	110	79	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1615	110	21	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1616	110	155	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1617	110	90	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1618	110	91	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1619	110	2	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1620	110	3	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1621	110	4	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1622	110	5	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1623	110	6	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1624	110	7	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1625	110	8	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1626	110	9	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1627	110	10	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1628	110	11	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1594	1	156	1	2026-06-14 18:15:08.769886+07	2026-06-14 18:15:08.769886+07	\N
1595	1	157	1	2026-06-14 18:15:08.769886+07	2026-06-14 18:15:08.769886+07	\N
1596	1	158	1	2026-06-14 18:15:08.769886+07	2026-06-14 18:15:08.769886+07	\N
1597	1	159	1	2026-06-14 18:15:08.769886+07	2026-06-14 18:15:08.769886+07	\N
1598	1	160	1	2026-06-14 18:15:08.769886+07	2026-06-14 18:15:08.769886+07	\N
1599	1	161	1	2026-06-14 18:15:08.769886+07	2026-06-14 18:15:08.769886+07	\N
1629	110	12	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1630	110	13	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1631	110	153	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1632	110	152	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1633	110	151	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1634	110	150	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1635	110	52	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1636	110	53	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1637	110	54	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1638	110	55	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1639	110	56	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1640	110	82	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1641	110	83	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1642	110	84	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1643	110	85	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1644	110	86	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1536	1	150	1	2026-06-07 15:37:33.953806+07	2026-06-07 15:37:33.953806+07	\N
1537	1	151	1	2026-06-07 15:37:33.953806+07	2026-06-07 15:37:33.953806+07	\N
1538	1	152	1	2026-06-07 15:37:33.953806+07	2026-06-07 15:37:33.953806+07	\N
1539	1	153	1	2026-06-07 15:37:33.953806+07	2026-06-07 15:37:33.953806+07	\N
1645	110	87	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1646	110	88	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1647	110	89	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1648	110	145	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1649	110	146	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1650	110	147	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1651	110	148	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1652	110	161	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1653	110	160	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1654	110	159	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1655	110	158	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1656	110	157	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
1657	110	156	1	2026-06-17 11:54:36.994146+07	2026-06-17 11:54:36.994146+07	\N
\.


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.roles (id, code, name, description, is_system_role, is_active, created_by, created, modified_by, modified, created_at, modified_at, public_id) FROM stdin;
110	f3342360a5f86b8714cca50d61006886	Admin Group	\N	f	1	1	2026-05-19 23:58:50.773453+07	1	2026-06-17 11:54:36.994146+07	2026-05-19 23:58:50.773453+07	2026-06-17 11:54:36.994146+07	dd8c074c-02a3-23b8-a5ec-24ef1d559950
1	ADMIN	System Admin	\N	t	1	\N	2026-05-10 23:13:01.860892+07	\N	\N	2026-05-10 23:13:01.860892+07	\N	18e44632-8ce1-eff7-3809-be1e299b9f18
4	DOCTOR	Doctor	Doctor group	f	1	1	2026-05-10 23:18:13.212644+07	\N	\N	2026-05-10 23:18:13.212644+07	\N	b114ea74-5265-1cb3-5a47-b460bb71205f
5	CLINIC	Clinic	Clinic group	f	1	1	2026-05-10 23:18:13.214895+07	\N	\N	2026-05-10 23:18:13.214895+07	\N	fbe06cb1-fc70-3ea5-7bf0-43edcc07dc05
70	SUPER_ADMIN_GROUP	SuperAdmin Group	\N	t	1	\N	2026-05-16 22:38:47.053003+07	\N	\N	2026-05-16 22:38:47.053003+07	\N	1c310aa1-f5ff-1f35-2777-7ae1517b0b9c
34	USER_HOSPITAL_SCOPE_ALL	User Hospital Scope All	\N	t	1	\N	2026-05-16 16:22:35.332551+07	1	2026-06-18 19:00:31.983063+07	2026-05-16 16:22:35.332551+07	2026-06-18 19:00:31.983063+07	ce13e33b-3322-6d2b-5b34-f83872c450be
\.


--
-- Data for Name: study_retention_delete_requests_default; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.study_retention_delete_requests_default (id, public_id, hospital_id, study_id, policy_id, dicom_server_id, modality_id, status, expires_at, near_expiry_at, study_instance_uid, dicom_server_study_id, accession_number, reference_visit_code, patient_mrn, patient_name, requested_by, requested_at, approved_by, approved_at, rejected_by, rejected_at, deleted_at, decision_note, error_message, created_at, updated_at, retain_until, archive_after, purge_after) FROM stdin;
\.


--
-- Data for Name: study_retention_policies; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.study_retention_policies (id, public_id, hospital_id, dicom_server_id, modality_id, retention_days, notify_before_days, require_approval, enabled, notes, is_active, created_by, modified_by, created_at, modified_at, retention_value, retention_unit, auto_delete) FROM stdin;
\.


--
-- Data for Name: system_activities_default; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_activities_default (id, endpoint, module, module_id, description, bug, line_code, browser, operating_system, ip, host_name, duration, created_by, created, status, action, public_id, created_at) FROM stdin;
\.


--
-- Data for Name: user_groups; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_groups (id, user_id, role_id, is_active, created_by, created, modified_by, modified, created_at, modified_at) FROM stdin;
1	1	1	1	1	2026-06-24 14:01:57.131908+07	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
2	1	4	1	1	2026-06-24 14:01:57.131908+07	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
3	1	5	1	1	2026-06-24 14:01:57.131908+07	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
4	1	34	1	1	2026-06-24 14:01:57.131908+07	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
5	1	70	1	1	2026-06-24 14:01:57.131908+07	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
6	1	110	1	1	2026-06-24 14:01:57.131908+07	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
\.


--
-- Data for Name: user_hospitals; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_hospitals (id, user_id, hospital_id, is_default, is_active, created_by, created, modified_by, modified, created_at, modified_at) FROM stdin;
1	1	1	t	1	1	2026-06-24 14:01:57.131908+07	1	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07	2026-06-24 14:01:57.131908+07
\.


--
-- Data for Name: user_logs_default; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_logs_default (id, user_id, type, http_user_agent, remote_addr, created, public_id, created_at) FROM stdin;
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (id, username, email, password, first_name, last_name, telephone, signature_photo, user_type, expire_date, account_locked, permission_version, is_active, created_by, created, modified_by, modified, created_at, modified_at, public_id) FROM stdin;
1	admin	admin@pacs.local	$2a$12$0XwbxxjhsMV9LgFfAndGw.m9g4XJDC/NY.OGTKmwdt2GD1xz7Q7FG	System	Admin	\N	\N	9	2099-12-31	f	476	1	\N	2026-05-10 23:13:01.862109+07	1	2026-06-24 08:30:05.264764+07	2026-05-10 23:13:01.862109+07	2026-05-31 01:06:47.30697+07	a93838c0-36a5-2298-b154-6a746847eda9
\.


--
-- Name: countries_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.countries_id_seq', 5, true);


--
-- Name: dicom_server_callback_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dicom_server_callback_log_id_seq', 1, false);


--
-- Name: dicom_server_unmatched_callback_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dicom_server_unmatched_callback_log_id_seq', 1, false);


--
-- Name: endpoint_permissions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.endpoint_permissions_id_seq', 278, true);


--
-- Name: hospital_dicom_machines_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.hospital_dicom_machines_id_seq', 11, true);


--
-- Name: hospital_dicom_routing_configs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.hospital_dicom_routing_configs_id_seq', 1, true);


--
-- Name: hospital_dicom_servers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.hospital_dicom_servers_id_seq', 1, true);


--
-- Name: hospital_modulight_server_routes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.hospital_modulight_server_routes_id_seq', 11, true);


--
-- Name: hospital_modulights_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.hospital_modulights_id_seq', 11, true);


--
-- Name: hospitals_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.hospitals_id_seq', 1, true);


--
-- Name: module_details_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.module_details_id_seq', 161, true);


--
-- Name: module_types_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.module_types_id_seq', 37, true);


--
-- Name: modules_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.modules_id_seq', 49, true);


--
-- Name: modulights_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.modulights_id_seq', 11, true);


--
-- Name: oauth2_clients_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.oauth2_clients_id_seq', 1, true);


--
-- Name: pacs_patient_queue_histories_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_patient_queue_histories_id_seq', 1, false);


--
-- Name: pacs_patient_queue_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_patient_queue_id_seq', 1, false);


--
-- Name: pacs_patient_sequences_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_patient_sequences_id_seq', 1, false);


--
-- Name: pacs_queue_study_links_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_queue_study_links_id_seq', 1, false);


--
-- Name: pacs_realtime_notification_events_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_realtime_notification_events_id_seq', 1, false);


--
-- Name: pacs_result_images_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_result_images_id_seq', 1, false);


--
-- Name: pacs_result_templates_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_result_templates_id_seq', 1, false);


--
-- Name: pacs_result_versions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_result_versions_id_seq', 1, false);


--
-- Name: pacs_results_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_results_id_seq', 1, false);


--
-- Name: pacs_studies_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_studies_id_seq', 1, false);


--
-- Name: pacs_viewer_states_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_viewer_states_id_seq', 1, false);


--
-- Name: pacs_visit_sequences_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pacs_visit_sequences_id_seq', 1, false);


--
-- Name: partition_maintenance_configs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.partition_maintenance_configs_id_seq', 80, true);


--
-- Name: patients_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.patients_id_seq', 1, false);


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.refresh_tokens_id_seq', 1, false);


--
-- Name: revoked_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revoked_tokens_id_seq', 1, false);


--
-- Name: role_module_details_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.role_module_details_id_seq', 1657, true);


--
-- Name: roles_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.roles_id_seq', 110, true);


--
-- Name: study_retention_delete_requests_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.study_retention_delete_requests_id_seq', 1, false);


--
-- Name: study_retention_policies_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.study_retention_policies_id_seq', 1, false);


--
-- Name: system_activities_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_activities_id_seq', 1, false);


--
-- Name: user_groups_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.user_groups_id_seq', 6, true);


--
-- Name: user_hospitals_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.user_hospitals_id_seq', 1, true);


--
-- Name: user_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.user_logs_id_seq', 1, false);


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.users_id_seq', 1, true);


--
-- Name: countries countries_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries
    ADD CONSTRAINT countries_name_key UNIQUE (name);


--
-- Name: countries countries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries
    ADD CONSTRAINT countries_pkey PRIMARY KEY (id);


--
-- Name: dicom_server_callback_log dicom_server_callback_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_server_callback_log
    ADD CONSTRAINT dicom_server_callback_log_pkey PRIMARY KEY (id, received_at);


--
-- Name: dicom_server_callback_log_default dicom_server_callback_log_default_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_server_callback_log_default
    ADD CONSTRAINT dicom_server_callback_log_default_pkey PRIMARY KEY (id, received_at);


--
-- Name: dicom_server_unmatched_callback_log dicom_server_unmatched_callback_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_server_unmatched_callback_log
    ADD CONSTRAINT dicom_server_unmatched_callback_log_pkey PRIMARY KEY (id);


--
-- Name: endpoint_permissions endpoint_permissions_http_method_endpoint_pattern_permissio_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.endpoint_permissions
    ADD CONSTRAINT endpoint_permissions_http_method_endpoint_pattern_permissio_key UNIQUE (http_method, endpoint_pattern, permission_code);


--
-- Name: endpoint_permissions endpoint_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.endpoint_permissions
    ADD CONSTRAINT endpoint_permissions_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: hospital_dicom_machines hospital_dicom_machines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_machines
    ADD CONSTRAINT hospital_dicom_machines_pkey PRIMARY KEY (id);


--
-- Name: hospital_dicom_routing_configs hospital_dicom_routing_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_routing_configs
    ADD CONSTRAINT hospital_dicom_routing_configs_pkey PRIMARY KEY (id);


--
-- Name: hospital_dicom_servers hospital_dicom_servers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_servers
    ADD CONSTRAINT hospital_dicom_servers_pkey PRIMARY KEY (id);


--
-- Name: hospital_modality_server_routes hospital_modulight_server_routes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modality_server_routes
    ADD CONSTRAINT hospital_modulight_server_routes_pkey PRIMARY KEY (id);


--
-- Name: hospital_modalities hospital_modulights_hospital_id_modulight_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modalities
    ADD CONSTRAINT hospital_modulights_hospital_id_modulight_id_key UNIQUE (hospital_id, modality_id);


--
-- Name: hospital_modalities hospital_modulights_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modalities
    ADD CONSTRAINT hospital_modulights_pkey PRIMARY KEY (id);


--
-- Name: hospitals hospitals_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospitals
    ADD CONSTRAINT hospitals_code_key UNIQUE (code);


--
-- Name: hospitals hospitals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospitals
    ADD CONSTRAINT hospitals_pkey PRIMARY KEY (id);


--
-- Name: module_details module_details_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_details
    ADD CONSTRAINT module_details_code_key UNIQUE (code);


--
-- Name: module_details module_details_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_details
    ADD CONSTRAINT module_details_pkey PRIMARY KEY (id);


--
-- Name: module_types module_types_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_types
    ADD CONSTRAINT module_types_code_key UNIQUE (code);


--
-- Name: module_types module_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_types
    ADD CONSTRAINT module_types_pkey PRIMARY KEY (id);


--
-- Name: modules modules_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modules
    ADD CONSTRAINT modules_code_key UNIQUE (code);


--
-- Name: modules modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modules
    ADD CONSTRAINT modules_pkey PRIMARY KEY (id);


--
-- Name: modalities modulights_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modalities
    ADD CONSTRAINT modulights_pkey PRIMARY KEY (id);


--
-- Name: oauth2_clients oauth2_clients_client_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth2_clients
    ADD CONSTRAINT oauth2_clients_client_id_key UNIQUE (client_id);


--
-- Name: oauth2_clients oauth2_clients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth2_clients
    ADD CONSTRAINT oauth2_clients_pkey PRIMARY KEY (id);


--
-- Name: pacs_daily_stats pacs_daily_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_daily_stats
    ADD CONSTRAINT pacs_daily_stats_pkey PRIMARY KEY (hospital_id, stat_date, modality_id);


--
-- Name: pacs_patient_sequences pacs_patient_sequences_hospital_id_sequence_year_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_patient_sequences
    ADD CONSTRAINT pacs_patient_sequences_hospital_id_sequence_year_key UNIQUE (hospital_id, sequence_year);


--
-- Name: pacs_patient_sequences pacs_patient_sequences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_patient_sequences
    ADD CONSTRAINT pacs_patient_sequences_pkey PRIMARY KEY (id);


--
-- Name: pacs_realtime_notification_events pacs_realtime_notification_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_realtime_notification_events
    ADD CONSTRAINT pacs_realtime_notification_events_pkey PRIMARY KEY (id, created_at);


--
-- Name: pacs_realtime_notification_events_default pacs_realtime_notification_events_default_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_realtime_notification_events_default
    ADD CONSTRAINT pacs_realtime_notification_events_default_pkey PRIMARY KEY (id, created_at);


--
-- Name: pacs_result_images pacs_result_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_images
    ADD CONSTRAINT pacs_result_images_pkey PRIMARY KEY (id);


--
-- Name: pacs_result_templates pacs_result_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_templates
    ADD CONSTRAINT pacs_result_templates_pkey PRIMARY KEY (id);


--
-- Name: pacs_result_versions pacs_result_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_versions
    ADD CONSTRAINT pacs_result_versions_pkey PRIMARY KEY (id);


--
-- Name: pacs_results pacs_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_results
    ADD CONSTRAINT pacs_results_pkey PRIMARY KEY (id);


--
-- Name: pacs_studies pacs_studies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_studies
    ADD CONSTRAINT pacs_studies_pkey PRIMARY KEY (id);


--
-- Name: pacs_studies_week_cache pacs_studies_week_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_studies_week_cache
    ADD CONSTRAINT pacs_studies_week_cache_pkey PRIMARY KEY (id);


--
-- Name: pacs_system_settings pacs_system_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_system_settings
    ADD CONSTRAINT pacs_system_settings_pkey PRIMARY KEY (setting_key);


--
-- Name: pacs_viewer_states pacs_viewer_states_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_viewer_states
    ADD CONSTRAINT pacs_viewer_states_pkey PRIMARY KEY (id);


--
-- Name: pacs_visit_sequences pacs_visit_sequences_hospital_id_sequence_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_visit_sequences
    ADD CONSTRAINT pacs_visit_sequences_hospital_id_sequence_date_key UNIQUE (hospital_id, sequence_date);


--
-- Name: pacs_visit_sequences pacs_visit_sequences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_visit_sequences
    ADD CONSTRAINT pacs_visit_sequences_pkey PRIMARY KEY (id);


--
-- Name: pacs_worklist_histories pacs_worklist_histories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklist_histories
    ADD CONSTRAINT pacs_worklist_histories_pkey PRIMARY KEY (id, created);


--
-- Name: pacs_worklist_histories_default pacs_worklist_histories_default_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklist_histories_default
    ADD CONSTRAINT pacs_worklist_histories_default_pkey PRIMARY KEY (id, created);


--
-- Name: pacs_worklist_study_links pacs_worklist_study_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklist_study_links
    ADD CONSTRAINT pacs_worklist_study_links_pkey PRIMARY KEY (id);


--
-- Name: pacs_worklists pacs_worklists_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklists
    ADD CONSTRAINT pacs_worklists_pkey PRIMARY KEY (id);


--
-- Name: pacs_worklists_week_cache pacs_worklists_week_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklists_week_cache
    ADD CONSTRAINT pacs_worklists_week_cache_pkey PRIMARY KEY (id);


--
-- Name: partition_maintenance_configs partition_maintenance_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.partition_maintenance_configs
    ADD CONSTRAINT partition_maintenance_configs_pkey PRIMARY KEY (id);


--
-- Name: patients patients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patients
    ADD CONSTRAINT patients_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: revoked_tokens revoked_tokens_jti_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revoked_tokens
    ADD CONSTRAINT revoked_tokens_jti_key UNIQUE (jti);


--
-- Name: revoked_tokens revoked_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revoked_tokens
    ADD CONSTRAINT revoked_tokens_pkey PRIMARY KEY (id);


--
-- Name: role_module_details role_module_details_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_module_details
    ADD CONSTRAINT role_module_details_pkey PRIMARY KEY (id);


--
-- Name: role_module_details role_module_details_role_id_module_detail_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_module_details
    ADD CONSTRAINT role_module_details_role_id_module_detail_id_key UNIQUE (role_id, module_detail_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: study_retention_delete_requests study_retention_delete_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_delete_requests
    ADD CONSTRAINT study_retention_delete_requests_pkey PRIMARY KEY (id, created_at);


--
-- Name: study_retention_delete_requests_default study_retention_delete_requests_default_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_delete_requests_default
    ADD CONSTRAINT study_retention_delete_requests_default_pkey PRIMARY KEY (id, created_at);


--
-- Name: study_retention_policies study_retention_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_policies
    ADD CONSTRAINT study_retention_policies_pkey PRIMARY KEY (id);


--
-- Name: system_activities system_activities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_activities
    ADD CONSTRAINT system_activities_pkey PRIMARY KEY (id, created);


--
-- Name: system_activities_default system_activities_default_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_activities_default
    ADD CONSTRAINT system_activities_default_pkey PRIMARY KEY (id, created);


--
-- Name: user_groups user_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_groups
    ADD CONSTRAINT user_groups_pkey PRIMARY KEY (id);


--
-- Name: user_groups user_groups_user_id_role_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_groups
    ADD CONSTRAINT user_groups_user_id_role_id_key UNIQUE (user_id, role_id);


--
-- Name: user_hospitals user_hospitals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_hospitals
    ADD CONSTRAINT user_hospitals_pkey PRIMARY KEY (id);


--
-- Name: user_hospitals user_hospitals_user_id_hospital_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_hospitals
    ADD CONSTRAINT user_hospitals_user_id_hospital_id_key UNIQUE (user_id, hospital_id);


--
-- Name: user_logs user_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_logs
    ADD CONSTRAINT user_logs_pkey PRIMARY KEY (id, created);


--
-- Name: user_logs_default user_logs_default_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_logs_default
    ADD CONSTRAINT user_logs_default_pkey PRIMARY KEY (id, created);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: hospital_dicom_machines ux_hdm_id_hospital_modality; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_machines
    ADD CONSTRAINT ux_hdm_id_hospital_modality UNIQUE (id, hospital_id, modality_id);


--
-- Name: idx_callback_log_hospital_server_received; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_callback_log_hospital_server_received ON ONLY public.dicom_server_callback_log USING btree (hospital_id, dicom_server_id, received_at DESC, id DESC) WHERE (dicom_server_id IS NOT NULL);


--
-- Name: dicom_server_callback_log_def_hospital_id_dicom_server_id_r_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dicom_server_callback_log_def_hospital_id_dicom_server_id_r_idx ON public.dicom_server_callback_log_default USING btree (hospital_id, dicom_server_id, received_at DESC, id DESC) WHERE (dicom_server_id IS NOT NULL);


--
-- Name: idx_callback_log_hospital_accession; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_callback_log_hospital_accession ON ONLY public.dicom_server_callback_log USING btree (hospital_id, accession_number) WHERE (accession_number IS NOT NULL);


--
-- Name: dicom_server_callback_log_defa_hospital_id_accession_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dicom_server_callback_log_defa_hospital_id_accession_number_idx ON public.dicom_server_callback_log_default USING btree (hospital_id, accession_number) WHERE (accession_number IS NOT NULL);


--
-- Name: idx_callback_log_hospital_received; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_callback_log_hospital_received ON ONLY public.dicom_server_callback_log USING btree (hospital_id, received_at DESC, id DESC);


--
-- Name: dicom_server_callback_log_defaul_hospital_id_received_at_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dicom_server_callback_log_defaul_hospital_id_received_at_id_idx ON public.dicom_server_callback_log_default USING btree (hospital_id, received_at DESC, id DESC);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_countries_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_countries_active ON public.countries USING btree (is_active, status);


--
-- Name: idx_endpoint_permissions_method_pattern_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_endpoint_permissions_method_pattern_active ON public.endpoint_permissions USING btree (http_method, endpoint_pattern, is_active);


--
-- Name: idx_endpoint_permissions_required_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_endpoint_permissions_required_scope ON public.endpoint_permissions USING btree (required_scope);


--
-- Name: idx_hdm_hospital_modality_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hdm_hospital_modality_active ON public.hospital_dicom_machines USING btree (hospital_id, modality_id, is_active, id DESC);


--
-- Name: idx_hdrc_hospital_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hdrc_hospital_active ON public.hospital_dicom_routing_configs USING btree (hospital_id, is_active, id DESC);


--
-- Name: idx_hdrc_hospital_server_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hdrc_hospital_server_active ON public.hospital_dicom_routing_configs USING btree (hospital_id, dicom_server_id, is_active, id DESC);


--
-- Name: idx_hdrc_package_built_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hdrc_package_built_hospital ON public.hospital_dicom_routing_configs USING btree (hospital_id, package_built_at DESC) WHERE (package_built_at IS NOT NULL);


--
-- Name: idx_hds_ae_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hds_ae_trgm ON public.hospital_dicom_servers USING gin (lower((COALESCE(ae_title, ''::character varying))::text) public.gin_trgm_ops);


--
-- Name: idx_hds_ip_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hds_ip_trgm ON public.hospital_dicom_servers USING gin (lower((ip_address)::text) public.gin_trgm_ops);


--
-- Name: idx_hds_name_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hds_name_trgm ON public.hospital_dicom_servers USING gin (lower((name)::text) public.gin_trgm_ops);


--
-- Name: idx_hmsr_config_active_modality_machine; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hmsr_config_active_modality_machine ON public.hospital_modality_server_routes USING btree (routing_config_id, is_active, modality_id, machine_id, id);


--
-- Name: idx_hmsr_hospital_active_id_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hmsr_hospital_active_id_desc ON public.hospital_modality_server_routes USING btree (hospital_id, is_active, id DESC);


--
-- Name: idx_hmsr_hospital_modality_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hmsr_hospital_modality_active ON public.hospital_modality_server_routes USING btree (hospital_id, modality_id, is_active, id);


--
-- Name: idx_hmsr_hospital_modality_active_machine; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hmsr_hospital_modality_active_machine ON public.hospital_modality_server_routes USING btree (hospital_id, modality_id, is_active, machine_id, id);


--
-- Name: idx_hospital_dicom_servers_hospital_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hospital_dicom_servers_hospital_active ON public.hospital_dicom_servers USING btree (hospital_id, is_active, id DESC);


--
-- Name: idx_hospital_dicom_servers_result_key_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hospital_dicom_servers_result_key_hospital ON public.hospital_dicom_servers USING btree (hospital_id, id) WHERE ((is_active = 1) AND (pacs_result_api_key_hash IS NOT NULL) AND (btrim(pacs_result_api_key_hash) <> ''::text));


--
-- Name: idx_hospital_modalities_hospital_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hospital_modalities_hospital_active ON public.hospital_modalities USING btree (hospital_id, is_active, modality_id);


--
-- Name: idx_hospital_modalities_modality_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hospital_modalities_modality_active ON public.hospital_modalities USING btree (modality_id, is_active, hospital_id);


--
-- Name: idx_hospitals_active_created_at_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hospitals_active_created_at_desc ON public.hospitals USING btree (is_active, created_at DESC, id DESC);


--
-- Name: idx_hospitals_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hospitals_created_by ON public.hospitals USING btree (created_by);


--
-- Name: idx_hospitals_logo_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hospitals_logo_updated_at ON public.hospitals USING btree (logo_updated_at) WHERE ((logo_path IS NOT NULL) AND (is_active = 1));


--
-- Name: idx_hospitals_modified_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hospitals_modified_by ON public.hospitals USING btree (modified_by);


--
-- Name: idx_modalities_active_id_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modalities_active_id_desc ON public.modalities USING btree (is_active, id DESC);


--
-- Name: idx_modalities_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modalities_created_by ON public.modalities USING btree (created_by);


--
-- Name: idx_modalities_modified_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modalities_modified_by ON public.modalities USING btree (modified_by);


--
-- Name: idx_module_details_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_module_details_code ON public.module_details USING btree (code);


--
-- Name: idx_module_types_active_display_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_module_types_active_display_order ON public.module_types USING btree (is_active, display_order, id);


--
-- Name: idx_modules_active_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modules_active_type ON public.modules USING btree (is_active, module_type_id);


--
-- Name: idx_oauth2_clients_dicom_server_callback; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oauth2_clients_dicom_server_callback ON public.oauth2_clients USING btree (dicom_server_id) WHERE ((dicom_server_id IS NOT NULL) AND ((client_type)::text = 'CONFIDENTIAL'::text) AND (is_active = true));


--
-- Name: idx_pacs_realtime_events_hospital_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_realtime_events_hospital_created ON ONLY public.pacs_realtime_notification_events USING btree (hospital_id, created_at DESC, id DESC);


--
-- Name: idx_pacs_realtime_events_hospital_cursor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_realtime_events_hospital_cursor ON ONLY public.pacs_realtime_notification_events USING btree (hospital_id, id);


--
-- Name: idx_pacs_result_images_hospital_result_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_result_images_hospital_result_active ON public.pacs_result_images USING btree (hospital_id, result_id, is_active, sort_order, id);


--
-- Name: idx_pacs_result_images_hospital_study_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_result_images_hospital_study_active ON public.pacs_result_images USING btree (hospital_id, study_id, is_active, sort_order, id) WHERE (study_id IS NOT NULL);


--
-- Name: idx_pacs_result_images_hospital_worklist_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_result_images_hospital_worklist_active ON public.pacs_result_images USING btree (hospital_id, worklist_id, is_active, sort_order, id) WHERE (worklist_id IS NOT NULL);


--
-- Name: idx_pacs_result_templates_hospital_modality; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_result_templates_hospital_modality ON public.pacs_result_templates USING btree (hospital_id, modality_id, is_active, template_name);


--
-- Name: idx_pacs_result_templates_scope_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_result_templates_scope_active ON public.pacs_result_templates USING btree (hospital_id, modality_id, is_active, lower((template_name)::text), id DESC);


--
-- Name: idx_pacs_result_versions_hospital_result_changed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_result_versions_hospital_result_changed ON public.pacs_result_versions USING btree (hospital_id, result_id, changed_at DESC, id DESC);


--
-- Name: idx_pacs_results_hospital_patient_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_results_hospital_patient_created ON public.pacs_results USING btree (hospital_id, patient_id, created_at DESC, id DESC) WHERE ((is_active = 1) AND (patient_id IS NOT NULL));


--
-- Name: idx_pacs_results_hospital_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_results_hospital_status_created ON public.pacs_results USING btree (hospital_id, status, created_at DESC, id DESC) WHERE (is_active = 1);


--
-- Name: idx_pacs_results_patient_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_results_patient_hospital ON public.pacs_results USING btree (patient_id, hospital_id) WHERE (patient_id IS NOT NULL);


--
-- Name: idx_pacs_results_study_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_results_study_hospital ON public.pacs_results USING btree (study_id, hospital_id) WHERE (study_id IS NOT NULL);


--
-- Name: idx_pacs_results_worklist_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_results_worklist_hospital ON public.pacs_results USING btree (worklist_id, hospital_id) WHERE (worklist_id IS NOT NULL);


--
-- Name: idx_pacs_studies_hospital_accession; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_hospital_accession ON public.pacs_studies USING btree (hospital_id, accession_number) WHERE (accession_number IS NOT NULL);


--
-- Name: idx_pacs_studies_hospital_dicom_server_study; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_hospital_dicom_server_study ON public.pacs_studies USING btree (hospital_id, dicom_server_study_id) WHERE (dicom_server_study_id IS NOT NULL);


--
-- Name: idx_pacs_studies_hospital_modality_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_hospital_modality_date ON public.pacs_studies USING btree (hospital_id, modality_id, study_date DESC, id DESC) WHERE ((is_active = 1) AND (modality_id IS NOT NULL));


--
-- Name: idx_pacs_studies_hospital_patient_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_hospital_patient_date ON public.pacs_studies USING btree (hospital_id, patient_id, study_date DESC, id DESC) WHERE (is_active = 1);


--
-- Name: idx_pacs_studies_hospital_received; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_hospital_received ON public.pacs_studies USING btree (hospital_id, received_at DESC, id DESC) WHERE ((is_active = 1) AND (received_at IS NOT NULL));


--
-- Name: idx_pacs_studies_hospital_reference_visit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_hospital_reference_visit ON public.pacs_studies USING btree (hospital_id, reference_visit_code) WHERE ((is_active = 1) AND (reference_visit_code IS NOT NULL));


--
-- Name: idx_pacs_studies_hospital_study_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_hospital_study_date ON public.pacs_studies USING btree (hospital_id, study_date DESC, id DESC) WHERE ((is_active = 1) AND (study_date IS NOT NULL));


--
-- Name: idx_pacs_studies_week_cache_hospital_accession; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_week_cache_hospital_accession ON public.pacs_studies_week_cache USING btree (hospital_id, accession_number) WHERE (accession_number IS NOT NULL);


--
-- Name: idx_pacs_studies_week_cache_hospital_modality_received; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_week_cache_hospital_modality_received ON public.pacs_studies_week_cache USING btree (hospital_id, modality_id, received_at DESC, id DESC) WHERE (modality_id IS NOT NULL);


--
-- Name: idx_pacs_studies_week_cache_hospital_patient_received; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_week_cache_hospital_patient_received ON public.pacs_studies_week_cache USING btree (hospital_id, patient_id, received_at DESC, id DESC);


--
-- Name: idx_pacs_studies_week_cache_hospital_received; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_week_cache_hospital_received ON public.pacs_studies_week_cache USING btree (hospital_id, received_at DESC, id DESC) WHERE (received_at IS NOT NULL);


--
-- Name: idx_pacs_studies_week_cache_hospital_study_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_studies_week_cache_hospital_study_date ON public.pacs_studies_week_cache USING btree (hospital_id, study_date DESC, id DESC) WHERE (study_date IS NOT NULL);


--
-- Name: idx_pacs_viewer_states_accession_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_viewer_states_accession_scope ON public.pacs_viewer_states USING btree (hospital_id, accession_number, state_type, modified_at DESC) WHERE ((is_active = 1) AND (accession_number IS NOT NULL));


--
-- Name: idx_pacs_viewer_states_patient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_viewer_states_patient ON public.pacs_viewer_states USING btree (hospital_id, patient_id, modified_at DESC) WHERE (is_active = 1);


--
-- Name: idx_pacs_worklist_study_links_hospital_study_linked_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklist_study_links_hospital_study_linked_desc ON public.pacs_worklist_study_links USING btree (hospital_id, study_id, linked_at DESC);


--
-- Name: idx_pacs_worklist_study_links_primary_study_latest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklist_study_links_primary_study_latest ON public.pacs_worklist_study_links USING btree (hospital_id, study_id, linked_at DESC, id DESC) INCLUDE (worklist_id) WHERE (is_primary = 1);


--
-- Name: idx_pacs_worklists_hospital_modality_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_hospital_modality_status ON public.pacs_worklists USING btree (hospital_id, modality_id, status, id DESC) WHERE (modality_id IS NOT NULL);


--
-- Name: idx_pacs_worklists_hospital_notification_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_hospital_notification_created ON public.pacs_worklists USING btree (hospital_id, created_at DESC, created DESC, id DESC);


--
-- Name: idx_pacs_worklists_hospital_patient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_hospital_patient ON public.pacs_worklists USING btree (hospital_id, patient_id, id DESC);


--
-- Name: idx_pacs_worklists_hospital_route_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_hospital_route_status ON public.pacs_worklists USING btree (hospital_id, dicom_route_id, status, id DESC) WHERE (dicom_route_id IS NOT NULL);


--
-- Name: idx_pacs_worklists_hospital_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_hospital_status_created ON public.pacs_worklists USING btree (hospital_id, status, created_at DESC, id DESC);


--
-- Name: idx_pacs_worklists_hospital_status_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_hospital_status_scheduled ON public.pacs_worklists USING btree (hospital_id, status, scheduled_date DESC, id DESC) WHERE (scheduled_date IS NOT NULL);


--
-- Name: idx_pacs_worklists_week_cache_hospital_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_week_cache_hospital_created ON public.pacs_worklists_week_cache USING btree (hospital_id, created_at DESC, id DESC);


--
-- Name: idx_pacs_worklists_week_cache_hospital_modality_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_week_cache_hospital_modality_status ON public.pacs_worklists_week_cache USING btree (hospital_id, modality_id, status, created_at DESC, id DESC) WHERE (modality_id IS NOT NULL);


--
-- Name: idx_pacs_worklists_week_cache_hospital_patient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_week_cache_hospital_patient ON public.pacs_worklists_week_cache USING btree (hospital_id, patient_id, created_at DESC, id DESC);


--
-- Name: idx_pacs_worklists_week_cache_hospital_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_week_cache_hospital_status_created ON public.pacs_worklists_week_cache USING btree (hospital_id, status, created_at DESC, id DESC);


--
-- Name: idx_pacs_worklists_week_cache_visit_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pacs_worklists_week_cache_visit_code ON public.pacs_worklists_week_cache USING btree (hospital_id, lower((visit_code)::text)) WHERE ((visit_code IS NOT NULL) AND (btrim((visit_code)::text) <> ''::text));


--
-- Name: idx_patients_first_name_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_patients_first_name_trgm ON public.patients USING gin (first_name public.gin_trgm_ops);


--
-- Name: idx_patients_hospital_active_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_patients_hospital_active_id ON public.patients USING btree (hospital_id, is_active, id DESC);


--
-- Name: idx_patients_hospital_hn; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_patients_hospital_hn ON public.patients USING btree (hospital_id, lower((patient_hn)::text)) WHERE ((patient_hn IS NOT NULL) AND (btrim((patient_hn)::text) <> ''::text));


--
-- Name: idx_patients_hospital_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_patients_hospital_name ON public.patients USING btree (hospital_id, lower((first_name)::text), lower((last_name)::text), id DESC);


--
-- Name: idx_patients_last_name_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_patients_last_name_trgm ON public.patients USING gin (last_name public.gin_trgm_ops);


--
-- Name: idx_refresh_tokens_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_expires ON public.refresh_tokens USING btree (expires_at);


--
-- Name: idx_refresh_tokens_user_client_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_user_client_active ON public.refresh_tokens USING btree (user_id, client_id, revoked_at, expires_at);


--
-- Name: idx_refresh_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_user_id ON public.refresh_tokens USING btree (user_id);


--
-- Name: idx_retention_requests_server_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_retention_requests_server_hospital ON ONLY public.study_retention_delete_requests USING btree (dicom_server_id, hospital_id) WHERE (dicom_server_id IS NOT NULL);


--
-- Name: idx_retention_requests_study_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_retention_requests_study_hospital ON ONLY public.study_retention_delete_requests USING btree (study_id, hospital_id) WHERE (study_id IS NOT NULL);


--
-- Name: idx_revoked_tokens_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_revoked_tokens_expires ON public.revoked_tokens USING btree (expires_at);


--
-- Name: idx_revoked_tokens_jti_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_revoked_tokens_jti_expires ON public.revoked_tokens USING btree (jti, expires_at);


--
-- Name: idx_role_module_details_module_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_module_details_module_role ON public.role_module_details USING btree (module_detail_id, role_id);


--
-- Name: idx_roles_active_created_at_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_roles_active_created_at_desc ON public.roles USING btree (is_active, created_at DESC, id DESC);


--
-- Name: idx_study_retention_delete_requests_purge_after; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_study_retention_delete_requests_purge_after ON ONLY public.study_retention_delete_requests USING btree (purge_after) WHERE (purge_after IS NOT NULL);


--
-- Name: idx_study_retention_delete_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_study_retention_delete_requests_status ON ONLY public.study_retention_delete_requests USING btree (hospital_id, status, updated_at DESC, id DESC);


--
-- Name: idx_study_retention_delete_requests_study_latest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_study_retention_delete_requests_study_latest ON ONLY public.study_retention_delete_requests USING btree (hospital_id, study_id, created_at DESC, id DESC);


--
-- Name: idx_study_retention_policies_auto_delete; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_study_retention_policies_auto_delete ON public.study_retention_policies USING btree (hospital_id, enabled, auto_delete, is_active);


--
-- Name: idx_study_retention_policies_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_study_retention_policies_scope ON public.study_retention_policies USING btree (hospital_id, dicom_server_id, modality_id, enabled, is_active);


--
-- Name: idx_system_activities_created_by_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_activities_created_by_created ON ONLY public.system_activities USING btree (created_by, created DESC, id DESC) WHERE (created_by IS NOT NULL);


--
-- Name: idx_system_activities_created_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_activities_created_id ON ONLY public.system_activities USING btree (created DESC, id DESC);


--
-- Name: idx_system_activities_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_activities_status_created ON ONLY public.system_activities USING btree (status, created DESC, id DESC);


--
-- Name: idx_unmatched_callback_accession; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_unmatched_callback_accession ON public.dicom_server_unmatched_callback_log USING btree (accession_number) WHERE (accession_number IS NOT NULL);


--
-- Name: idx_unmatched_callback_received; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_unmatched_callback_received ON public.dicom_server_unmatched_callback_log USING btree (received_at DESC, id DESC);


--
-- Name: idx_user_groups_role_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_groups_role_active ON public.user_groups USING btree (role_id, is_active);


--
-- Name: idx_user_groups_user_role_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_groups_user_role_active ON public.user_groups USING btree (user_id, role_id, is_active);


--
-- Name: idx_user_hospitals_active_hospital_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_hospitals_active_hospital_user ON public.user_hospitals USING btree (is_active, hospital_id, user_id);


--
-- Name: idx_user_hospitals_hospital_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_hospitals_hospital_id ON public.user_hospitals USING btree (hospital_id);


--
-- Name: idx_user_hospitals_user_active_default; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_hospitals_user_active_default ON public.user_hospitals USING btree (user_id, is_active, is_default, hospital_id);


--
-- Name: idx_user_hospitals_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_hospitals_user_id ON public.user_hospitals USING btree (user_id);


--
-- Name: idx_user_logs_created_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_logs_created_id ON ONLY public.user_logs USING btree (created DESC, id DESC);


--
-- Name: idx_user_logs_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_logs_user_created ON ONLY public.user_logs USING btree (user_id, created DESC, id DESC) WHERE (user_id IS NOT NULL);


--
-- Name: idx_users_active_created_at_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_active_created_at_desc ON public.users USING btree (is_active, created_at DESC, id DESC);


--
-- Name: idx_users_active_id_desc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_active_id_desc ON public.users USING btree (is_active, id DESC);


--
-- Name: idx_users_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_created_by ON public.users USING btree (created_by);


--
-- Name: idx_users_email_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_email_trgm ON public.users USING gin (email public.gin_trgm_ops);


--
-- Name: idx_users_first_name_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_first_name_trgm ON public.users USING gin (first_name public.gin_trgm_ops);


--
-- Name: idx_users_last_name_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_last_name_trgm ON public.users USING gin (last_name public.gin_trgm_ops);


--
-- Name: idx_users_modified_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_modified_by ON public.users USING btree (modified_by);


--
-- Name: idx_users_telephone_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_telephone_trgm ON public.users USING gin (telephone public.gin_trgm_ops);


--
-- Name: idx_users_username_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_username_trgm ON public.users USING gin (username public.gin_trgm_ops);


--
-- Name: idx_worklist_histories_hospital_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_worklist_histories_hospital_created ON ONLY public.pacs_worklist_histories USING btree (hospital_id, created DESC, id DESC);


--
-- Name: idx_worklist_histories_hospital_worklist_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_worklist_histories_hospital_worklist_created ON ONLY public.pacs_worklist_histories USING btree (hospital_id, worklist_id, created DESC, id DESC);


--
-- Name: idx_worklist_histories_patient_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_worklist_histories_patient_created ON ONLY public.pacs_worklist_histories USING btree (hospital_id, patient_id, created DESC, id DESC);


--
-- Name: idx_worklist_histories_purge_after; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_worklist_histories_purge_after ON ONLY public.pacs_worklist_histories USING btree (purge_after) WHERE (purge_after IS NOT NULL);


--
-- Name: pacs_realtime_notification_events_default_hospital_id_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pacs_realtime_notification_events_default_hospital_id_id_idx ON public.pacs_realtime_notification_events_default USING btree (hospital_id, id);


--
-- Name: pacs_realtime_notification_events_hospital_id_created_at_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pacs_realtime_notification_events_hospital_id_created_at_id_idx ON public.pacs_realtime_notification_events_default USING btree (hospital_id, created_at DESC, id DESC);


--
-- Name: pacs_worklist_histories_defau_hospital_id_patient_id_create_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pacs_worklist_histories_defau_hospital_id_patient_id_create_idx ON public.pacs_worklist_histories_default USING btree (hospital_id, patient_id, created DESC, id DESC);


--
-- Name: pacs_worklist_histories_defau_hospital_id_worklist_id_creat_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pacs_worklist_histories_defau_hospital_id_worklist_id_creat_idx ON public.pacs_worklist_histories_default USING btree (hospital_id, worklist_id, created DESC, id DESC);


--
-- Name: pacs_worklist_histories_default_hospital_id_created_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pacs_worklist_histories_default_hospital_id_created_id_idx ON public.pacs_worklist_histories_default USING btree (hospital_id, created DESC, id DESC);


--
-- Name: pacs_worklist_histories_default_purge_after_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pacs_worklist_histories_default_purge_after_idx ON public.pacs_worklist_histories_default USING btree (purge_after) WHERE (purge_after IS NOT NULL);


--
-- Name: study_retention_delete_reques_hospital_id_status_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX study_retention_delete_reques_hospital_id_status_updated_at_idx ON public.study_retention_delete_requests_default USING btree (hospital_id, status, updated_at DESC, id DESC);


--
-- Name: study_retention_delete_reques_hospital_id_study_id_created__idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX study_retention_delete_reques_hospital_id_study_id_created__idx ON public.study_retention_delete_requests_default USING btree (hospital_id, study_id, created_at DESC, id DESC);


--
-- Name: ux_study_retention_delete_requests_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_study_retention_delete_requests_public_id ON ONLY public.study_retention_delete_requests USING btree (public_id, created_at);


--
-- Name: study_retention_delete_requests_defaul_public_id_created_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX study_retention_delete_requests_defaul_public_id_created_at_idx ON public.study_retention_delete_requests_default USING btree (public_id, created_at);


--
-- Name: study_retention_delete_requests_defaul_study_id_hospital_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX study_retention_delete_requests_defaul_study_id_hospital_id_idx ON public.study_retention_delete_requests_default USING btree (study_id, hospital_id) WHERE (study_id IS NOT NULL);


--
-- Name: study_retention_delete_requests_default_purge_after_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX study_retention_delete_requests_default_purge_after_idx ON public.study_retention_delete_requests_default USING btree (purge_after) WHERE (purge_after IS NOT NULL);


--
-- Name: study_retention_delete_requests_dicom_server_id_hospital_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX study_retention_delete_requests_dicom_server_id_hospital_id_idx ON public.study_retention_delete_requests_default USING btree (dicom_server_id, hospital_id) WHERE (dicom_server_id IS NOT NULL);


--
-- Name: system_activities_default_created_by_created_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX system_activities_default_created_by_created_id_idx ON public.system_activities_default USING btree (created_by, created DESC, id DESC) WHERE (created_by IS NOT NULL);


--
-- Name: system_activities_default_created_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX system_activities_default_created_id_idx ON public.system_activities_default USING btree (created DESC, id DESC);


--
-- Name: uq_system_activities_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_system_activities_public_id ON ONLY public.system_activities USING btree (public_id, created);


--
-- Name: system_activities_default_public_id_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX system_activities_default_public_id_created_idx ON public.system_activities_default USING btree (public_id, created);


--
-- Name: system_activities_default_status_created_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX system_activities_default_status_created_id_idx ON public.system_activities_default USING btree (status, created DESC, id DESC);


--
-- Name: uq_hospital_dicom_machines_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_hospital_dicom_machines_public_id ON public.hospital_dicom_machines USING btree (public_id);


--
-- Name: uq_hospital_dicom_routing_configs_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_hospital_dicom_routing_configs_public_id ON public.hospital_dicom_routing_configs USING btree (public_id);


--
-- Name: uq_hospital_dicom_servers_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_hospital_dicom_servers_public_id ON public.hospital_dicom_servers USING btree (public_id);


--
-- Name: uq_hospital_modality_server_routes_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_hospital_modality_server_routes_public_id ON public.hospital_modality_server_routes USING btree (public_id);


--
-- Name: uq_hospitals_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_hospitals_public_id ON public.hospitals USING btree (public_id);


--
-- Name: uq_modalities_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_modalities_public_id ON public.modalities USING btree (public_id);


--
-- Name: uq_module_details_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_module_details_public_id ON public.module_details USING btree (public_id);


--
-- Name: uq_module_types_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_module_types_public_id ON public.module_types USING btree (public_id);


--
-- Name: uq_modules_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_modules_public_id ON public.modules USING btree (public_id);


--
-- Name: uq_pacs_result_templates_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pacs_result_templates_public_id ON public.pacs_result_templates USING btree (public_id);


--
-- Name: uq_pacs_studies_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pacs_studies_public_id ON public.pacs_studies USING btree (public_id);


--
-- Name: uq_pacs_worklists_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pacs_worklists_public_id ON public.pacs_worklists USING btree (public_id);


--
-- Name: uq_patients_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_patients_public_id ON public.patients USING btree (public_id);


--
-- Name: uq_roles_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_roles_public_id ON public.roles USING btree (public_id);


--
-- Name: uq_unmatched_callback_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_unmatched_callback_public_id ON public.dicom_server_unmatched_callback_log USING btree (public_id);


--
-- Name: uq_user_logs_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_user_logs_public_id ON ONLY public.user_logs USING btree (public_id, created);


--
-- Name: uq_users_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_users_public_id ON public.users USING btree (public_id);


--
-- Name: user_logs_default_created_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_logs_default_created_id_idx ON public.user_logs_default USING btree (created DESC, id DESC);


--
-- Name: user_logs_default_public_id_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX user_logs_default_public_id_created_idx ON public.user_logs_default USING btree (public_id, created);


--
-- Name: user_logs_default_user_id_created_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_logs_default_user_id_created_id_idx ON public.user_logs_default USING btree (user_id, created DESC, id DESC) WHERE (user_id IS NOT NULL);


--
-- Name: ux_hdm_active_machine_endpoint; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hdm_active_machine_endpoint ON public.hospital_dicom_machines USING btree (hospital_id, modality_id, lower((machine_ae_title)::text), lower((machine_host)::text), machine_port) WHERE (is_active = 1);


--
-- Name: ux_hdrc_hospital_server_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hdrc_hospital_server_active ON public.hospital_dicom_routing_configs USING btree (hospital_id, dicom_server_id) WHERE ((is_active = 1) AND (dicom_server_id IS NOT NULL));


--
-- Name: ux_hdrc_id_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hdrc_id_hospital ON public.hospital_dicom_routing_configs USING btree (id, hospital_id);


--
-- Name: ux_hds_hospital_id_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hds_hospital_id_id ON public.hospital_dicom_servers USING btree (hospital_id, id);


--
-- Name: ux_hds_id_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hds_id_hospital ON public.hospital_dicom_servers USING btree (id, hospital_id);


--
-- Name: ux_hmsr_active_machine; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hmsr_active_machine ON public.hospital_modality_server_routes USING btree (hospital_id, machine_id) WHERE ((is_active = 1) AND (machine_id IS NOT NULL));


--
-- Name: ux_hmsr_config_machine_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hmsr_config_machine_active ON public.hospital_modality_server_routes USING btree (routing_config_id, machine_id) WHERE ((is_active = 1) AND (routing_config_id IS NOT NULL));


--
-- Name: ux_hmsr_id_hospital_modality; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hmsr_id_hospital_modality ON public.hospital_modality_server_routes USING btree (id, hospital_id, modality_id);


--
-- Name: ux_hospital_dicom_servers_hospital_endpoint_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hospital_dicom_servers_hospital_endpoint_active ON public.hospital_dicom_servers USING btree (hospital_id, lower((ip_address)::text), port, COALESCE(dicom_port, 4242), lower((COALESCE(ae_title, ''::character varying))::text)) WHERE (is_active = 1);


--
-- Name: ux_hospital_dicom_servers_hospital_name_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hospital_dicom_servers_hospital_name_active ON public.hospital_dicom_servers USING btree (hospital_id, lower((name)::text)) WHERE (is_active = 1);


--
-- Name: ux_hospitals_abbr_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hospitals_abbr_active ON public.hospitals USING btree (lower((abbr)::text)) WHERE (is_active = 1);


--
-- Name: ux_hospitals_active_visit_code_token; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_hospitals_active_visit_code_token ON public.hospitals USING btree (public.hospital_visit_code_token((abbr)::text)) WHERE ((is_active = 1) AND (public.hospital_visit_code_token((abbr)::text) <> ''::text));


--
-- Name: ux_modalities_abbr_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_modalities_abbr_active ON public.modalities USING btree (lower((abbr)::text)) WHERE (is_active = 1);


--
-- Name: ux_modalities_name_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_modalities_name_active ON public.modalities USING btree (lower((name)::text)) WHERE (is_active = 1);


--
-- Name: ux_pacs_result_images_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_result_images_public_id ON public.pacs_result_images USING btree (image_public_id);


--
-- Name: ux_pacs_result_templates_name_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_result_templates_name_active ON public.pacs_result_templates USING btree (hospital_id, modality_id, lower((template_name)::text)) WHERE (is_active = 1);


--
-- Name: ux_pacs_result_versions_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_result_versions_public_id ON public.pacs_result_versions USING btree (public_id);


--
-- Name: ux_pacs_result_versions_result_version; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_result_versions_result_version ON public.pacs_result_versions USING btree (result_id, version_no);


--
-- Name: ux_pacs_results_hospital_study_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_results_hospital_study_active ON public.pacs_results USING btree (hospital_id, study_id) WHERE ((is_active = 1) AND (study_id IS NOT NULL));


--
-- Name: ux_pacs_results_hospital_worklist_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_results_hospital_worklist_active ON public.pacs_results USING btree (hospital_id, worklist_id) WHERE ((is_active = 1) AND (worklist_id IS NOT NULL));


--
-- Name: ux_pacs_results_id_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_results_id_hospital ON public.pacs_results USING btree (id, hospital_id);


--
-- Name: ux_pacs_results_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_results_public_id ON public.pacs_results USING btree (result_public_id);


--
-- Name: ux_pacs_studies_hospital_study_instance_uid; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_studies_hospital_study_instance_uid ON public.pacs_studies USING btree (hospital_id, study_instance_uid);


--
-- Name: ux_pacs_studies_id_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_studies_id_hospital ON public.pacs_studies USING btree (id, hospital_id);


--
-- Name: ux_pacs_studies_week_cache_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_studies_week_cache_public_id ON public.pacs_studies_week_cache USING btree (public_id);


--
-- Name: ux_pacs_studies_week_cache_study_uid; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_studies_week_cache_study_uid ON public.pacs_studies_week_cache USING btree (hospital_id, study_instance_uid);


--
-- Name: ux_pacs_viewer_states_current_study; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_viewer_states_current_study ON public.pacs_viewer_states USING btree (hospital_id, study_id, state_type) WHERE ((is_active = 1) AND (worklist_id IS NULL) AND (study_id IS NOT NULL));


--
-- Name: ux_pacs_viewer_states_current_study_uid; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_viewer_states_current_study_uid ON public.pacs_viewer_states USING btree (hospital_id, study_instance_uid, state_type) WHERE ((is_active = 1) AND (worklist_id IS NULL) AND (study_id IS NULL) AND (study_instance_uid IS NOT NULL));


--
-- Name: ux_pacs_viewer_states_current_worklist; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_viewer_states_current_worklist ON public.pacs_viewer_states USING btree (hospital_id, worklist_id, state_type) WHERE ((is_active = 1) AND (worklist_id IS NOT NULL));


--
-- Name: ux_pacs_viewer_states_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_viewer_states_public_id ON public.pacs_viewer_states USING btree (public_id);


--
-- Name: ux_pacs_worklist_study_links_primary_worklist; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_worklist_study_links_primary_worklist ON public.pacs_worklist_study_links USING btree (hospital_id, worklist_id) WHERE (is_primary = 1);


--
-- Name: ux_pacs_worklist_study_links_worklist_study; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_worklist_study_links_worklist_study ON public.pacs_worklist_study_links USING btree (hospital_id, worklist_id, study_id);


--
-- Name: ux_pacs_worklists_hospital_visit_code_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_worklists_hospital_visit_code_lower ON public.pacs_worklists USING btree (hospital_id, lower((visit_code)::text)) WHERE ((visit_code IS NOT NULL) AND (btrim((visit_code)::text) <> ''::text));


--
-- Name: ux_pacs_worklists_id_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_worklists_id_hospital ON public.pacs_worklists USING btree (id, hospital_id);


--
-- Name: ux_pacs_worklists_week_cache_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_pacs_worklists_week_cache_public_id ON public.pacs_worklists_week_cache USING btree (public_id);


--
-- Name: ux_partition_maintenance_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_partition_maintenance_parent ON public.partition_maintenance_configs USING btree (parent_schema, parent_table);


--
-- Name: ux_patients_hospital_patient_uid_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_patients_hospital_patient_uid_lower ON public.patients USING btree (hospital_id, lower((patient_uid)::text));


--
-- Name: ux_patients_id_hospital; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_patients_id_hospital ON public.patients USING btree (id, hospital_id);


--
-- Name: ux_roles_active_name_global; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_roles_active_name_global ON public.roles USING btree (lower(TRIM(BOTH FROM COALESCE(name, ''::character varying)))) WHERE (is_active = 1);


--
-- Name: ux_study_retention_policies_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_study_retention_policies_public_id ON public.study_retention_policies USING btree (public_id);


--
-- Name: ux_study_retention_policies_scope_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_study_retention_policies_scope_active ON public.study_retention_policies USING btree (COALESCE(hospital_id, (0)::bigint), COALESCE(dicom_server_id, (0)::bigint), COALESCE(modality_id, (0)::bigint)) WHERE (is_active = 1);


--
-- Name: ux_unmatched_callback_dedupe; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_unmatched_callback_dedupe ON public.dicom_server_unmatched_callback_log USING btree (dedupe_key) WHERE (dedupe_key IS NOT NULL);


--
-- Name: ux_unmatched_callback_original; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_unmatched_callback_original ON public.dicom_server_unmatched_callback_log USING btree (original_callback_log_id) WHERE (original_callback_log_id IS NOT NULL);


--
-- Name: ux_user_hospitals_one_default_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_user_hospitals_one_default_active ON public.user_hospitals USING btree (user_id) WHERE ((is_default = true) AND (is_active = 1));


--
-- Name: dicom_server_callback_log_def_hospital_id_dicom_server_id_r_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_callback_log_hospital_server_received ATTACH PARTITION public.dicom_server_callback_log_def_hospital_id_dicom_server_id_r_idx;


--
-- Name: dicom_server_callback_log_defa_hospital_id_accession_number_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_callback_log_hospital_accession ATTACH PARTITION public.dicom_server_callback_log_defa_hospital_id_accession_number_idx;


--
-- Name: dicom_server_callback_log_defaul_hospital_id_received_at_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_callback_log_hospital_received ATTACH PARTITION public.dicom_server_callback_log_defaul_hospital_id_received_at_id_idx;


--
-- Name: dicom_server_callback_log_default_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.dicom_server_callback_log_pkey ATTACH PARTITION public.dicom_server_callback_log_default_pkey;


--
-- Name: pacs_realtime_notification_events_default_hospital_id_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_pacs_realtime_events_hospital_cursor ATTACH PARTITION public.pacs_realtime_notification_events_default_hospital_id_id_idx;


--
-- Name: pacs_realtime_notification_events_default_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.pacs_realtime_notification_events_pkey ATTACH PARTITION public.pacs_realtime_notification_events_default_pkey;


--
-- Name: pacs_realtime_notification_events_hospital_id_created_at_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_pacs_realtime_events_hospital_created ATTACH PARTITION public.pacs_realtime_notification_events_hospital_id_created_at_id_idx;


--
-- Name: pacs_worklist_histories_defau_hospital_id_patient_id_create_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_worklist_histories_patient_created ATTACH PARTITION public.pacs_worklist_histories_defau_hospital_id_patient_id_create_idx;


--
-- Name: pacs_worklist_histories_defau_hospital_id_worklist_id_creat_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_worklist_histories_hospital_worklist_created ATTACH PARTITION public.pacs_worklist_histories_defau_hospital_id_worklist_id_creat_idx;


--
-- Name: pacs_worklist_histories_default_hospital_id_created_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_worklist_histories_hospital_created ATTACH PARTITION public.pacs_worklist_histories_default_hospital_id_created_id_idx;


--
-- Name: pacs_worklist_histories_default_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.pacs_worklist_histories_pkey ATTACH PARTITION public.pacs_worklist_histories_default_pkey;


--
-- Name: pacs_worklist_histories_default_purge_after_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_worklist_histories_purge_after ATTACH PARTITION public.pacs_worklist_histories_default_purge_after_idx;


--
-- Name: study_retention_delete_reques_hospital_id_status_updated_at_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_study_retention_delete_requests_status ATTACH PARTITION public.study_retention_delete_reques_hospital_id_status_updated_at_idx;


--
-- Name: study_retention_delete_reques_hospital_id_study_id_created__idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_study_retention_delete_requests_study_latest ATTACH PARTITION public.study_retention_delete_reques_hospital_id_study_id_created__idx;


--
-- Name: study_retention_delete_requests_defaul_public_id_created_at_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.ux_study_retention_delete_requests_public_id ATTACH PARTITION public.study_retention_delete_requests_defaul_public_id_created_at_idx;


--
-- Name: study_retention_delete_requests_defaul_study_id_hospital_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_retention_requests_study_hospital ATTACH PARTITION public.study_retention_delete_requests_defaul_study_id_hospital_id_idx;


--
-- Name: study_retention_delete_requests_default_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.study_retention_delete_requests_pkey ATTACH PARTITION public.study_retention_delete_requests_default_pkey;


--
-- Name: study_retention_delete_requests_default_purge_after_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_study_retention_delete_requests_purge_after ATTACH PARTITION public.study_retention_delete_requests_default_purge_after_idx;


--
-- Name: study_retention_delete_requests_dicom_server_id_hospital_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_retention_requests_server_hospital ATTACH PARTITION public.study_retention_delete_requests_dicom_server_id_hospital_id_idx;


--
-- Name: system_activities_default_created_by_created_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_system_activities_created_by_created ATTACH PARTITION public.system_activities_default_created_by_created_id_idx;


--
-- Name: system_activities_default_created_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_system_activities_created_id ATTACH PARTITION public.system_activities_default_created_id_idx;


--
-- Name: system_activities_default_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.system_activities_pkey ATTACH PARTITION public.system_activities_default_pkey;


--
-- Name: system_activities_default_public_id_created_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.uq_system_activities_public_id ATTACH PARTITION public.system_activities_default_public_id_created_idx;


--
-- Name: system_activities_default_status_created_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_system_activities_status_created ATTACH PARTITION public.system_activities_default_status_created_id_idx;


--
-- Name: user_logs_default_created_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_user_logs_created_id ATTACH PARTITION public.user_logs_default_created_id_idx;


--
-- Name: user_logs_default_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.user_logs_pkey ATTACH PARTITION public.user_logs_default_pkey;


--
-- Name: user_logs_default_public_id_created_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.uq_user_logs_public_id ATTACH PARTITION public.user_logs_default_public_id_created_idx;


--
-- Name: user_logs_default_user_id_created_id_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_user_logs_user_created ATTACH PARTITION public.user_logs_default_user_id_created_id_idx;


--
-- Name: pacs_result_images trg_pacs_result_images_set_scope; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pacs_result_images_set_scope BEFORE INSERT OR UPDATE OF result_id ON public.pacs_result_images FOR EACH ROW EXECUTE FUNCTION public.pacs_set_result_image_scope();


--
-- Name: pacs_results trg_pacs_results_capture_version; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pacs_results_capture_version BEFORE UPDATE ON public.pacs_results FOR EACH ROW EXECUTE FUNCTION public.pacs_capture_result_version();


--
-- Name: pacs_studies trg_pacs_studies_sync_audit_timestamps; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pacs_studies_sync_audit_timestamps BEFORE INSERT OR UPDATE ON public.pacs_studies FOR EACH ROW EXECUTE FUNCTION public.pacs_sync_legacy_audit_timestamps();


--
-- Name: pacs_studies trg_pacs_studies_week_cache_sync; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pacs_studies_week_cache_sync AFTER INSERT OR DELETE OR UPDATE ON public.pacs_studies FOR EACH ROW EXECUTE FUNCTION public.pacs_study_week_cache_trigger();


--
-- Name: pacs_worklist_study_links trg_pacs_worklist_study_links_sync_worklist; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pacs_worklist_study_links_sync_worklist AFTER INSERT OR UPDATE OF study_id, is_primary ON public.pacs_worklist_study_links FOR EACH ROW EXECUTE FUNCTION public.pacs_sync_primary_link_to_worklist();


--
-- Name: pacs_worklists trg_pacs_worklists_sync_primary_study; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pacs_worklists_sync_primary_study AFTER INSERT OR UPDATE OF study_id ON public.pacs_worklists FOR EACH ROW EXECUTE FUNCTION public.pacs_sync_worklist_primary_study();


--
-- Name: pacs_worklists trg_pacs_worklists_week_cache_sync; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pacs_worklists_week_cache_sync AFTER INSERT OR DELETE OR UPDATE ON public.pacs_worklists FOR EACH ROW EXECUTE FUNCTION public.pacs_worklist_week_cache_trigger();


--
-- Name: patients trg_patients_sync_audit_timestamps; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_patients_sync_audit_timestamps BEFORE INSERT OR UPDATE ON public.patients FOR EACH ROW EXECUTE FUNCTION public.pacs_sync_legacy_audit_timestamps();


--
-- Name: dicom_server_callback_log fk_callback_log_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.dicom_server_callback_log
    ADD CONSTRAINT fk_callback_log_hospital FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: dicom_server_callback_log fk_callback_log_server_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.dicom_server_callback_log
    ADD CONSTRAINT fk_callback_log_server_hospital FOREIGN KEY (dicom_server_id, hospital_id) REFERENCES public.hospital_dicom_servers(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: hospital_dicom_machines fk_hdm_hospital_modality; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_machines
    ADD CONSTRAINT fk_hdm_hospital_modality FOREIGN KEY (hospital_id, modality_id) REFERENCES public.hospital_modalities(hospital_id, modality_id);


--
-- Name: hospital_dicom_routing_configs fk_hdrc_dicom_server_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_routing_configs
    ADD CONSTRAINT fk_hdrc_dicom_server_hospital FOREIGN KEY (dicom_server_id, hospital_id) REFERENCES public.hospital_dicom_servers(id, hospital_id);


--
-- Name: hospital_modality_server_routes fk_hmsr_hospital_modality; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modality_server_routes
    ADD CONSTRAINT fk_hmsr_hospital_modality FOREIGN KEY (hospital_id, modality_id) REFERENCES public.hospital_modalities(hospital_id, modality_id);


--
-- Name: hospital_modality_server_routes fk_hmsr_machine; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modality_server_routes
    ADD CONSTRAINT fk_hmsr_machine FOREIGN KEY (machine_id) REFERENCES public.hospital_dicom_machines(id);


--
-- Name: hospital_modality_server_routes fk_hmsr_machine_hospital_modality; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modality_server_routes
    ADD CONSTRAINT fk_hmsr_machine_hospital_modality FOREIGN KEY (machine_id, hospital_id, modality_id) REFERENCES public.hospital_dicom_machines(id, hospital_id, modality_id);


--
-- Name: hospital_modality_server_routes fk_hmsr_routing_config; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modality_server_routes
    ADD CONSTRAINT fk_hmsr_routing_config FOREIGN KEY (routing_config_id) REFERENCES public.hospital_dicom_routing_configs(id);


--
-- Name: hospital_modality_server_routes fk_hmsr_routing_config_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modality_server_routes
    ADD CONSTRAINT fk_hmsr_routing_config_hospital FOREIGN KEY (routing_config_id, hospital_id) REFERENCES public.hospital_dicom_routing_configs(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: oauth2_clients fk_oauth2_clients_dicom_server; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth2_clients
    ADD CONSTRAINT fk_oauth2_clients_dicom_server FOREIGN KEY (dicom_server_id) REFERENCES public.hospital_dicom_servers(id);


--
-- Name: pacs_daily_stats fk_pacs_daily_stats_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_daily_stats
    ADD CONSTRAINT fk_pacs_daily_stats_hospital FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_result_templates fk_pacs_result_templates_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_templates
    ADD CONSTRAINT fk_pacs_result_templates_created_by FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: pacs_result_templates fk_pacs_result_templates_modified_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_templates
    ADD CONSTRAINT fk_pacs_result_templates_modified_by FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: pacs_results fk_pacs_results_template; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_results
    ADD CONSTRAINT fk_pacs_results_template FOREIGN KEY (template_id) REFERENCES public.pacs_result_templates(id) DEFERRABLE;


--
-- Name: pacs_studies fk_pacs_studies_dicom_server_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_studies
    ADD CONSTRAINT fk_pacs_studies_dicom_server_hospital FOREIGN KEY (dicom_server_id, hospital_id) REFERENCES public.hospital_dicom_servers(id, hospital_id);


--
-- Name: pacs_studies fk_pacs_studies_hospital_modality; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_studies
    ADD CONSTRAINT fk_pacs_studies_hospital_modality FOREIGN KEY (hospital_id, modality_id) REFERENCES public.hospital_modalities(hospital_id, modality_id);


--
-- Name: pacs_studies fk_pacs_studies_patient_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_studies
    ADD CONSTRAINT fk_pacs_studies_patient_hospital FOREIGN KEY (patient_id, hospital_id) REFERENCES public.patients(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_viewer_states fk_pacs_viewer_states_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_viewer_states
    ADD CONSTRAINT fk_pacs_viewer_states_created_by FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: pacs_viewer_states fk_pacs_viewer_states_deleted_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_viewer_states
    ADD CONSTRAINT fk_pacs_viewer_states_deleted_by FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: pacs_viewer_states fk_pacs_viewer_states_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_viewer_states
    ADD CONSTRAINT fk_pacs_viewer_states_hospital FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: pacs_viewer_states fk_pacs_viewer_states_modality; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_viewer_states
    ADD CONSTRAINT fk_pacs_viewer_states_modality FOREIGN KEY (modality_id) REFERENCES public.modalities(id);


--
-- Name: pacs_viewer_states fk_pacs_viewer_states_modified_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_viewer_states
    ADD CONSTRAINT fk_pacs_viewer_states_modified_by FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: pacs_worklists fk_pacs_worklists_route_hospital_modality; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklists
    ADD CONSTRAINT fk_pacs_worklists_route_hospital_modality FOREIGN KEY (dicom_route_id, hospital_id, modality_id) REFERENCES public.hospital_modality_server_routes(id, hospital_id, modality_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: CONSTRAINT fk_pacs_worklists_route_hospital_modality ON pacs_worklists; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT fk_pacs_worklists_route_hospital_modality ON public.pacs_worklists IS 'New worklists must reference a route for the same hospital and modality.';


--
-- Name: pacs_realtime_notification_events fk_realtime_events_hospital_restrict; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.pacs_realtime_notification_events
    ADD CONSTRAINT fk_realtime_events_hospital_restrict FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_realtime_notification_events fk_realtime_events_study_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.pacs_realtime_notification_events
    ADD CONSTRAINT fk_realtime_events_study_hospital FOREIGN KEY (study_id, hospital_id) REFERENCES public.pacs_studies(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_realtime_notification_events fk_realtime_events_worklist_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.pacs_realtime_notification_events
    ADD CONSTRAINT fk_realtime_events_worklist_hospital FOREIGN KEY (worklist_id, hospital_id) REFERENCES public.pacs_worklists(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: refresh_tokens fk_refresh_tokens_rotated_from; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_rotated_from FOREIGN KEY (rotated_from_id) REFERENCES public.refresh_tokens(id);


--
-- Name: pacs_result_images fk_result_images_result_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_images
    ADD CONSTRAINT fk_result_images_result_hospital FOREIGN KEY (result_id, hospital_id) REFERENCES public.pacs_results(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_result_images fk_result_images_study_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_images
    ADD CONSTRAINT fk_result_images_study_hospital FOREIGN KEY (study_id, hospital_id) REFERENCES public.pacs_studies(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_result_images fk_result_images_worklist_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_images
    ADD CONSTRAINT fk_result_images_worklist_hospital FOREIGN KEY (worklist_id, hospital_id) REFERENCES public.pacs_worklists(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_result_versions fk_result_versions_result_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_versions
    ADD CONSTRAINT fk_result_versions_result_hospital FOREIGN KEY (result_id, hospital_id) REFERENCES public.pacs_results(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_results fk_results_patient_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_results
    ADD CONSTRAINT fk_results_patient_hospital FOREIGN KEY (patient_id, hospital_id) REFERENCES public.patients(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_results fk_results_study_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_results
    ADD CONSTRAINT fk_results_study_hospital FOREIGN KEY (study_id, hospital_id) REFERENCES public.pacs_studies(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_results fk_results_worklist_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_results
    ADD CONSTRAINT fk_results_worklist_hospital FOREIGN KEY (worklist_id, hospital_id) REFERENCES public.pacs_worklists(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: study_retention_delete_requests fk_retention_requests_server_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.study_retention_delete_requests
    ADD CONSTRAINT fk_retention_requests_server_hospital FOREIGN KEY (dicom_server_id, hospital_id) REFERENCES public.hospital_dicom_servers(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: study_retention_delete_requests fk_retention_requests_study_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.study_retention_delete_requests
    ADD CONSTRAINT fk_retention_requests_study_hospital FOREIGN KEY (study_id, hospital_id) REFERENCES public.pacs_studies(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: revoked_tokens fk_revoked_tokens_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revoked_tokens
    ADD CONSTRAINT fk_revoked_tokens_hospital FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: revoked_tokens fk_revoked_tokens_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revoked_tokens
    ADD CONSTRAINT fk_revoked_tokens_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: pacs_viewer_states fk_viewer_states_patient_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_viewer_states
    ADD CONSTRAINT fk_viewer_states_patient_hospital FOREIGN KEY (patient_id, hospital_id) REFERENCES public.patients(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_viewer_states fk_viewer_states_study_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_viewer_states
    ADD CONSTRAINT fk_viewer_states_study_hospital FOREIGN KEY (study_id, hospital_id) REFERENCES public.pacs_studies(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_viewer_states fk_viewer_states_worklist_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_viewer_states
    ADD CONSTRAINT fk_viewer_states_worklist_hospital FOREIGN KEY (worklist_id, hospital_id) REFERENCES public.pacs_worklists(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_worklist_histories fk_worklist_histories_patient_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.pacs_worklist_histories
    ADD CONSTRAINT fk_worklist_histories_patient_hospital FOREIGN KEY (patient_id, hospital_id) REFERENCES public.patients(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_worklist_histories fk_worklist_histories_worklist_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.pacs_worklist_histories
    ADD CONSTRAINT fk_worklist_histories_worklist_hospital FOREIGN KEY (worklist_id, hospital_id) REFERENCES public.pacs_worklists(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_worklist_study_links fk_worklist_study_links_study_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklist_study_links
    ADD CONSTRAINT fk_worklist_study_links_study_hospital FOREIGN KEY (study_id, hospital_id) REFERENCES public.pacs_studies(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_worklist_study_links fk_worklist_study_links_worklist_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklist_study_links
    ADD CONSTRAINT fk_worklist_study_links_worklist_hospital FOREIGN KEY (worklist_id, hospital_id) REFERENCES public.pacs_worklists(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_worklists fk_worklists_patient_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklists
    ADD CONSTRAINT fk_worklists_patient_hospital FOREIGN KEY (patient_id, hospital_id) REFERENCES public.patients(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: pacs_worklists fk_worklists_study_hospital; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklists
    ADD CONSTRAINT fk_worklists_study_hospital FOREIGN KEY (study_id, hospital_id) REFERENCES public.pacs_studies(id, hospital_id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: hospital_dicom_machines hospital_dicom_machines_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_machines
    ADD CONSTRAINT hospital_dicom_machines_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: hospital_dicom_machines hospital_dicom_machines_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_machines
    ADD CONSTRAINT hospital_dicom_machines_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: hospital_dicom_machines hospital_dicom_machines_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_machines
    ADD CONSTRAINT hospital_dicom_machines_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: hospital_dicom_routing_configs hospital_dicom_routing_configs_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_routing_configs
    ADD CONSTRAINT hospital_dicom_routing_configs_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: hospital_dicom_routing_configs hospital_dicom_routing_configs_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_routing_configs
    ADD CONSTRAINT hospital_dicom_routing_configs_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: hospital_dicom_routing_configs hospital_dicom_routing_configs_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_routing_configs
    ADD CONSTRAINT hospital_dicom_routing_configs_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: hospital_dicom_servers hospital_dicom_servers_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_servers
    ADD CONSTRAINT hospital_dicom_servers_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: hospital_dicom_servers hospital_dicom_servers_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_servers
    ADD CONSTRAINT hospital_dicom_servers_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: hospital_dicom_servers hospital_dicom_servers_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_dicom_servers
    ADD CONSTRAINT hospital_dicom_servers_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: hospital_modality_server_routes hospital_modulight_server_routes_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modality_server_routes
    ADD CONSTRAINT hospital_modulight_server_routes_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: hospital_modality_server_routes hospital_modulight_server_routes_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modality_server_routes
    ADD CONSTRAINT hospital_modulight_server_routes_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: hospital_modalities hospital_modulights_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modalities
    ADD CONSTRAINT hospital_modulights_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: hospital_modalities hospital_modulights_modulight_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hospital_modalities
    ADD CONSTRAINT hospital_modulights_modulight_id_fkey FOREIGN KEY (modality_id) REFERENCES public.modalities(id);


--
-- Name: module_details module_details_module_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.module_details
    ADD CONSTRAINT module_details_module_id_fkey FOREIGN KEY (module_id) REFERENCES public.modules(id);


--
-- Name: modules modules_module_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modules
    ADD CONSTRAINT modules_module_type_id_fkey FOREIGN KEY (module_type_id) REFERENCES public.module_types(id);


--
-- Name: pacs_patient_sequences pacs_patient_sequences_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_patient_sequences
    ADD CONSTRAINT pacs_patient_sequences_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: pacs_result_templates pacs_result_templates_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_templates
    ADD CONSTRAINT pacs_result_templates_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: pacs_result_templates pacs_result_templates_modality_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_result_templates
    ADD CONSTRAINT pacs_result_templates_modality_id_fkey FOREIGN KEY (modality_id) REFERENCES public.modalities(id);


--
-- Name: pacs_results pacs_results_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_results
    ADD CONSTRAINT pacs_results_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: pacs_results pacs_results_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_results
    ADD CONSTRAINT pacs_results_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: pacs_results pacs_results_modality_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_results
    ADD CONSTRAINT pacs_results_modality_id_fkey FOREIGN KEY (modality_id) REFERENCES public.modalities(id);


--
-- Name: pacs_studies pacs_studies_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_studies
    ADD CONSTRAINT pacs_studies_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: pacs_studies pacs_studies_uploaded_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_studies
    ADD CONSTRAINT pacs_studies_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES public.users(id);


--
-- Name: pacs_visit_sequences pacs_visit_sequences_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_visit_sequences
    ADD CONSTRAINT pacs_visit_sequences_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: pacs_worklist_histories pacs_worklist_histories_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.pacs_worklist_histories
    ADD CONSTRAINT pacs_worklist_histories_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: pacs_worklist_study_links pacs_worklist_study_links_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklist_study_links
    ADD CONSTRAINT pacs_worklist_study_links_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: pacs_worklist_study_links pacs_worklist_study_links_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklist_study_links
    ADD CONSTRAINT pacs_worklist_study_links_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: pacs_worklists pacs_worklists_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklists
    ADD CONSTRAINT pacs_worklists_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: pacs_worklists pacs_worklists_modality_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_worklists
    ADD CONSTRAINT pacs_worklists_modality_id_fkey FOREIGN KEY (modality_id) REFERENCES public.modalities(id);


--
-- Name: patients patients_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patients
    ADD CONSTRAINT patients_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: refresh_tokens refresh_tokens_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: refresh_tokens refresh_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: role_module_details role_module_details_module_detail_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_module_details
    ADD CONSTRAINT role_module_details_module_detail_id_fkey FOREIGN KEY (module_detail_id) REFERENCES public.module_details(id);


--
-- Name: role_module_details role_module_details_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_module_details
    ADD CONSTRAINT role_module_details_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: study_retention_delete_requests study_retention_delete_requests_approved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.study_retention_delete_requests
    ADD CONSTRAINT study_retention_delete_requests_approved_by_fkey FOREIGN KEY (approved_by) REFERENCES public.users(id);


--
-- Name: study_retention_delete_requests study_retention_delete_requests_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.study_retention_delete_requests
    ADD CONSTRAINT study_retention_delete_requests_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: study_retention_delete_requests study_retention_delete_requests_modality_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.study_retention_delete_requests
    ADD CONSTRAINT study_retention_delete_requests_modality_id_fkey FOREIGN KEY (modality_id) REFERENCES public.modalities(id);


--
-- Name: study_retention_delete_requests study_retention_delete_requests_policy_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.study_retention_delete_requests
    ADD CONSTRAINT study_retention_delete_requests_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES public.study_retention_policies(id) ON DELETE SET NULL;


--
-- Name: study_retention_delete_requests study_retention_delete_requests_rejected_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.study_retention_delete_requests
    ADD CONSTRAINT study_retention_delete_requests_rejected_by_fkey FOREIGN KEY (rejected_by) REFERENCES public.users(id);


--
-- Name: study_retention_delete_requests study_retention_delete_requests_requested_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.study_retention_delete_requests
    ADD CONSTRAINT study_retention_delete_requests_requested_by_fkey FOREIGN KEY (requested_by) REFERENCES public.users(id);


--
-- Name: study_retention_policies study_retention_policies_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_policies
    ADD CONSTRAINT study_retention_policies_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: study_retention_policies study_retention_policies_dicom_server_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_policies
    ADD CONSTRAINT study_retention_policies_dicom_server_id_fkey FOREIGN KEY (dicom_server_id) REFERENCES public.hospital_dicom_servers(id);


--
-- Name: study_retention_policies study_retention_policies_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_policies
    ADD CONSTRAINT study_retention_policies_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: study_retention_policies study_retention_policies_modality_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_policies
    ADD CONSTRAINT study_retention_policies_modality_id_fkey FOREIGN KEY (modality_id) REFERENCES public.modalities(id);


--
-- Name: study_retention_policies study_retention_policies_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_retention_policies
    ADD CONSTRAINT study_retention_policies_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: user_groups user_groups_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_groups
    ADD CONSTRAINT user_groups_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: user_groups user_groups_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_groups
    ADD CONSTRAINT user_groups_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_hospitals user_hospitals_hospital_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_hospitals
    ADD CONSTRAINT user_hospitals_hospital_id_fkey FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);


--
-- Name: user_hospitals user_hospitals_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_hospitals
    ADD CONSTRAINT user_hospitals_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_logs user_logs_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.user_logs
    ADD CONSTRAINT user_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- PostgreSQL database dump complete
--

\unrestrict XtAWpzamsUKeUxgOxqpyKuwnroKaINC4fPr3KYFh2ghP5RIrr0IxpoGRSRfzDq9

