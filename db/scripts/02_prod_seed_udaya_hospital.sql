BEGIN;

SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '30min';
SET LOCAL client_min_messages = 'notice';

CREATE TEMP TABLE seed_primary_modalities (
  id bigint PRIMARY KEY,
  abbr text NOT NULL UNIQUE,
  name text NOT NULL,
  ae_suffix text NOT NULL,
  machine_port integer NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_primary_modalities (id, abbr, name, ae_suffix, machine_port) VALUES
  (1, 'CR', 'Computed Radiography', 'CR', 10401),
  (2, 'CT', 'Computed Tomography', 'CT', 10402),
  (3, 'DR', 'Digital Radiography', 'DR', 10403),
  (4, 'DX', 'Diagnostic X-Ray', 'DX', 10404),
  (5, 'MG', 'Mammography', 'MG', 10405),
  (6, 'MR', 'Magnetic Resonance Imaging', 'MR', 10406),
  (7, 'OT', 'Other', 'OT', 10407),
  (8, 'PT', 'Positron Emission Tomography', 'PT', 10408),
  (9, 'US', 'Ultrasound', 'US', 10409),
  (10, 'XA', 'X-Ray Angiography', 'XA', 10410),
  (11, 'XC', 'External-Camera Photography', 'XC', 10411);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.users WHERE id = 1 AND username = 'admin') THEN
    RAISE EXCEPTION 'Run 01_prod_truncate_clean_core.sql first. Admin user id=1 is missing.';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM public.roles WHERE id = 1) THEN
    RAISE EXCEPTION 'Run 01_prod_truncate_clean_core.sql first. Admin role id=1 is missing.';
  END IF;
END $$;

INSERT INTO public.hospitals (
  id,
  code,
  name,
  name_other,
  dicomweb_base_url,
  timezone,
  is_active,
  created_by,
  created,
  modified_by,
  modified,
  created_at,
  modified_at,
  abbr,
  public_id
) VALUES (
  1,
  'UDAYA',
  'UDAYA Hospital',
  NULL,
  NULL,
  'Asia/Phnom_Penh',
  1,
  1,
  NOW(),
  1,
  NOW(),
  NOW(),
  NOW(),
  'UDAYA',
  gen_random_uuid()
);

SELECT setval(pg_get_serial_sequence('public.hospitals', 'id'), 1, true);

INSERT INTO public.user_hospitals (
  id,
  user_id,
  hospital_id,
  is_default,
  is_active,
  created_by,
  created,
  modified_by,
  modified,
  created_at,
  modified_at
) VALUES (
  1,
  1,
  1,
  true,
  1,
  1,
  NOW(),
  1,
  NOW(),
  NOW(),
  NOW()
);

SELECT setval(pg_get_serial_sequence('public.user_hospitals', 'id'), 1, true);

INSERT INTO public.user_groups (
  user_id,
  role_id,
  is_active,
  created_by,
  created,
  modified_by,
  modified,
  created_at,
  modified_at
)
SELECT 1, r.id, 1, 1, NOW(), 1, NOW(), NOW(), NOW()
FROM public.roles r
WHERE r.id IN (1, 4, 5, 34, 70, 110)
ORDER BY r.id;

SELECT setval(pg_get_serial_sequence('public.user_groups', 'id'), COALESCE((SELECT max(id) FROM public.user_groups), 1), true);

INSERT INTO public.modalities (
  id,
  name,
  is_active,
  created_by,
  modified_by,
  created_at,
  modified_at,
  abbr,
  public_id
)
SELECT id, name, 1, 1, 1, NOW(), NOW(), abbr, gen_random_uuid()
FROM seed_primary_modalities
ORDER BY id;

SELECT setval(pg_get_serial_sequence('public.modalities', 'id'), 11, true);

INSERT INTO public.hospital_modalities (
  id,
  hospital_id,
  modality_id,
  is_active,
  created_by,
  modified_by,
  created_at,
  modified_at
)
SELECT id, 1, id, 1, 1, 1, NOW(), NOW()
FROM seed_primary_modalities
ORDER BY id;

SELECT setval(pg_get_serial_sequence('public.hospital_modalities', 'id'), 11, true);

INSERT INTO public.hospital_dicom_servers (
  id,
  hospital_id,
  name,
  ip_address,
  port,
  ae_title,
  username,
  password,
  is_active,
  created_by,
  modified_by,
  created_at,
  modified_at,
  viewer_base_url,
  dicom_port,
  storage_directory,
  index_directory,
  maximum_storage_size,
  maximum_patient_count,
  remote_access_allowed,
  http_server_enabled,
  enable_http_compression,
  ssl_enabled,
  authentication_enabled,
  authorization_enabled,
  authorization_root,
  authorization_checked_level,
  dicom_always_allow_echo,
  dicom_always_allow_find,
  dicom_always_allow_get,
  dicom_always_allow_move,
  dicom_always_allow_store,
  dicom_check_called_aet,
  dicom_tls_enabled,
  dicom_scp_timeout,
  dicom_peers_json,
  worklists_enabled,
  worklists_database,
  plugins_paths,
  pacs_api_callback_base_url,
  pacs_result_api_key_hash,
  dicomweb_path,
  public_id,
  public_health_check_url
) VALUES (
  1,
  1,
  'UDAYA DICOM Server',
  '127.0.0.1',
  8042,
  'UDAYA_DCM_SERVER',
  NULL,
  NULL,
  1,
  1,
  1,
  NOW(),
  NOW(),
  'http://127.0.0.1:3005',
  4242,
  '/var/lib/udaya_dicom_server/db',
  '/var/lib/udaya_dicom_server/db',
  0,
  0,
  true,
  true,
  true,
  false,
  true,
  true,
  '/authorization',
  'studies',
  true,
  true,
  true,
  true,
  true,
  false,
  false,
  30,
  '{}',
  true,
  '/var/lib/udaya_dicom_server/worklists',
  '/usr/share/udaya_dicom_server/plugins' || chr(10) || '/usr/local/share/udaya_dicom_server/plugins',
  NULL,
  NULL,
  '/dicom-web',
  gen_random_uuid(),
  'http://127.0.0.1:8042/system'
);

