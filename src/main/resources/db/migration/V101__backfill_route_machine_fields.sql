UPDATE hospital_modality_server_routes route
SET
    machine_ae_title = COALESCE(
        NULLIF(BTRIM(route.machine_ae_title), ''),
        NULLIF(BTRIM(server.ae_title), ''),
        'DICOM_SERVER'
    ),
    machine_host = COALESCE(
        NULLIF(BTRIM(route.machine_host), ''),
        NULLIF(BTRIM(server.ip_address), ''),
        NULLIF(
            SPLIT_PART(
                REGEXP_REPLACE(COALESCE(server.dicom_server_ui_base_url, server.base_url, ''), '^https?://', ''),
                ':',
                1
            ),
            ''
        )
    ),
    machine_port = COALESCE(
        NULLIF(route.machine_port, 0),
        NULLIF(server.dicom_port, 0),
        4242
    ),
    modified_at = NOW()
FROM hospital_dicom_servers server
WHERE route.dicom_server_id = server.id
  AND (
      route.machine_ae_title IS NULL
      OR BTRIM(route.machine_ae_title) = ''
      OR route.machine_host IS NULL
      OR BTRIM(route.machine_host) = ''
      OR route.machine_port IS NULL
      OR route.machine_port <= 0
  );

COMMENT ON COLUMN hospital_modality_server_routes.machine_ae_title IS 'Remote modality machine AE title used in DICOM server DicomModalities. Backfilled from the destination DICOM server for legacy routes.';
COMMENT ON COLUMN hospital_modality_server_routes.machine_host IS 'Remote modality machine host/IP used in DICOM server DicomModalities. Backfilled from the destination DICOM server for legacy routes.';
COMMENT ON COLUMN hospital_modality_server_routes.machine_port IS 'Remote modality machine DICOM port used in DICOM server DicomModalities. Backfilled from the destination DICOM server for legacy routes.';
