ALTER TABLE hospital_dicom_servers
    ADD COLUMN IF NOT EXISTS public_health_check_url VARCHAR(1024);

UPDATE hospital_dicom_servers
SET public_health_check_url =
        (CASE WHEN ssl_enabled IS TRUE THEN 'https' ELSE 'http' END
            || '://' || TRIM(ip_address) || ':' || port || '/system')
WHERE NULLIF(BTRIM(public_health_check_url), '') IS NULL
  AND NULLIF(BTRIM(ip_address), '') IS NOT NULL
  AND port IS NOT NULL;

COMMENT ON COLUMN hospital_dicom_servers.public_health_check_url IS
    'Public or API-reachable URL used only for DICOM server health checks. If only host:port is configured, use /system.';
