package com.ut.emrPacs.config;

public final class ApiConstants {

    private ApiConstants() {
    }

    public static final class Auth {
        public static final String BASE_PATH = "/auth";
        public static final String LOGIN_PATH = "/auth-login";
        public static final String LOGOUT_PATH = "/auth-logout";
        public static final String REFRESH_PATH = "/auth-refresh";
        public static final String CLIENT_CREDENTIALS_PATH = "/auth-client-credentials";

        public static final String LOGIN_FULL_PATH = BASE_PATH + LOGIN_PATH;
        public static final String LOGOUT_FULL_PATH = BASE_PATH + LOGOUT_PATH;
        public static final String REFRESH_FULL_PATH = BASE_PATH + REFRESH_PATH;
        public static final String CLIENT_CREDENTIALS_FULL_PATH = BASE_PATH + CLIENT_CREDENTIALS_PATH;

        private Auth() {
        }
    }

    public static final class User {
        public static final String BASE_PATH = "/user";
        public static final String LIST_PATH = "/user-list";
        public static final String GROUP_LIST_PATH = "/user-group-list";
        public static final String CREATE_PATH = "/user-create";
        public static final String CHANGE_PASSWORD_PATH = "/user-change-password";
        public static final String ME_PATH = "/user-me";
        public static final String FIND_PATH = "/user-find/{id}";
        public static final String UPDATE_PATH = "/user-update";
        public static final String DELETE_PATH = "/user-delete/{id}";

        public static final String ME_FULL_PATH = BASE_PATH + ME_PATH;

        private User() {
        }
    }

    public static final class UserGroup {
        public static final String BASE_PATH = "/user-group";
        public static final String LIST_PATH = "/list";
        public static final String SYSTEM_LIST_PATH = "/system-user-group-list";
        public static final String HOSPITAL_LIST_PATH = "/hospital-user-group-list";
        public static final String FIND_PATH = "/find/{id}";
        public static final String ADD_PATH = "/add";
        public static final String CREATE_PATH = "/create";
        public static final String UPDATE_PATH = "/update";
        public static final String ASSIGN_USERS_PATH = "/assign-users";
        public static final String DELETE_PATH = "/delete/{id}";

        // Clear UserGroup-only aliases for frontend readability
        public static final String ROLE_LIST_PATH = "/user-group-role-list";
        public static final String ROLE_FIND_PATH = "/user-group-role-find/{id}";
        public static final String ROLE_ADD_PATH = "/user-group-role-add";
        public static final String ROLE_UPDATE_PATH = "/user-group-role-update";
        public static final String ROLE_DELETE_PATH = "/user-group-role-delete/{id}";

        private UserGroup() {
        }
    }

    public static final class UserProfile {
        public static final String BASE_PATH = "/user-profile";
        public static final String GET_PATH = "/user-profile-get";
        public static final String UPDATE_PATH = "/user-profile-update";

        private UserProfile() {
        }
    }

    public static final class Hospital {
        public static final String BASE_PATH = "/hospital";
        public static final String LIST_PATH = "/hospital-list";
        public static final String FIND_PATH = "/hospital-find/{id}";
        public static final String UPDATE_PATH = "/hospital-update";
        public static final String CREATE_PATH = "/hospital-create";

        private Hospital() {
        }
    }

    public static final class Role {
        public static final String BASE_PATH = "/role";
        public static final String LIST_PATH = "/role-list";
        public static final String USER_GROUP_LIST_PATH = "/user-group-list";
        public static final String FIND_PATH = "/role-find/{id}";
        public static final String CREATE_PATH = "/role-add";
        public static final String UPDATE_PATH = "/role-update";
        public static final String MENU_PATH = "/role-menu";
        public static final String DELETE_PATH = "/role-delete/{id}";

        public static final String MENU_FULL_PATH = BASE_PATH + MENU_PATH;

        private Role() {
        }
    }

    public static final class ModuleType {
        public static final String BASE_PATH = "/module-type";
        public static final String LIST_PATH = "/module-type/module-type-list";
        public static final String FIND_PATH = "/find/{id}";

        private ModuleType() {
        }
    }

