ALTER TABLE hospital_dicom_servers
    ADD COLUMN IF NOT EXISTS public_endpoint_include_port BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE hospital_dicom_servers
SET public_endpoint_include_port = TRUE
WHERE public_endpoint_include_port IS NULL;

COMMENT ON COLUMN hospital_dicom_servers.public_endpoint_include_port IS
    'When true, derived public DICOM server URLs include :port. Turn off when a reverse proxy exposes the endpoint without an explicit port.';