SELECT setval(pg_get_serial_sequence('public.hospital_dicom_servers', 'id'), 1, true);

INSERT INTO public.hospital_dicom_routing_configs (
  id,
  hospital_id,
  is_active,
  created_by,
  modified_by,
  created_at,
  modified_at,
  dicom_server_id,
  public_id
) VALUES (
  1,
  1,
  1,
  1,
  1,
  NOW(),
  NOW(),
  1,
  gen_random_uuid()
);

SELECT setval(pg_get_serial_sequence('public.hospital_dicom_routing_configs', 'id'), 1, true);

INSERT INTO public.hospital_dicom_machines (
  id,
  hospital_id,
  modality_id,
  machine_name,
  machine_ae_title,
  machine_host,
  machine_port,
  is_active,
  created_by,
  modified_by,
  created_at,
  modified_at,
  public_id
)
SELECT
  id,
  1,
  id,
  'UDAYA ' || abbr || ' Room 1',
  'UDAYA_' || ae_suffix || '01',
  '127.0.0.1',
  machine_port,
  1,
  1,
  1,
  NOW(),
  NOW(),
  gen_random_uuid()
FROM seed_primary_modalities
ORDER BY id;

SELECT setval(pg_get_serial_sequence('public.hospital_dicom_machines', 'id'), 11, true);

INSERT INTO public.hospital_modality_server_routes (
  id,
  hospital_id,
  modality_id,
  is_active,
  created_by,
  modified_by,
  created_at,
  modified_at,
  routing_config_id,
  machine_id,
  public_id
)
SELECT
  id,
  1,
  id,
  1,
  1,
  1,
  NOW(),
  NOW(),
  1,
  id,
  gen_random_uuid()
FROM seed_primary_modalities
ORDER BY id;

SELECT setval(pg_get_serial_sequence('public.hospital_modality_server_routes', 'id'), 11, true);

INSERT INTO public.oauth2_clients (
  id,
  client_id,
  client_name,
  client_secret_hash,
  client_type,
  allowed_grant_types,
  allowed_scopes,
  access_token_lifetime_ms,
  refresh_token_lifetime_ms,
  is_active,
  created,
  modified,
  dicom_server_id
) VALUES (
  1,
  'pacs-web',
  'PACS Web Client',
  NULL,
  'PUBLIC',
  'password_login,refresh_token',
  'pacs.api pacs.patient.read pacs.patient.write pacs.study.read pacs.viewer.open user.read user.write',
  900000,
  2592000000,
  true,
  NOW(),
  NOW(),
  NULL
), (
  2,
  'pacs-mobile',
  'PACS Mobile Client',
  NULL,
  'PUBLIC',
  'password_login,refresh_token',
  'pacs.api pacs.patient.read pacs.patient.write pacs.study.read pacs.viewer.open user.read',
  900000,
  2592000000,
  true,
  NOW(),
  NOW(),
  NULL
), (
  3,
  'udaya-dicom-server',
  'UDAYA DICOM Server Callback',
  NULL,
  'CONFIDENTIAL',
  'client_credentials',
  'pacs.api',
  900000,
  2592000000,
  true,
  NOW(),
  NOW(),
  1
);

SELECT setval(pg_get_serial_sequence('public.oauth2_clients', 'id'), 3, true);

SELECT 'hospitals' AS table_name, count(*) AS rows FROM public.hospitals
UNION ALL SELECT 'users', count(*) FROM public.users
UNION ALL SELECT 'user_hospitals', count(*) FROM public.user_hospitals
UNION ALL SELECT 'user_groups', count(*) FROM public.user_groups
UNION ALL SELECT 'roles', count(*) FROM public.roles
UNION ALL SELECT 'role_module_details', count(*) FROM public.role_module_details
UNION ALL SELECT 'modalities', count(*) FROM public.modalities
UNION ALL SELECT 'hospital_modalities', count(*) FROM public.hospital_modalities
UNION ALL SELECT 'hospital_dicom_servers', count(*) FROM public.hospital_dicom_servers
UNION ALL SELECT 'hospital_dicom_machines', count(*) FROM public.hospital_dicom_machines
UNION ALL SELECT 'hospital_dicom_routing_configs', count(*) FROM public.hospital_dicom_routing_configs
UNION ALL SELECT 'hospital_modality_server_routes', count(*) FROM public.hospital_modality_server_routes
UNION ALL SELECT 'oauth2_clients', count(*) FROM public.oauth2_clients
UNION ALL SELECT 'patients', count(*) FROM public.patients
UNION ALL SELECT 'pacs_studies', count(*) FROM public.pacs_studies
UNION ALL SELECT 'pacs_worklists', count(*) FROM public.pacs_worklists
ORDER BY table_name;

COMMIT;