    public static final class Permission {
        public static final String BASE_PATH = "/permission";
        public static final String TREE_PATH = "/permission-tree";
        public static final String SAVE_ROLE_PERMISSIONS_PATH = "/permission-save-role-permissions";

        private Permission() {
        }
    }

    public static final class Patient {
        public static final String BASE_PATH = "/patient";
        public static final String LIST_PATH = "/patient-list";
        public static final String FIND_PATH = "/patient-find/{id}";
        public static final String CREATE_PATH = "/patient-create";
        public static final String UPDATE_PATH = "/patient-update";

        private Patient() {
        }
    }

    public static final class Worklist {
        public static final String BASE_PATH = "/worklist";
        public static final String LIST_PATH = "/worklist-list";
        public static final String ASSIGN_PATH = "/worklist-assign";
        public static final String FIND_PATH = "/worklist-find";
        public static final String UPDATE_PATH = "/worklist-update";
        public static final String ROUTED_MODALITY_LIST_PATH = "/worklist-routed-modality-list";
        public static final String ROUTE_AVAILABILITY_PATH = "/worklist-route-availability";
        public static final String SEND_TO_PACS_PATH = "/worklist-send-to-pacs";
        public static final String MACHINE_ROUTES_PATH = "/worklist-machine-routes";
        public static final String SYNC_RESULT_PATH = "/worklist-sync-result";
        public static final String WORKLIST_PATH = "/{worklistId}/worklist";
        public static final String RECEIVED_STUDY_PATH = "/worklist-received-study";
        public static final String VIEW_STUDY_PATH = "/worklist-view-study";
        public static final String VIEWER_INFO_PATH = "/{worklistId}/viewer-info";
        public static final String VIEW_STUDY_PREVIEW_PATH = "/worklist-view-study-preview/{worklistId}/{instanceId}";
        public static final String VIEWER_DICOMWEB_PATH = "/viewer-dicom-web/{viewerToken}/{hospitalId}/{worklistId}/**";
        public static final String VIEWER_DICOMWEB_PROXY_PATH = "/viewer-dicom-web-proxy/**";
        public static final String VIEWER_DICOMWEB_AUTHORIZE_PATH = "/viewer-dicom-web-authorize";
        public static final String VIEWER_DICOMWEB_PROXY_AUTHORIZE_PATH = "/viewer-dicom-web-proxy-authorize";
        public static final String VIEWER_DICOMWEB_DECODE_PATH = "/viewer-dicom-web-decode";
        public static final String VIEWER_DICOMWEB_PROFILE_PATH = "/viewer-dicom-web-profile";
        public static final String VIEWER_DICOMWEB_RENEW_PATH = "/viewer-dicom-web-renew";
        public static final String VIEWER_DICOMWEB_REVOKE_PATH = "/viewer-dicom-web-revoke";
        public static final String RETURN_PATH = "/worklist-return";
        public static final String CANCEL_PATH = "/worklist-cancel";

        private Worklist() {
        }
    }

    public static final class Study {
        public static final String BASE_PATH = "/study";
        public static final String LIST_PATH = "/study-list";
        public static final String FIND_PATH = "/study-find/{id}";
        public static final String VIEWER_INFO_PATH = "/{studyId}/viewer-info";

        private Study() {
        }
    }

    public static final class StudyRetention {
        public static final String BASE_PATH = "/study-retention";
        public static final String POLICY_LIST_PATH = "/policy-list";
        public static final String POLICY_FIND_PATH = "/policy-find/{publicKey}";
        public static final String POLICY_SAVE_PATH = "/policy-save";
        public static final String POLICY_DELETE_PATH = "/policy-delete/{id}";
        public static final String REVIEW_LIST_PATH = "/review-list";
        public static final String SUMMARY_PATH = "/summary";
        public static final String APPROVE_DELETE_PATH = "/approve-delete/{studyId}";
        public static final String BULK_DELETE_PATH = "/bulk-delete";
        public static final String AUTO_DELETE_RUN_PATH = "/auto-delete-run";
        public static final String REJECT_DELETE_PATH = "/reject-delete/{studyId}";

        private StudyRetention() {
        }
    }

    public static final class DicomUpload {
        public static final String BASE_PATH = "/dicom-uploads";

