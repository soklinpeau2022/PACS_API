ALTER TABLE hospital_dicom_servers
    ADD COLUMN IF NOT EXISTS dicom_host VARCHAR(255);

UPDATE hospital_dicom_servers
SET dicom_host = NULLIF(BTRIM(dicom_host), '')
WHERE dicom_host IS NOT NULL;

COMMENT ON COLUMN hospital_dicom_servers.dicom_host IS 'Optional native DICOM C-FIND/C-MOVE/C-STORE IP/domain. When null, ip_address is used.';

DROP INDEX IF EXISTS ux_hospital_dicom_servers_hospital_endpoint_active;

CREATE UNIQUE INDEX IF NOT EXISTS ux_hospital_dicom_servers_hospital_endpoint_active
    ON hospital_dicom_servers (
        hospital_id,
        LOWER(ip_address),
        port,
        LOWER(COALESCE(dicom_host, ip_address)),
        COALESCE(dicom_port, 4242),
        LOWER(COALESCE(ae_title, ''))
    )
    WHERE is_active = 1;
