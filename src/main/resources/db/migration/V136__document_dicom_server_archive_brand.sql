COMMENT ON TABLE dicom_server_callback_log IS
    'Stable-study callback audit log for DICOM server archive notifications.';

COMMENT ON COLUMN hospital_dicom_servers.dicom_server_ui_base_url IS
    'Public browser URL for DICOM server Explorer.';

COMMENT ON COLUMN pacs_worklists.dicom_server_worklist_id IS
    'DICOM server worklist identifier returned by the routed archive destination.';

COMMENT ON COLUMN pacs_worklists.dicom_server_worklist_path IS
    'DICOM server worklist path returned by the routed archive destination.';

COMMENT ON COLUMN pacs_studies.dicom_server_study_id IS
    'DICOM server study identifier linked to the PACS study archive row.';

COMMENT ON COLUMN pacs_studies.dicom_server_patient_id IS
    'DICOM server patient identifier linked to the PACS study archive row.';

COMMENT ON COLUMN pacs_studies.dicom_server_series_id IS
    'DICOM server series identifier linked to the PACS study archive row.';