        private DicomUpload() {
        }
    }

    public static final class PacsResult {
        public static final String BASE_PATH = "/pacs-result";
        public static final String CREATE_PATH = "/pacs-result-create";
        public static final String UPDATE_PATH = "/pacs-result-update";
        public static final String FIND_BY_STUDY_PATH = "/pacs-result-find-by-study";
        public static final String FIND_BY_WORKLIST_PATH = "/pacs-result-find-by-worklist";
        public static final String CONTEXT_PATH = "/pacs-result-context";
        public static final String IMAGE_UPLOAD_PATH = "/pacs-result-image-upload";
        public static final String IMAGE_DELETE_PATH = "/pacs-result-image-delete";
        public static final String IMAGE_CONTENT_PATH = "/pacs-result-image-content";
        public static final String HOSPITAL_LOGO_CONTENT_PATH = "/pacs-result-hospital-logo-content";
        public static final String TEMPLATE_LIST_PATH = "/pacs-result-template-list";
        public static final String TEMPLATE_FIND_PATH = "/pacs-result-template-find/{templateKey}";
        public static final String VIEWER_STATE_FIND_PATH = "/pacs-result-viewer-state-find";
        public static final String VIEWER_STATE_SAVE_PATH = "/pacs-result-viewer-state-save";
        public static final String VIEWER_STATE_DELETE_PATH = "/pacs-result-viewer-state-delete";

        private PacsResult() {
        }
    }

    public static final class PacsResultApi {
        public static final String BASE_PATH = "/pacs-result-api";
        public static final String CREATE_PATH = "/pacs-result-create";
        public static final String UPDATE_PATH = "/pacs-result-update";
        public static final String FIND_BY_STUDY_PATH = "/pacs-result-find-by-study";
        public static final String FIND_BY_WORKLIST_PATH = "/pacs-result-find-by-worklist";
        public static final String CONTEXT_PATH = "/pacs-result-context";
        public static final String IMAGE_UPLOAD_PATH = "/pacs-result-image-upload";
        public static final String IMAGE_DELETE_PATH = "/pacs-result-image-delete";
        public static final String IMAGE_CONTENT_PATH = "/pacs-result-image-content";
        public static final String HOSPITAL_LOGO_CONTENT_PATH = "/pacs-result-hospital-logo-content";
        public static final String TEMPLATE_LIST_PATH = "/pacs-result-template-list";
        public static final String TEMPLATE_FIND_PATH = "/pacs-result-template-find/{templateKey}";
        public static final String VIEWER_STATE_FIND_PATH = "/pacs-result-viewer-state-find";
        public static final String VIEWER_STATE_SAVE_PATH = "/pacs-result-viewer-state-save";
        public static final String VIEWER_STATE_SAVE_CHUNK_PATH = "/pacs-result-viewer-state-save-chunk";
        public static final String VIEWER_STATE_SAVE_CHUNK_COMPLETE_PATH = "/pacs-result-viewer-state-save-chunk-complete";
        public static final String VIEWER_STATE_DELETE_PATH = "/pacs-result-viewer-state-delete";
        public static final String PUBLIC_VIEWER_AUTHORIZE_PATH = "/public-viewer-authorize";

        private PacsResultApi() {
        }
    }

    public static final class PacsResultTemplate {
        public static final String BASE_PATH = "/pacs-result-template";
        public static final String LIST_PATH = "/pacs-result-template-list";
        public static final String FIND_PATH = "/pacs-result-template-find/{id}";
        public static final String CREATE_PATH = "/pacs-result-template-create";
        public static final String UPDATE_PATH = "/pacs-result-template-update";
        public static final String DELETE_PATH = "/pacs-result-template-delete/{id}";

        private PacsResultTemplate() {
        }
    }

    public static final class Dropdown {
        public static final String BASE_PATH = "/dropdown";
        public static final String NATIONALITY_PATH = "/dropdown-nationality";
        public static final String HOSPITAL_PATH = "/dropdown-hospital";
        public static final String MODALITY_PATH = "/dropdown-modality";
        public static final String MODALITY_CATALOG_PATH = "/dropdown-modality-catalog";
        public static final String DICOM_SERVER_PATH = "/dropdown-dicom-server";
        public static final String USER_GROUP_MEMBER_PATH = "/dropdown-user-group-member";
        public static final String USER_PATH = "/dropdown-user";
        public static final String PATIENT_PATH = "/dropdown-patient";
        public static final String USER_GROUP_PATH = "/dropdown-user-group";

