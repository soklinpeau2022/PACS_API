package com.ut.emrPacs.config;

public final class WorklistConstants {

    private WorklistConstants() {
    }

    public static final String MODULE_CODE = "Worklist";
    public static final String ACTION_RETURN = "Return";
    public static final String ACTION_CANCEL = "Cancel";
    public static final String ACTION_UPDATE_STATUS = "Update Status";
    public static final String ACTION_FIND = "Find";
    public static final String ACTION_UPDATE = "Update";
    public static final String ACTION_ASSIGN = "Assign";
    public static final String ACTION_SEND_TO_DICOMSERVER = "Send To DicomServer";
    // Backward compatibility alias for legacy references.
    public static final String ACTION_SEND_TO_PACS = ACTION_SEND_TO_DICOMSERVER;
    public static final String ACTION_RECEIVED_STUDY = "Received Study";
    public static final String ACTION_SYNC_PACS_RESULT = "Sync PACS Result";
    public static final String ACTION_SEND_WORKLIST = "Send Worklist";
    public static final String ACTION_WORKLIST_SYNC = "Worklist Sync";
    public static final String ACTION_WORKLIST_UPDATE = "Worklist Update";
    public static final String ACTION_WORKLIST_DELETE = "Worklist Delete";
    public static final String ACTION_VIEW_STUDY = "View Study";
    public static final String ACTION_VIEW = "View";

    /** Activity-log result labels and status codes (codebase convention: 1 = success, 2 = error). */
    public static final String RESULT_SUCCESS = "Success";
    public static final String RESULT_ERROR = "Error";
    public static final int LOG_STATUS_SUCCESS = 1;
    public static final int LOG_STATUS_ERROR = 2;

    /** Repeated audit-log labels. */
    public static final String LABEL_RECEIVED_STUDY = "Worklist (Received Study)";
    public static final String LABEL_SEND_TO_DICOM_SERVER = "Worklist (Send To DicomServer Worklist)";
    public static final String LABEL_ROUTE_AVAILABILITY = "Worklist (Route Availability)";

    /** Repeated client-facing messages. */
    public static final String MSG_WORKLIST_NOT_FOUND = "Worklist not found.";
    public static final String MSG_ID_REQUIRED = "id is required.";
    public static final String MSG_DICOM_SERVER_UNAUTHORIZED = "DicomServer unauthorized (401).";
    public static final String MSG_DICOM_SERVER_UNREACHABLE = "DicomServer is unreachable.";
    public static final String MSG_UNABLE_TO_UPDATE_STATUS = "Unable to update Worklist status.";
    public static final String MSG_VIEWER_DICOMWEB_PATH_REQUIRED = "Viewer DICOMweb path is required.";
}
