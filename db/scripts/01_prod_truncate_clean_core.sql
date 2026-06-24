BEGIN;

SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '30min';
SET LOCAL client_min_messages = 'notice';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.users WHERE id = 1 AND username = 'admin') THEN
    RAISE EXCEPTION 'Required admin user id=1 username=admin is missing.';
  END IF;
END $$;

TRUNCATE TABLE
  public.dicom_server_callback_log,
  public.dicom_server_unmatched_callback_log,
  public.pacs_daily_stats,
  public.pacs_patient_sequences,
  public.pacs_realtime_notification_events,
  public.pacs_result_images,
  public.pacs_results,
  public.pacs_result_versions,
  public.pacs_studies,
  public.pacs_studies_week_cache,
  public.pacs_viewer_states,
  public.pacs_visit_sequences,
  public.pacs_worklist_histories,
  public.pacs_worklist_study_links,
  public.pacs_worklists,
  public.pacs_worklists_week_cache,
  public.patients,
  public.refresh_tokens,
  public.revoked_tokens,
  public.study_retention_delete_requests,
  public.system_activities,
  public.user_logs
RESTART IDENTITY CASCADE;

TRUNCATE TABLE
  public.oauth2_clients,
  public.study_retention_policies,
  public.pacs_result_templates,
  public.hospital_modality_server_routes,
  public.hospital_dicom_routing_configs,
  public.hospital_dicom_machines,
  public.hospital_dicom_servers,
  public.hospital_modalities,
  public.user_hospitals,
  public.user_groups,
  public.hospitals,
  public.modalities
RESTART IDENTITY CASCADE;

DELETE FROM public.role_module_details
WHERE role_id NOT IN (1, 4, 5, 34, 70, 110);

DELETE FROM public.roles
WHERE id NOT IN (1, 4, 5, 34, 70, 110);

DELETE FROM public.users
WHERE id <> 1;

SELECT setval(pg_get_serial_sequence('public.users', 'id'), 1, true);
SELECT setval(pg_get_serial_sequence('public.roles', 'id'), COALESCE((SELECT max(id) FROM public.roles), 1), true);
SELECT setval(pg_get_serial_sequence('public.role_module_details', 'id'), COALESCE((SELECT max(id) FROM public.role_module_details), 1), true);

DO $$
DECLARE
  parent_tables text[] := ARRAY[
    'dicom_server_callback_log',
    'pacs_realtime_notification_events',
    'pacs_worklist_histories',
    'study_retention_delete_requests',
    'system_activities',
    'user_logs'
  ];
  parent_name text;
  parent_reg regclass;
  parent_kind "char";
  child_record record;
BEGIN
  FOREACH parent_name IN ARRAY parent_tables LOOP
    parent_reg := to_regclass(format('public.%I', parent_name));
    IF parent_reg IS NULL THEN
      CONTINUE;
    END IF;

    SELECT relkind INTO parent_kind
    FROM pg_class
    WHERE oid = parent_reg;

    IF parent_kind <> 'p' THEN
      CONTINUE;
    END IF;

    FOR child_record IN
      SELECT child_ns.nspname AS child_schema,
             child_cls.relname AS child_table
      FROM pg_inherits i
      JOIN pg_class child_cls ON child_cls.oid = i.inhrelid
      JOIN pg_namespace child_ns ON child_ns.oid = child_cls.relnamespace
      WHERE i.inhparent = parent_reg
        AND child_ns.nspname = 'public'
        AND child_cls.relname <> parent_name || '_default'
        AND (
          child_cls.relname ~ ('^' || parent_name || '_[0-9]{6}$')
          OR child_cls.relname ~ ('^' || parent_name || '_[0-9]{4}$')
        )
    LOOP
      EXECUTE format('DROP TABLE IF EXISTS %I.%I CASCADE',
                     child_record.child_schema,
                     child_record.child_table);
    END LOOP;

    EXECUTE format('CREATE TABLE IF NOT EXISTS public.%I PARTITION OF public.%I DEFAULT',
                   parent_name || '_default',
                   parent_name);
  END LOOP;
END $$;

SELECT 'hospitals' AS table_name, count(*) AS rows FROM public.hospitals
UNION ALL SELECT 'users', count(*) FROM public.users
UNION ALL SELECT 'roles', count(*) FROM public.roles
UNION ALL SELECT 'role_module_details', count(*) FROM public.role_module_details
UNION ALL SELECT 'modalities', count(*) FROM public.modalities
UNION ALL SELECT 'hospital_modalities', count(*) FROM public.hospital_modalities
UNION ALL SELECT 'hospital_dicom_servers', count(*) FROM public.hospital_dicom_servers
UNION ALL SELECT 'hospital_dicom_machines', count(*) FROM public.hospital_dicom_machines
UNION ALL SELECT 'hospital_modality_server_routes', count(*) FROM public.hospital_modality_server_routes
UNION ALL SELECT 'patients', count(*) FROM public.patients
UNION ALL SELECT 'pacs_studies', count(*) FROM public.pacs_studies
UNION ALL SELECT 'pacs_worklists', count(*) FROM public.pacs_worklists
ORDER BY table_name;

COMMIT;