        private Dropdown() {
        }
    }

    public static final class FileUpload {
        public static final String BASE_PATH = "/file";
        public static final String UPLOAD_PATH = "/file-upload";
        public static final String UPLOAD_CONTENT_PATH = "/file-upload/{filename:.+}";
        public static final String DELETE_PATH = "/file-delete";

        private FileUpload() {
        }
    }

    public static final class Modality {
        public static final String BASE_PATH = "/modality";
        public static final String LIST_PATH = "/modality-list";
        public static final String FIND_PATH = "/modality-find/{id}";
        public static final String CREATE_PATH = "/modality-create";
        public static final String UPDATE_PATH = "/modality-update";
        public static final String DELETE_PATH = "/modality-delete/{id}";

        private Modality() {
        }
    }

    public static final class HospitalModality {
        public static final String LIST_BY_USER_PATH = "/hospital-modality";

        private HospitalModality() {
        }
    }

    public static final class SystemActivity {
        public static final String BASE_PATH = "/system-activity";
        public static final String LIST_PATH = "/system-activity-list";
        public static final String FIND_PATH = "/system-activity-find/{id}";

        private SystemActivity() {
        }
    }

    public static final class UserLog {
        public static final String BASE_PATH = "/report/user-log";
        public static final String LIST_PATH = "/user-log-list";
        public static final String FIND_PATH = "/user-log-find/{id}";

        private UserLog() {
        }
    }

    public static final class Notification {
        public static final String BASE_PATH = "/notification";
        public static final String LIST_PATH = "/notification-list";

        private Notification() {
        }
    }

    public static final class DicomServer {
        public static final String BASE_PATH = "/dicom-server";
        public static final String LIST_PATH = "/dicom-server-list";
        public static final String HEALTH_LIST_PATH = "/dicom-server-health-list";
        public static final String HEALTH_SUMMARY_PATH = "/dicom-server-health-summary";
        public static final String HEALTH_SETTINGS_GET_PATH = "/dicom-server-health-settings-get";
        public static final String HEALTH_SETTINGS_UPDATE_PATH = "/dicom-server-health-settings-update";
        public static final String FIND_PATH = "/dicom-server-find/{id}";
        public static final String CREATE_PATH = "/dicom-server-create";
        public static final String UPDATE_PATH = "/dicom-server-update";
        public static final String DELETE_PATH = "/dicom-server-delete/{id}";

        private DicomServer() {
        }
    }

    public static final class DicomMachine {
        public static final String BASE_PATH = "/dicom-machine";
        public static final String LIST_PATH = "/dicom-machine-list";
        public static final String FIND_PATH = "/dicom-machine-find/{id}";
        public static final String CREATE_PATH = "/dicom-machine-create";
        public static final String UPDATE_PATH = "/dicom-machine-update";
        public static final String DELETE_PATH = "/dicom-machine-delete/{id}";

        private DicomMachine() {
        }
    }

    public static final class DicomRouting {
        public static final String BASE_PATH = "/dicom-routing";
        public static final String LIST_PATH = "/dicom-routing-list";
        public static final String FIND_PATH = "/dicom-routing-find/{id}";
        public static final String CREATE_PATH = "/dicom-routing-create";
        public static final String UPDATE_PATH = "/dicom-routing-update";
        public static final String DELETE_PATH = "/dicom-routing-delete/{id}";
        public static final String BUILD_CONFIG_PATH = "/dicom-routing-build-config/{id}";

        private DicomRouting() {
        }
    }

    public static final class Dashboard {
        public static final String BASE_PATH = "/dashboard";
        public static final String OVERVIEW_PATH = "/dashboard-overview";

        private Dashboard() {
        }
    }

    public static final class Security {
        public static final String PRINCIPAL_TYPE_USER = "USER";
        public static final String PRINCIPAL_TYPE_CLIENT = "CLIENT";

        private Security() {
        }
    }
}
