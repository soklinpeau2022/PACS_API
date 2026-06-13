ALTER TABLE hospital_dicom_servers
    ADD COLUMN IF NOT EXISTS dicom_port INTEGER;

UPDATE hospital_dicom_servers
SET dicom_port = 4242
WHERE dicom_port IS NULL;

ALTER TABLE hospital_dicom_servers
    ALTER COLUMN dicom_port SET DEFAULT 4242;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_hospital_dicom_servers_dicom_port_positive'
    ) THEN
        ALTER TABLE hospital_dicom_servers
            ADD CONSTRAINT chk_hospital_dicom_servers_dicom_port_positive
                CHECK (dicom_port IS NULL OR dicom_port > 0);
    END IF;
END $$;

COMMENT ON COLUMN hospital_dicom_servers.port IS 'DICOMweb / DICOM server REST HTTP port, for example 8042.';
COMMENT ON COLUMN hospital_dicom_servers.dicom_port IS 'Native DICOM C-FIND/C-STORE port, for example 4242.';

DROP INDEX IF EXISTS ux_hospital_dicom_servers_hospital_endpoint_active;

CREATE UNIQUE INDEX IF NOT EXISTS ux_hospital_dicom_servers_hospital_endpoint_active
    ON hospital_dicom_servers (
        hospital_id,
        LOWER(ip_address),
        port,
        COALESCE(dicom_port, 4242),
        LOWER(COALESCE(ae_title, ''))
    )
    WHERE is_active = 1;
