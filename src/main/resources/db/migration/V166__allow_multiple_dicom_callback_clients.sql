DROP INDEX IF EXISTS ux_oauth2_clients_dicom_server_callback;

CREATE INDEX IF NOT EXISTS idx_oauth2_clients_dicom_server_callback
    ON oauth2_clients(dicom_server_id)
    WHERE dicom_server_id IS NOT NULL
      AND client_type = 'CONFIDENTIAL'
      AND is_active = TRUE;

COMMENT ON COLUMN oauth2_clients.dicom_server_id IS
    'Optional DICOM server identity for generated DICOM server callback clients. Multiple active client credentials can exist so an older running DICOM server remains valid after a new config build rotates credentials.';
