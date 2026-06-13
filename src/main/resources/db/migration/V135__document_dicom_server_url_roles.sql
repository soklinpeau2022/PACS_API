COMMENT ON COLUMN hospital_dicom_servers.base_url IS
    'Internal API-to-DICOM server HTTP base URL. Use an IP/DNS reachable from the PACS API server; do not use the PACS API callback URL here.';

COMMENT ON COLUMN hospital_dicom_servers.dicom_server_ui_base_url IS
    'Public browser URL for DICOM server Explorer. This may be localhost for local developer access or a real hospital DICOM server host/IP.';

COMMENT ON COLUMN hospital_dicom_servers.dicomweb_base_url IS
    'Public DICOMweb URL for browser/viewer display only. The PACS API proxy uses base_url plus /dicom-web for server-side access.';

COMMENT ON COLUMN hospital_dicom_servers.viewer_base_url IS
    'Public URL for the single OHIF viewer. One viewer can serve all hospitals through the PACS API DICOMweb proxy.';

COMMENT ON COLUMN hospital_dicom_servers.pacs_api_callback_base_url IS
    'PACS API base URL reachable from DICOM server for stable-study callbacks and viewer token authorization.';
