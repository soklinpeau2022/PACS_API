UPDATE hospital_dicom_servers
SET storage_directory = '/var/lib/dicom_server/db'
WHERE storage_directory IS NULL OR BTRIM(storage_directory) = '';

UPDATE hospital_dicom_servers
SET index_directory = '/var/lib/dicom_server/db'
WHERE index_directory IS NULL OR BTRIM(index_directory) = '';

UPDATE hospital_dicom_servers
SET worklists_database = '/var/lib/dicom_server/worklists'
WHERE worklists_database IS NULL OR BTRIM(worklists_database) = '';

UPDATE hospital_dicom_servers
SET plugins_paths = '/usr/share/dicom_server/plugins' || CHR(10) || '/usr/local/share/dicom_server/plugins'
WHERE plugins_paths IS NULL OR BTRIM(plugins_paths) = '';

ALTER TABLE hospital_dicom_servers
    ALTER COLUMN storage_directory SET DEFAULT '/var/lib/dicom_server/db',
    ALTER COLUMN index_directory SET DEFAULT '/var/lib/dicom_server/db',
    ALTER COLUMN worklists_database SET DEFAULT '/var/lib/dicom_server/worklists',
    ALTER COLUMN plugins_paths SET DEFAULT '/usr/share/dicom_server/plugins' || CHR(10) || '/usr/local/share/dicom_server/plugins';

COMMENT ON COLUMN hospital_dicom_servers.storage_directory IS
    'DICOM server StorageDirectory.';

COMMENT ON COLUMN hospital_dicom_servers.index_directory IS
    'DICOM server IndexDirectory.';

COMMENT ON COLUMN hospital_dicom_servers.worklists_database IS
    'DICOM server Worklists.Database.';

COMMENT ON COLUMN hospital_dicom_servers.plugins_paths IS
    'DICOM server Plugins paths, one per line.';
