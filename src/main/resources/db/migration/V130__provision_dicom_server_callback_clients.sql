ALTER TABLE oauth2_clients
    ADD COLUMN IF NOT EXISTS dicom_server_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_oauth2_clients_dicom_server'
    ) THEN
        ALTER TABLE oauth2_clients
            ADD CONSTRAINT fk_oauth2_clients_dicom_server
                FOREIGN KEY (dicom_server_id)
                REFERENCES hospital_dicom_servers(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_oauth2_clients_dicom_server_confidential'
    ) THEN
        ALTER TABLE oauth2_clients
            ADD CONSTRAINT chk_oauth2_clients_dicom_server_confidential
                CHECK (dicom_server_id IS NULL OR client_type = 'CONFIDENTIAL');
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_oauth2_clients_dicom_server_callback
    ON oauth2_clients(dicom_server_id)
    WHERE dicom_server_id IS NOT NULL;

COMMENT ON COLUMN oauth2_clients.dicom_server_id IS
    'Optional DICOM server identity for generated DICOM server callback clients. Used to bind callbacks to the routed PACS destination.';
