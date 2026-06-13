ALTER TABLE hospital_dicom_servers
    ADD COLUMN IF NOT EXISTS storage_directory TEXT,
    ADD COLUMN IF NOT EXISTS index_directory TEXT,
    ADD COLUMN IF NOT EXISTS maximum_storage_size BIGINT,
    ADD COLUMN IF NOT EXISTS maximum_patient_count BIGINT,
    ADD COLUMN IF NOT EXISTS remote_access_allowed BOOLEAN,
    ADD COLUMN IF NOT EXISTS http_server_enabled BOOLEAN,
    ADD COLUMN IF NOT EXISTS enable_http_compression BOOLEAN,
    ADD COLUMN IF NOT EXISTS ssl_enabled BOOLEAN,
    ADD COLUMN IF NOT EXISTS authentication_enabled BOOLEAN,
    ADD COLUMN IF NOT EXISTS authorization_enabled BOOLEAN,
    ADD COLUMN IF NOT EXISTS authorization_root VARCHAR(255),
    ADD COLUMN IF NOT EXISTS authorization_checked_level VARCHAR(64),
    ADD COLUMN IF NOT EXISTS dicom_always_allow_echo BOOLEAN,
    ADD COLUMN IF NOT EXISTS dicom_always_allow_find BOOLEAN,
    ADD COLUMN IF NOT EXISTS dicom_always_allow_get BOOLEAN,
    ADD COLUMN IF NOT EXISTS dicom_always_allow_move BOOLEAN,
    ADD COLUMN IF NOT EXISTS dicom_always_allow_store BOOLEAN,
    ADD COLUMN IF NOT EXISTS dicom_check_called_aet BOOLEAN,
    ADD COLUMN IF NOT EXISTS dicom_tls_enabled BOOLEAN,
    ADD COLUMN IF NOT EXISTS dicom_scp_timeout INTEGER,
    ADD COLUMN IF NOT EXISTS dicom_peers_json TEXT,
    ADD COLUMN IF NOT EXISTS worklists_enabled BOOLEAN,
    ADD COLUMN IF NOT EXISTS worklists_database TEXT,
    ADD COLUMN IF NOT EXISTS plugins_paths TEXT;

UPDATE hospital_dicom_servers
SET
    storage_directory = COALESCE(storage_directory, '/var/lib/dicom_server/db'),
    index_directory = COALESCE(index_directory, '/var/lib/dicom_server/db'),
    maximum_storage_size = COALESCE(maximum_storage_size, 0),
    maximum_patient_count = COALESCE(maximum_patient_count, 0),
    remote_access_allowed = COALESCE(remote_access_allowed, TRUE),
    http_server_enabled = COALESCE(http_server_enabled, TRUE),
    enable_http_compression = COALESCE(enable_http_compression, TRUE),
    ssl_enabled = COALESCE(ssl_enabled, FALSE),
    authentication_enabled = COALESCE(authentication_enabled, TRUE),
    authorization_enabled = COALESCE(authorization_enabled, TRUE),
    authorization_root = COALESCE(authorization_root, '/authorization'),
    authorization_checked_level = COALESCE(authorization_checked_level, 'studies'),
    dicom_always_allow_echo = COALESCE(dicom_always_allow_echo, TRUE),
    dicom_always_allow_find = COALESCE(dicom_always_allow_find, TRUE),
    dicom_always_allow_get = COALESCE(dicom_always_allow_get, TRUE),
    dicom_always_allow_move = COALESCE(dicom_always_allow_move, TRUE),
    dicom_always_allow_store = COALESCE(dicom_always_allow_store, TRUE),
    dicom_check_called_aet = COALESCE(dicom_check_called_aet, FALSE),
    dicom_tls_enabled = COALESCE(dicom_tls_enabled, FALSE),
    dicom_scp_timeout = COALESCE(dicom_scp_timeout, 30),
    dicom_peers_json = COALESCE(dicom_peers_json, '{}'),
    worklists_enabled = COALESCE(worklists_enabled, TRUE),
    worklists_database = COALESCE(worklists_database, '/var/lib/dicom_server/worklists'),
    plugins_paths = COALESCE(plugins_paths, '/usr/share/dicom_server/plugins' || CHR(10) || '/usr/local/share/dicom_server/plugins');

