ALTER TABLE hospital_dicom_servers
    ADD COLUMN IF NOT EXISTS dicom_server_ui_base_url TEXT,
    ADD COLUMN IF NOT EXISTS dicomweb_base_url TEXT,
    ADD COLUMN IF NOT EXISTS viewer_base_url TEXT;

UPDATE hospital_dicom_servers
SET dicomweb_base_url = COALESCE(
        NULLIF(TRIM(dicomweb_base_url), ''),
        CASE
            WHEN COALESCE(NULLIF(TRIM(dicom_server_ui_base_url), ''), '') = '' THEN NULL
            WHEN LOWER(TRIM(dicom_server_ui_base_url)) LIKE '%/dicom-web' THEN REGEXP_REPLACE(TRIM(dicom_server_ui_base_url), '/$', '')
            ELSE REGEXP_REPLACE(TRIM(dicom_server_ui_base_url), '/$', '') || '/dicom-web'
        END
    )
WHERE COALESCE(NULLIF(TRIM(dicomweb_base_url), ''), '') = '';
