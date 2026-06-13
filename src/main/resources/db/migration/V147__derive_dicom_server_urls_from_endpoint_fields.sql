ALTER TABLE hospital_dicom_servers
    ADD COLUMN IF NOT EXISTS dicomweb_path TEXT;

UPDATE hospital_dicom_servers
SET dicomweb_path = CASE
        WHEN COALESCE(NULLIF(BTRIM(dicomweb_base_url), ''), '') ~* '^[a-z][a-z0-9+.-]*://[^/]+/.+'
            THEN REGEXP_REPLACE(BTRIM(dicomweb_base_url), '^[a-z][a-z0-9+.-]*://[^/]+', '', 'i')
        WHEN BTRIM(COALESCE(dicomweb_base_url, '')) LIKE '/%'
            THEN BTRIM(dicomweb_base_url)
        ELSE '/dicom-web'
    END
WHERE dicomweb_path IS NULL
   OR BTRIM(dicomweb_path) = '';

UPDATE hospital_dicom_servers
SET dicomweb_path = '/' || BTRIM(dicomweb_path)
WHERE dicomweb_path IS NOT NULL
  AND BTRIM(dicomweb_path) != ''
  AND BTRIM(dicomweb_path) NOT LIKE '/%';

UPDATE hospital_dicom_servers
SET dicomweb_path = REGEXP_REPLACE(BTRIM(dicomweb_path), '/+$', '')
WHERE dicomweb_path IS NOT NULL
  AND BTRIM(dicomweb_path) != '/'
  AND BTRIM(dicomweb_path) LIKE '%/';

UPDATE hospital_dicom_servers
SET dicomweb_path = '/dicom-web'
WHERE dicomweb_path IS NULL
   OR BTRIM(dicomweb_path) = ''
   OR BTRIM(dicomweb_path) = '/';

ALTER TABLE hospital_dicom_servers
    ALTER COLUMN dicomweb_path SET DEFAULT '/dicom-web';

COMMENT ON COLUMN hospital_dicom_servers.dicomweb_path IS
    'DICOMweb path appended to the derived DICOM server HTTP base URL. Full public URLs are derived from ssl_enabled, ip_address, port, and this path.';

ALTER TABLE hospital_dicom_servers
    DROP COLUMN IF EXISTS base_url,
    DROP COLUMN IF EXISTS dicom_server_ui_base_url,
    DROP COLUMN IF EXISTS dicomweb_base_url;
