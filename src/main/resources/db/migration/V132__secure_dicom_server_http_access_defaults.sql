-- Keep generated DICOM server deployments reachable through real host/IP ports,
-- but require HTTP Basic Auth whenever server credentials already exist.
UPDATE hospital_dicom_servers
SET
    http_server_enabled = TRUE,
    remote_access_allowed = TRUE,
    authentication_enabled = TRUE,
    modified_at = NOW()
WHERE is_active = 1
  AND NULLIF(BTRIM(username), '') IS NOT NULL
  AND NULLIF(BTRIM(password), '') IS NOT NULL
  AND (
      http_server_enabled IS DISTINCT FROM TRUE
      OR remote_access_allowed IS DISTINCT FROM TRUE
      OR authentication_enabled IS DISTINCT FROM TRUE
  );

COMMENT ON COLUMN hospital_dicom_servers.username IS
    'DICOM server HTTP Basic Auth username used by PACS API and generated DICOM server deployment packages.';

COMMENT ON COLUMN hospital_dicom_servers.password IS
    'DICOM server HTTP Basic Auth password used by PACS API and generated DICOM server deployment packages. Keep response DTOs redacted.';
