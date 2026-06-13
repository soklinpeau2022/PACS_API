ALTER TABLE hospital_dicom_servers
    ADD COLUMN IF NOT EXISTS pacs_api_callback_base_url TEXT;

COMMENT ON COLUMN hospital_dicom_servers.pacs_api_callback_base_url IS
    'PACS API base URL used when generating DICOM server callback ZIP files. Example: http://192.168.8.10:8080/pacsApi or http://localhost:8080/pacsApi.';