ALTER TABLE hospital_dicom_servers
    ALTER COLUMN storage_directory SET DEFAULT '/var/lib/dicom_server/db',
    ALTER COLUMN index_directory SET DEFAULT '/var/lib/dicom_server/db',
    ALTER COLUMN maximum_storage_size SET DEFAULT 0,
    ALTER COLUMN maximum_patient_count SET DEFAULT 0,
    ALTER COLUMN remote_access_allowed SET DEFAULT TRUE,
    ALTER COLUMN http_server_enabled SET DEFAULT TRUE,
    ALTER COLUMN enable_http_compression SET DEFAULT TRUE,
    ALTER COLUMN ssl_enabled SET DEFAULT FALSE,
    ALTER COLUMN authentication_enabled SET DEFAULT TRUE,
    ALTER COLUMN authorization_enabled SET DEFAULT TRUE,
    ALTER COLUMN authorization_root SET DEFAULT '/authorization',
    ALTER COLUMN authorization_checked_level SET DEFAULT 'studies',
    ALTER COLUMN dicom_always_allow_echo SET DEFAULT TRUE,
    ALTER COLUMN dicom_always_allow_find SET DEFAULT TRUE,
    ALTER COLUMN dicom_always_allow_get SET DEFAULT TRUE,
    ALTER COLUMN dicom_always_allow_move SET DEFAULT TRUE,
    ALTER COLUMN dicom_always_allow_store SET DEFAULT TRUE,
    ALTER COLUMN dicom_check_called_aet SET DEFAULT FALSE,
    ALTER COLUMN dicom_tls_enabled SET DEFAULT FALSE,
    ALTER COLUMN dicom_scp_timeout SET DEFAULT 30,
    ALTER COLUMN dicom_peers_json SET DEFAULT '{}',
    ALTER COLUMN worklists_enabled SET DEFAULT TRUE,
    ALTER COLUMN worklists_database SET DEFAULT '/var/lib/dicom_server/worklists',
    ALTER COLUMN plugins_paths SET DEFAULT '/usr/share/dicom_server/plugins' || CHR(10) || '/usr/local/share/dicom_server/plugins';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_hospital_dicom_servers_storage_limit'
    ) THEN
        ALTER TABLE hospital_dicom_servers
            ADD CONSTRAINT chk_hospital_dicom_servers_storage_limit
                CHECK (maximum_storage_size IS NULL OR maximum_storage_size >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_hospital_dicom_servers_patient_limit'
    ) THEN
        ALTER TABLE hospital_dicom_servers
            ADD CONSTRAINT chk_hospital_dicom_servers_patient_limit
                CHECK (maximum_patient_count IS NULL OR maximum_patient_count >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_hospital_dicom_servers_scp_timeout'
    ) THEN
        ALTER TABLE hospital_dicom_servers
            ADD CONSTRAINT chk_hospital_dicom_servers_scp_timeout
                CHECK (dicom_scp_timeout IS NULL OR dicom_scp_timeout > 0);
    END IF;
END $$;

COMMENT ON COLUMN hospital_dicom_servers.storage_directory IS 'DICOM server StorageDirectory.';
COMMENT ON COLUMN hospital_dicom_servers.index_directory IS 'DICOM server IndexDirectory.';
COMMENT ON COLUMN hospital_dicom_servers.maximum_storage_size IS 'DICOM server MaximumStorageSize. 0 means unlimited.';
COMMENT ON COLUMN hospital_dicom_servers.maximum_patient_count IS 'DICOM server MaximumPatientCount. 0 means unlimited.';
COMMENT ON COLUMN hospital_dicom_servers.remote_access_allowed IS 'DICOM server RemoteAccessAllowed.';
COMMENT ON COLUMN hospital_dicom_servers.http_server_enabled IS 'DICOM server HttpServerEnabled.';
COMMENT ON COLUMN hospital_dicom_servers.enable_http_compression IS 'DICOM server EnableHttpCompression.';
COMMENT ON COLUMN hospital_dicom_servers.ssl_enabled IS 'DICOM server SslEnabled for HTTP.';
COMMENT ON COLUMN hospital_dicom_servers.authentication_enabled IS 'DICOM server AuthenticationEnabled.';
COMMENT ON COLUMN hospital_dicom_servers.authorization_enabled IS 'DICOM server Authorization.Enabled.';
COMMENT ON COLUMN hospital_dicom_servers.authorization_root IS 'DICOM server Authorization.Root.';
COMMENT ON COLUMN hospital_dicom_servers.authorization_checked_level IS 'DICOM server Authorization.CheckedLevel.';
COMMENT ON COLUMN hospital_dicom_servers.dicom_peers_json IS 'DICOM server DicomPeers JSON object. DicomModalities are managed by DICOM Routing.';
COMMENT ON COLUMN hospital_dicom_servers.worklists_database IS 'DICOM server Worklists.Database.';
COMMENT ON COLUMN hospital_dicom_servers.plugins_paths IS 'DICOM server Plugins paths, one per line.';
