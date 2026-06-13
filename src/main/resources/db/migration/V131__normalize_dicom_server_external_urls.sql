-- DICOM Server URLs must be reachable server endpoints, not Docker-only service aliases.
-- Use the stored host/IP and port as the canonical API-to-DICOM server base URL.
UPDATE hospital_dicom_servers
SET
    base_url = (CASE WHEN COALESCE(ssl_enabled, FALSE) THEN 'https' ELSE 'http' END)
        || '://' || TRIM(ip_address) || ':' || port,
    modified_at = NOW()
WHERE NULLIF(TRIM(ip_address), '') IS NOT NULL
  AND port IS NOT NULL
  AND (
        NULLIF(TRIM(base_url), '') IS NULL
        OR LOWER(TRIM(base_url)) LIKE '%docker.internal%'
        OR LOWER(TRIM(base_url)) ~ '^https?://dicom_server_[a-z0-9-]+(:[0-9]+)?/?$'
  );

UPDATE hospital_dicom_servers
SET
    dicom_server_ui_base_url = (CASE WHEN COALESCE(ssl_enabled, FALSE) THEN 'https' ELSE 'http' END)
        || '://' || TRIM(ip_address) || ':' || port,
    modified_at = NOW()
WHERE NULLIF(TRIM(ip_address), '') IS NOT NULL
  AND port IS NOT NULL
  AND (
        NULLIF(TRIM(dicom_server_ui_base_url), '') IS NULL
        OR LOWER(TRIM(dicom_server_ui_base_url)) LIKE '%docker.internal%'
        OR LOWER(TRIM(dicom_server_ui_base_url)) ~ '^https?://dicom_server_[a-z0-9-]+(:[0-9]+)?/?$'
  );

UPDATE hospital_dicom_servers
SET
    dicomweb_base_url = REGEXP_REPLACE(
        COALESCE(dicom_server_ui_base_url, base_url),
        '/$',
        ''
    ) || '/dicom-web',
    modified_at = NOW()
WHERE COALESCE(NULLIF(TRIM(dicom_server_ui_base_url), ''), NULLIF(TRIM(base_url), '')) IS NOT NULL
  AND (
        NULLIF(TRIM(dicomweb_base_url), '') IS NULL
        OR LOWER(TRIM(dicomweb_base_url)) LIKE '%docker.internal%'
        OR LOWER(TRIM(dicomweb_base_url)) ~ '^https?://dicom_server_[a-z0-9-]+(:[0-9]+)?(/dicom-web)?/?$'
  );
