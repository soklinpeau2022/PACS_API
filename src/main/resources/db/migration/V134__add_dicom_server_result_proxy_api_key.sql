ALTER TABLE hospital_dicom_servers
    ADD COLUMN IF NOT EXISTS pacs_result_api_key_hash TEXT;

COMMENT ON COLUMN hospital_dicom_servers.pacs_result_api_key_hash IS
    'BCrypt hash of the server-side PACS Result proxy API key generated per DICOM server. The raw key is written only to private deployment .env files and is never returned by API list/find responses.';

CREATE INDEX IF NOT EXISTS idx_hospital_dicom_servers_result_key_hospital
    ON hospital_dicom_servers (hospital_id, id)
    WHERE is_active = 1
      AND pacs_result_api_key_hash IS NOT NULL
      AND BTRIM(pacs_result_api_key_hash) <> '';
