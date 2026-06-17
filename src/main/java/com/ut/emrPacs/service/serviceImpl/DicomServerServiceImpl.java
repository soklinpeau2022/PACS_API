package com.ut.emrPacs.service.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.cache.config.CacheConfig;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.FunctionHelper;
import static com.ut.emrPacs.helper.FunctionHelper.firstNonNull;
import static com.ut.emrPacs.helper.FunctionHelper.hasText;
import static com.ut.emrPacs.helper.FunctionHelper.trimToNull;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.auth.OAuth2ClientMapper;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.DicomServerFilter;
import com.ut.emrPacs.model.dto.request.pacs.dicom.DicomServerHealthSettingsRequest;
import com.ut.emrPacs.model.components.pacs.dicom.HospitalDicomMachine;
import com.ut.emrPacs.model.components.pacs.dicom.HospitalDicomServer;
import com.ut.emrPacs.model.components.pacs.dicom.HospitalDicomRoutingConfig;
import com.ut.emrPacs.model.components.pacs.dicom.HospitalModalityServerRoute;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomMachineListRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomMachineRequestUpdate;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomServerRequestUpdate;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalModalityServerRouteListRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalModalityServerRouteRequestUpdate;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalModalityServerRouteSaveRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomMachineResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomRoutingConfigResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalModalityServerRouteResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerConfigBuildResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerHealthService;
import com.ut.emrPacs.service.service.DicomServerService;
import com.ut.emrPacs.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DicomServerServiceImpl implements DicomServerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomServerServiceImpl.class);
    private static final String MULTI_VALUE_DELIMITER = "~|~";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DICOM_SERVER_CONTAINER_HTTP_PORT = 8042;
    private static final int DEFAULT_DICOM_PORT = 4242;
    private static final String DEFAULT_DICOM_AE_TITLE = "UDAYA_DCM_SERVER";
    private static final String DICOM_SERVER_PREFIX = "dicom_server";
    private static final String LEGACY_DICOM_SERVER_PREFIX = "udaya_dicom_server";
    private static final String DEFAULT_STORAGE_DIRECTORY = "/var/lib/dicom_server/db";
    private static final String DEFAULT_WORKLISTS_DATABASE = "/var/lib/dicom_server/worklists";
    private static final String DEFAULT_AUTHORIZATION_ROOT = "/authorization";
    private static final String DEFAULT_AUTHORIZATION_CHECKED_LEVEL = "studies";
    private static final String DEFAULT_DICOM_PEERS_JSON = "{}";
    private static final String DICOM_SERVER_BASE_DOCKER_IMAGE = "dicom_server_base:latest";
    private static final String DEFAULT_PLUGINS_PATHS =
            "/usr/share/dicom_server/plugins\n/usr/local/share/dicom_server/plugins";
    private static final String DEFAULT_DICOMWEB_PATH = "/dicom-web";
    private static final String DICOM_SERVER_UPSTREAM_DOCKER_IMAGE = "orthancteam/orthanc:latest";
    private static final String DEFAULT_CALLBACK_API_BASE_URL = "http://localhost:8080/pacsApi";
    private static final String CALLBACK_RECEIVED_STUDY_PATH = "/worklist/worklist-received-study";
    private static final String CALLBACK_TOKEN_PATH = "/auth/auth-client-credentials";
    private static final String CALLBACK_SCRIPT_FILE_NAME = "notify-emr.lua";
    private static final String DICOM_SERVER_CONFIG_CONTAINER_PATH = "/etc/dicom_server/config.json";
    private static final String DICOM_SERVER_SCRIPT_CONTAINER_DIRECTORY = "/etc/dicom_server/scripts";
    private static final String DICOM_SERVER_BRAND_CONTAINER_DIRECTORY = "/usr/share/dicom_server/brand";
    private static final String DICOM_SERVER_BRAND_LOGO_FILE_NAME = "udaya-logo.svg";
    private static final String DICOM_SERVER_BRAND_CSS_FILE_NAME = "udaya-explorer.css";
    private static final String CALLBACK_SCRIPT_CONTAINER_PATH =
            DICOM_SERVER_SCRIPT_CONTAINER_DIRECTORY + "/" + CALLBACK_SCRIPT_FILE_NAME;
    private static final String VIEWER_DICOMWEB_AUTHORIZE_PATH =
            ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_DICOMWEB_AUTHORIZE_PATH;
    private static final String VIEWER_DICOMWEB_DECODE_PATH =
            ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_DICOMWEB_DECODE_PATH;
    private static final String VIEWER_DICOMWEB_PROFILE_PATH =
            ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_DICOMWEB_PROFILE_PATH;
    private static final String CALLBACK_SCRIPT_RESOURCE = "dicom_server/" + CALLBACK_SCRIPT_FILE_NAME;
    private static final int PACS_RESULT_API_KEY_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private DicomServerMapper dicomServerMapper;

    @Autowired
    private OAuth2ClientMapper oauth2ClientMapper;

    @Autowired
    private ModalityMapper modalityMapper;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserService userService;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private DicomServerHealthService dicomServerHealthService;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @Value("${api.authUrl:}")
    private String apiAuthUrl;

    @Override
    public ResponseMessage<BaseResult> listDicomServers(DicomServerFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long requestedHospitalId = filter == null ? null : publicEntityKeyResolver.resolve(Entity.HOSPITAL, filter.getHospitalKey(), null);
            Long hospitalId = resolveOptionalScopedHospitalId(requestedHospitalId);
            Pagination pagination = PaginationHelper.buildAndApplyOffset(filter, dicomServerMapper.countDicomServers(hospitalId, filter));
            List<HospitalDicomServerResponse> rows = dicomServerMapper.listDicomServers(hospitalId, filter);
            enrichDicomServerRows(rows);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.LIST_PATH, null, null, "DicomServer", "Dicom Server (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", rows, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.LIST_PATH, errorLine, error.toString(), "DicomServer", "Dicom Server (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> listDicomServerHealth(DicomServerFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long requestedHospitalId = filter == null ? null : publicEntityKeyResolver.resolve(Entity.HOSPITAL, filter.getHospitalKey(), null);
            Long hospitalId = resolveOptionalScopedHospitalId(requestedHospitalId);
            var rows = dicomServerHealthService.listHealth(hospitalId);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.HEALTH_LIST_PATH, null, null, "DicomServer", "Dicom Server Health (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", rows, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.HEALTH_LIST_PATH, errorLine, error.toString(), "DicomServer", "Dicom Server Health (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getDicomServerHealthSummary(DicomServerFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long requestedHospitalId = filter == null ? null : publicEntityKeyResolver.resolve(Entity.HOSPITAL, filter.getHospitalKey(), null);
            Long hospitalId = resolveOptionalScopedHospitalId(requestedHospitalId);
            var summary = dicomServerHealthService.getSummary(hospitalId);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.HEALTH_SUMMARY_PATH, null, null, "DicomServer", "DICOM Server Health (Summary)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(summary), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.HEALTH_SUMMARY_PATH, errorLine, error.toString(), "DicomServer", "DICOM Server Health (Summary)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getDicomServerHealthSettings(HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var settings = dicomServerHealthService.getSettings();

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.HEALTH_SETTINGS_GET_PATH, null, null, "DicomServer", "DICOM Server Health Settings (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(settings), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.HEALTH_SETTINGS_GET_PATH, errorLine, error.toString(), "DicomServer", "DICOM Server Health Settings (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> updateDicomServerHealthSettings(DicomServerHealthSettingsRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var principal = UserAuthSession.getCurrentUser();
            if (principal == null || !isSuperAdmin(principal.userId())) {
                return ResponseMessageUtils.makeResponse(false, 403, "Forbidden", "Only SuperAdmin can update DICOM server health settings.");
            }
            var safeRequest = request == null ? new DicomServerHealthSettingsRequest() : request;
            var settings = dicomServerHealthService.updateSettings(
                    safeRequest.getEnabled(),
                    safeRequest.getPollIntervalSeconds(),
                    principal.userId()
            );

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.HEALTH_SETTINGS_UPDATE_PATH, null, null, "DicomServer", "DICOM Server Health Settings (Update)", "Edit", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(settings), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.HEALTH_SETTINGS_UPDATE_PATH, errorLine, error.toString(), "DicomServer", "DICOM Server Health Settings (Update)", "Edit", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getDicomServerById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long hospitalId = resolveOptionalScopedHospitalId(null);
            List<HospitalDicomServerResponse> rows = dicomServerMapper.getDicomServerById(id, hospitalId);
            enrichDicomServerRows(rows);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.FIND_PATH, null, null, "DicomServer", "Dicom Server (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", rows, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.FIND_PATH, errorLine, error.toString(), "DicomServer", "Dicom Server (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> createDicomServer(HospitalDicomServerRequestUpdate requestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (requestUpdate == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            if (requestUpdate.getName() == null || requestUpdate.getName().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Dicom server name is required.", false));
            }

            Long hospitalId = resolveScopedHospitalId(requestUpdate.getHospitalKey(), requestUpdate.getHospitalId());
            ResolvedDicomEndpoint endpoint;
            try {
                endpoint = resolveDicomEndpoint(requestUpdate, null);
            } catch (IllegalArgumentException validationError) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
            }
            String name = requestUpdate.getName().trim();
            String ipAddress = endpoint.ipAddress;
            String aeTitle = trimToNull(requestUpdate.getAeTitle());

            if (dicomServerMapper.countActiveDicomServerNameDuplicate(hospitalId, name, null) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Dicom Server Name", false));
            }
            if (dicomServerMapper.countActiveDicomServerEndpointDuplicate(hospitalId, ipAddress, endpoint.dicomWebPort, endpoint.dicomPort, aeTitle, null) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Dicom Server Endpoint", false));
            }

            Long userId = userService.getUserAuth().getId();
            HospitalDicomServer server = new HospitalDicomServer();
            server.setHospitalId(hospitalId);
            server.setName(name);
            server.setIpAddress(ipAddress);
            server.setPort(endpoint.dicomWebPort);
            server.setDicomPort(endpoint.dicomPort);
            server.setAeTitle(aeTitle);
            server.setDicomwebPath(normalizeDicomwebPath(requestUpdate.getDicomwebPath()));
            server.setPublicHealthCheckUrl(normalizeOptionalHealthCheckUrl(
                    requestUpdate.getPublicHealthCheckUrl(),
                    endpoint.baseUrl,
                    "Public DICOM server ping URL"
            ));
            server.setViewerBaseUrl(normalizeOptionalHttpBaseUrl(requestUpdate.getViewerBaseUrl(), "Viewer Base URL"));
            server.setPacsApiCallbackBaseUrl(normalizeOptionalHttpBaseUrl(requestUpdate.getPacsApiCallbackBaseUrl(), "UDAYA_PACS_API Callback URL"));
            server.setUsername(trimToNull(requestUpdate.getUsername()));
            server.setPassword(trimToNull(requestUpdate.getPassword()));
            server.setPacsResultApiKeyHash(passwordEncoder.encode(generatePacsResultApiKey()));
            try {
                server.setIsActive(resolveActiveStatus(requestUpdate.getIsActive(), 1L));
            } catch (IllegalArgumentException validationError) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
            }
            try {
                applyDicomServerConfig(server, requestUpdate, endpoint);
            } catch (IllegalArgumentException validationError) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
            }
            server.setCreatedBy(userId);
            server.setModifiedBy(userId);

            Boolean result = dicomServerMapper.createDicomServer(server);
            if (Boolean.TRUE.equals(result)) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.CREATE_PATH, null, null, "DicomServer", "Dicom Server (Add)", "Add", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.CREATE_PATH, errorLine, error.toString(), "DicomServer", "Dicom Server (Add)", "Add", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> updateDicomServer(HospitalDicomServerRequestUpdate requestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (requestUpdate == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            requestUpdate.setId(publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, requestUpdate.getPublicKey(), null));
            if (requestUpdate.getId() == null || requestUpdate.getId() <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Dicom server id is required.", false));
            }
            if (requestUpdate.getName() == null || requestUpdate.getName().trim().isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Dicom server name is required.", false));
            }

            Long hospitalId = resolveScopedHospitalId(requestUpdate.getHospitalKey(), requestUpdate.getHospitalId());
            List<HospitalDicomServerResponse> existing = dicomServerMapper.getDicomServerById(requestUpdate.getId(), hospitalId);
            if (existing == null || existing.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Dicom server not found.", false));
            }
            HospitalDicomServerResponse existingServer = existing.get(0);

            ResolvedDicomEndpoint endpoint;
            try {
                endpoint = resolveDicomEndpoint(requestUpdate, existingServer);
            } catch (IllegalArgumentException validationError) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
            }

            String name = requestUpdate.getName().trim();
            String ipAddress = endpoint.ipAddress;
            String aeTitle = trimToNull(requestUpdate.getAeTitle());
            if (dicomServerMapper.countActiveDicomServerNameDuplicate(hospitalId, name, requestUpdate.getId()) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Dicom Server Name", false));
            }
            if (dicomServerMapper.countActiveDicomServerEndpointDuplicate(hospitalId, ipAddress, endpoint.dicomWebPort, endpoint.dicomPort, aeTitle, requestUpdate.getId()) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Duplicate Dicom Server Endpoint", false));
            }

            Long userId = userService.getUserAuth().getId();
            HospitalDicomServer server = new HospitalDicomServer();
            server.setId(requestUpdate.getId());
            server.setHospitalId(hospitalId);
            server.setName(name);
            server.setIpAddress(ipAddress);
            server.setPort(endpoint.dicomWebPort);
            server.setDicomPort(endpoint.dicomPort);
            server.setAeTitle(aeTitle);
            server.setDicomwebPath(normalizeDicomwebPath(firstNonBlank(requestUpdate.getDicomwebPath(), existingServer.getDicomwebPath())));
            server.setPublicHealthCheckUrl(normalizeOptionalHealthCheckUrl(
                    firstNonBlank(requestUpdate.getPublicHealthCheckUrl(), existingServer.getPublicHealthCheckUrl()),
                    endpoint.baseUrl,
                    "Public DICOM server ping URL"
            ));
            server.setViewerBaseUrl(normalizeOptionalHttpBaseUrl(
                    firstNonBlank(requestUpdate.getViewerBaseUrl(), existingServer.getViewerBaseUrl()),
                    "Viewer Base URL"
            ));
            server.setPacsApiCallbackBaseUrl(normalizeOptionalHttpBaseUrl(
                    firstNonBlank(requestUpdate.getPacsApiCallbackBaseUrl(), existingServer.getPacsApiCallbackBaseUrl()),
                    "UDAYA_PACS_API Callback URL"
            ));
            server.setUsername(firstNonBlank(requestUpdate.getUsername(), existingServer.getUsername()));
            String requestedPassword = trimToNull(requestUpdate.getPassword());
            server.setPassword(requestedPassword == null ? existingServer.getPassword() : requestedPassword);
            server.setPacsResultApiKeyHash(existingServer.getPacsResultApiKeyHash());
            try {
                server.setIsActive(resolveActiveStatus(requestUpdate.getIsActive(), existingServer.getIsActive()));
            } catch (IllegalArgumentException validationError) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
            }
            preserveExistingDicomServerConfig(requestUpdate, existingServer);
            try {
                applyDicomServerConfig(server, requestUpdate, endpoint);
            } catch (IllegalArgumentException validationError) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
            }
            server.setModifiedBy(userId);

            Boolean result = dicomServerMapper.updateDicomServer(server);
            if (Boolean.TRUE.equals(result)) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.UPDATE_PATH, null, null, "DicomServer", "Dicom Server (Edit)", "Edit", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.UPDATE_PATH, errorLine, error.toString(), "DicomServer", "Dicom Server (Edit)", "Edit", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> deleteDicomServer(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long scopeHospitalId = resolveOptionalScopedHospitalId(null);
            List<HospitalDicomServerResponse> existing = dicomServerMapper.getDicomServerById(id, scopeHospitalId);
            if (existing == null || existing.isEmpty() || existing.get(0).getHospitalId() == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Dicom server not found.", false));
            }
            Long hospitalId = existing.get(0).getHospitalId();
            List<String> routeUsage = dicomServerMapper.listActiveRouteUsageByDicomServerId(hospitalId, id);
            if (routeUsage != null && !routeUsage.isEmpty()) {
                return ResponseMessageUtils.makeResponse(
                        false,
                        messageService.message(buildDicomRoutingRelationMessage("This DICOM server"), routeUsage, false)
                );
            }
            Long userId = userService.getUserAuth().getId();
            Boolean result = dicomServerMapper.deleteDicomServer(id, hospitalId, userId);
            if (Boolean.TRUE.equals(result)) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.DELETE_PATH, null, null, "DicomServer", "Dicom Server (Delete)", "Delete", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomServer.BASE_PATH + ApiConstants.DicomServer.DELETE_PATH, errorLine, error.toString(), "DicomServer", "Dicom Server (Delete)", "Delete", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> listDicomMachines(HospitalDicomMachineListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                request = new HospitalDicomMachineListRequest();
            }
            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long hospitalId = resolveOptionalScopedHospitalId(requestedHospitalId);
            request.setModalityId(publicEntityKeyResolver.resolve(Entity.MODALITY, request.getModalityKey(), null));
            Pagination pagination = PaginationHelper.buildAndApplyOffset(
                    request,
                    dicomServerMapper.countDicomMachines(hospitalId, request)
            );
            List<HospitalDicomMachineResponse> rows = dicomServerMapper.listDicomMachines(hospitalId, request);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomMachine.BASE_PATH + ApiConstants.DicomMachine.LIST_PATH, null, null, "DicomMachine", "DICOM Machine (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", rows, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomMachine.BASE_PATH + ApiConstants.DicomMachine.LIST_PATH, errorLine, error.toString(), "DicomMachine", "DICOM Machine (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getDicomMachineById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Machine id is required.", false));
            }
            Long hospitalId = resolveOptionalScopedHospitalId(null);
            HospitalDicomMachineResponse row = dicomServerMapper.getDicomMachineById(id, hospitalId);
            if (row == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("DICOM machine not found.", false));
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomMachine.BASE_PATH + ApiConstants.DicomMachine.FIND_PATH, null, null, "DicomMachine", "DICOM Machine (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(row), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomMachine.BASE_PATH + ApiConstants.DicomMachine.FIND_PATH, errorLine, error.toString(), "DicomMachine", "DICOM Machine (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(cacheNames = {
            CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
            CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL
    }, allEntries = true)
    public ResponseMessage<BaseResult> createDicomMachine(HospitalDicomMachineRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            Long hospitalId = resolveScopedHospitalId(request.getHospitalKey(), request.getHospitalId());
            Long userId = userService.getUserAuth().getId();
            HospitalDicomMachine machine;
            try {
                machine = normalizeMachineRequest(request, hospitalId, null, userId);
            } catch (IllegalArgumentException validationError) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
            }
            ResponseMessage<BaseResult> domainValidation = validateMachineDomain(hospitalId, machine.getModalityId());
            if (domainValidation != null) {
                return domainValidation;
            }
            Long duplicateCount = dicomServerMapper.countActiveDicomMachineEndpointDuplicate(
                    hospitalId,
                    machine.getModalityId(),
                    machine.getMachineAeTitle(),
                    machine.getMachineHost(),
                    machine.getMachinePort(),
                    null
            );
            if (duplicateCount != null && duplicateCount > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("This machine DICOM endpoint already exists for the selected hospital and modality.", false));
            }

            Boolean created = dicomServerMapper.createDicomMachine(machine);
            if (Boolean.TRUE.equals(created)) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.DicomMachine.BASE_PATH + ApiConstants.DicomMachine.CREATE_PATH, null, null, "DicomMachine", "DICOM Machine (Add)", "Add", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to create DICOM machine.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomMachine.BASE_PATH + ApiConstants.DicomMachine.CREATE_PATH, errorLine, error.toString(), "DicomMachine", "DICOM Machine (Add)", "Add", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(cacheNames = {
            CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
            CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL
    }, allEntries = true)
    public ResponseMessage<BaseResult> updateDicomMachine(HospitalDicomMachineRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            request.setId(publicEntityKeyResolver.resolve(Entity.DICOM_MACHINE, request.getPublicKey(), null));
            if (request.getId() == null || request.getId() <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Machine id is required.", false));
            }
            Long hospitalId = resolveScopedHospitalId(request.getHospitalKey(), request.getHospitalId());
            HospitalDicomMachineResponse existing = dicomServerMapper.getDicomMachineById(request.getId(), hospitalId);
            if (existing == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("DICOM machine not found.", false));
            }
            Long userId = userService.getUserAuth().getId();
            HospitalDicomMachine machine;
            try {
                machine = normalizeMachineRequest(request, hospitalId, request.getId(), userId);
            } catch (IllegalArgumentException validationError) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
            }
            ResponseMessage<BaseResult> domainValidation = validateMachineDomain(hospitalId, machine.getModalityId());
            if (domainValidation != null) {
                return domainValidation;
            }
            if (existing.getModalityId() == null || !existing.getModalityId().equals(machine.getModalityId())) {
                Long routeUsageCount = dicomServerMapper.countRoutesByMachineId(hospitalId, request.getId());
                if (routeUsageCount != null && routeUsageCount > 0) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("This machine has routing history. Create a new machine to use another modality.", false));
                }
            }
            Long duplicateCount = dicomServerMapper.countActiveDicomMachineEndpointDuplicate(
                    hospitalId,
                    machine.getModalityId(),
                    machine.getMachineAeTitle(),
                    machine.getMachineHost(),
                    machine.getMachinePort(),
                    request.getId()
            );
            if (duplicateCount != null && duplicateCount > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("This machine DICOM endpoint already exists for the selected hospital and modality.", false));
            }

            Boolean updated = dicomServerMapper.updateDicomMachine(machine);
            if (Boolean.TRUE.equals(updated)) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.DicomMachine.BASE_PATH + ApiConstants.DicomMachine.UPDATE_PATH, null, null, "DicomMachine", "DICOM Machine (Edit)", "Edit", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to update DICOM machine.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomMachine.BASE_PATH + ApiConstants.DicomMachine.UPDATE_PATH, errorLine, error.toString(), "DicomMachine", "DICOM Machine (Edit)", "Edit", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(cacheNames = {
            CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
            CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL
    }, allEntries = true)
    public ResponseMessage<BaseResult> deleteDicomMachine(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Machine id is required.", false));
            }
            Long hospitalId = resolveOptionalScopedHospitalId(null);
            HospitalDicomMachineResponse existing = dicomServerMapper.getDicomMachineById(id, hospitalId);
            if (existing == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("DICOM machine not found.", false));
            }
            List<String> routeUsage = dicomServerMapper.listActiveRouteUsageByMachineId(existing.getHospitalId(), id);
            if (routeUsage != null && !routeUsage.isEmpty()) {
                return ResponseMessageUtils.makeResponse(
                        false,
                        messageService.message(buildDicomRoutingRelationMessage("This machine"), routeUsage, false)
                );
            }
            Long userId = userService.getUserAuth().getId();
            Integer deleted = dicomServerMapper.deleteDicomMachine(id, existing.getHospitalId(), userId);
            if (deleted != null && deleted > 0) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.DicomMachine.BASE_PATH + ApiConstants.DicomMachine.DELETE_PATH, null, null, "DicomMachine", "DICOM Machine (Delete)", "Delete", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message(buildDicomRoutingRelationMessage("This machine"), false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomMachine.BASE_PATH + ApiConstants.DicomMachine.DELETE_PATH, errorLine, error.toString(), "DicomMachine", "DICOM Machine (Delete)", "Delete", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> listRouting(HospitalModalityServerRouteListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                request = new HospitalModalityServerRouteListRequest();
            }
            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long hospitalId = resolveOptionalScopedHospitalId(requestedHospitalId);
            request.setModalityId(publicEntityKeyResolver.resolve(Entity.MODALITY, request.getModalityKey(), null));
            Pagination pagination = PaginationHelper.buildAndApplyOffset(
                    request,
                    dicomServerMapper.countRoutingConfigs(hospitalId, request)
            );
            List<HospitalDicomRoutingConfigResponse> rows = dicomServerMapper.listRoutingConfigs(hospitalId, request);
            enrichRoutingConfigRows(rows);
            attachRoutesToRoutingConfigs(rows, hospitalId, request.getModalityId());

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.LIST_PATH, null, null, "DicomRouting", "Dicom Routing (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", rows, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.LIST_PATH, errorLine, error.toString(), "DicomRouting", "Dicom Routing (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getRoutingById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Route id is required.", false));
            }
            Long hospitalId = resolveOptionalScopedHospitalId(null);
            HospitalDicomRoutingConfigResponse routeConfig = dicomServerMapper.getRoutingConfigById(id, hospitalId);
            if (routeConfig == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Dicom routing not found.", false));
            }
            enrichRoutingConfigRows(List.of(routeConfig));
            attachRoutesToRoutingConfigs(List.of(routeConfig), hospitalId, null);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.FIND_PATH, null, null, "DicomRouting", "Dicom Routing (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(routeConfig), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.FIND_PATH, errorLine, error.toString(), "DicomRouting", "Dicom Routing (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> createRouting(HospitalModalityServerRouteSaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            Long hospitalId = resolveScopedHospitalId(request.getHospitalKey(), request.getHospitalId());
            Long dicomServerId = firstPositive(
                    publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, request.getDicomServerKey(), null),
                    request.getDicomServerId()
            );
            if (dicomServerId == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Destination DICOM server is required.", false));
            }
            ResponseMessage<BaseResult> serverValidation = validateRoutingDestinationServer(hospitalId, dicomServerId);
            if (serverValidation != null) {
                return serverValidation;
            }
            if (dicomServerMapper.countActiveRoutingConfigByHospitalAndServer(hospitalId, dicomServerId, null) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("This hospital already has a DICOM routing configuration for this destination DICOM server.", false));
            }

            Long userId = userService.getUserAuth().getId();
            List<HospitalModalityServerRoute> normalizedRoutes;
            try {
                normalizedRoutes = normalizeRoutes(hospitalId, request.getRoutes());
            } catch (IllegalArgumentException validationError) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
            }
            if (normalizedRoutes.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("routes[] with machineId and modalityId is required.", false));
            }

            ResponseMessage<BaseResult> validationResult = validateRoutesForHospital(hospitalId, normalizedRoutes);
            if (validationResult != null) {
                return validationResult;
            }

            HospitalDicomRoutingConfig config = new HospitalDicomRoutingConfig();
            config.setHospitalId(hospitalId);
            config.setDicomServerId(dicomServerId);
            config.setCreatedBy(userId);
            config.setModifiedBy(userId);

            Boolean configCreated = dicomServerMapper.createRoutingConfig(config);
            if (!Boolean.TRUE.equals(configCreated) || config.getId() == null || config.getId() <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to create DICOM routing configuration.", false));
            }

            int affected = upsertRoutingChildren(config.getId(), hospitalId, normalizedRoutes, userId);
            if (affected > 0) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.CREATE_PATH, null, null, "DicomRouting", "Dicom Routing (Add)", "Add", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.CREATE_PATH, errorLine, error.toString(), "DicomRouting", "Dicom Routing (Add)", "Add", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> updateRouting(HospitalModalityServerRouteRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Request is required.", false));
            }
            Long hospitalId = resolveScopedHospitalId(request.getHospitalKey(), request.getHospitalId());
            request.setId(publicEntityKeyResolver.resolve(Entity.DICOM_ROUTING_CONFIG, request.getPublicKey(), null));
            if (request.getId() == null || request.getId() <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Routing configuration id is required.", false));
            }

            HospitalDicomRoutingConfigResponse existingConfig = dicomServerMapper.getRoutingConfigById(request.getId(), hospitalId);
            if (existingConfig == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Dicom routing not found.", false));
            }

            Long dicomServerId = firstPositive(
                    publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, request.getDicomServerKey(), null),
                    request.getDicomServerId(),
                    existingConfig.getDicomServerId()
            );
            if (dicomServerId == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Destination DICOM server is required.", false));
            }
            ResponseMessage<BaseResult> serverValidation = validateRoutingDestinationServer(hospitalId, dicomServerId);
            if (serverValidation != null) {
                return serverValidation;
            }
            if (dicomServerMapper.countActiveRoutingConfigByHospitalAndServer(hospitalId, dicomServerId, existingConfig.getId()) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("This hospital already has a DICOM routing configuration for this destination DICOM server.", false));
            }

            Long userId = userService.getUserAuth().getId();
            List<HospitalModalityServerRoute> normalizedRoutes;
            try {
                normalizedRoutes = normalizeUpdateRoutes(hospitalId, request.getRoutes());
            } catch (IllegalArgumentException validationError) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
            }
            if (normalizedRoutes.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("routes is required.", false));
            }

            ResponseMessage<BaseResult> validationResult = validateRoutesForHospital(hospitalId, normalizedRoutes);
            if (validationResult != null) {
                return validationResult;
            }

            dicomServerMapper.deactivateRoutesByRoutingConfigId(existingConfig.getId(), hospitalId, userId);
            int affected = upsertRoutingChildren(existingConfig.getId(), hospitalId, normalizedRoutes, userId);
            dicomServerMapper.touchRoutingConfig(existingConfig.getId(), hospitalId, dicomServerId, userId);

            if (affected > 0) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.UPDATE_PATH, null, null, "DicomRouting", "Dicom Routing (Edit)", "Edit", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to complete the request. Please try again.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.UPDATE_PATH, errorLine, error.toString(), "DicomRouting", "Dicom Routing (Edit)", "Edit", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @CacheEvict(
            cacheNames = {
                    CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL,
                    CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL
            },
            allEntries = true
    )
    public ResponseMessage<BaseResult> deleteRouting(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Route id is required.", false));
            }
            Long scopeHospitalId = resolveOptionalScopedHospitalId(null);
            Long userId = userService.getUserAuth().getId();
            HospitalDicomRoutingConfigResponse existingConfig = dicomServerMapper.getRoutingConfigById(id, scopeHospitalId);
            if (existingConfig == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Dicom routing not found.", false));
            }
            Long hospitalId = existingConfig.getHospitalId();
            dicomServerMapper.deactivateRoutesByRoutingConfigId(id, hospitalId, userId);
            Integer deleteResult = dicomServerMapper.deleteRoutingConfigById(id, hospitalId, userId);
            if (deleteResult != null && deleteResult > 0) {
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.DELETE_PATH, null, null, "DicomRouting", "Dicom Routing (Delete)", "Delete", 1, "Success", startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Dicom routing not found.", false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.DELETE_PATH, errorLine, error.toString(), "DicomRouting", "Dicom Routing (Delete)", "Delete", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    @Transactional
    public ResponseMessage<BaseResult> buildRoutingDicomServerConfig(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Routing configuration id is required.", false));
            }

            Long hospitalId = resolveOptionalScopedHospitalId(null);
            HospitalDicomRoutingConfigResponse routeConfig = dicomServerMapper.getRoutingConfigById(id, hospitalId);
            if (routeConfig == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Dicom routing not found.", false));
            }
            enrichRoutingConfigRows(List.of(routeConfig));

            List<HospitalModalityServerRouteResponse> routes = dicomServerMapper.listRoutesByRoutingConfigIds(
                    List.of(routeConfig.getId()),
                    hospitalId,
                    null
            );
            if (routes == null || routes.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("No active route links found for this routing configuration.", false));
            }

            Map<Long, List<HospitalModalityServerRouteResponse>> routesByServer = routes.stream()
                    .filter(route -> route.getDicomServerId() != null && route.getDicomServerId() > 0)
                    .collect(Collectors.groupingBy(
                            HospitalModalityServerRouteResponse::getDicomServerId,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
            if (routesByServer.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("No active DICOM server route links found.", false));
            }

            String callbackScriptContent = loadCallbackScript();
            List<DicomServerConfigBuildResponse> configRows = new ArrayList<>();
            for (Map.Entry<Long, List<HospitalModalityServerRouteResponse>> entry : routesByServer.entrySet()) {
                List<HospitalModalityServerRouteResponse> serverRoutes = entry.getValue();
                if (serverRoutes == null || serverRoutes.isEmpty()) {
                    continue;
                }
                HospitalModalityServerRouteResponse serverRoute = serverRoutes.get(0);
                ensureDicomServerHttpCredential(serverRoute);
                DicomServerConfigBuildResponse response = new DicomServerConfigBuildResponse();
                response.setRoutingConfigId(routeConfig.getId());
                response.setHospitalId(routeConfig.getHospitalId());
                response.setHospitalPublicKey(routeConfig.getHospitalPublicKey());
                response.setHospitalName(routeConfig.getHospitalName());
                response.setDicomServerId(serverRoute.getDicomServerId());
                response.setDicomServerPublicKey(serverRoute.getDicomServerPublicKey());
                response.setDicomServerName(serverRoute.getDicomServerName());
                response.setFileName(buildDicomServerConfigFileName(routeConfig, serverRoute));
                response.setConfig(buildDicomServerConfig(routeConfig, serverRoute, serverRoutes));
                response.setProjectName(buildDicomServerProjectName(routeConfig, serverRoute, true));
                DicomServerCallbackCredential callbackCredential = provisionDicomServerCallbackCredential(serverRoute, response.getProjectName());
                String pacsResultApiKey = provisionPacsResultApiKey(serverRoute);
                String artifactStem = response.getFileName().replaceFirst("(?i)\\.json$", "");
                response.setEnvironmentFileName(artifactStem + ".env");
                response.setEnvironmentContent(buildCallbackEnvironmentContent(serverRoute, callbackCredential, pacsResultApiKey));
                response.setCallbackScriptFileName(artifactStem + "-" + CALLBACK_SCRIPT_FILE_NAME);
                response.setCallbackScriptContent(callbackScriptContent);
                response.setSetupFileName(artifactStem + "-setup.txt");
                response.setCallbackClientId(callbackCredential.clientId());
                response.setZipFileName(response.getProjectName() + ".zip");
                response.setSetupContent(buildCallbackSetupContent(response));
                response.setZipContentBase64(buildDicomServerProjectZipBase64(response));
                redactDicomServerBuildResponse(response);
                configRows.add(response);
            }
            if (configRows.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("No DICOM server deployment package was generated for this routing configuration.", false));
            }
            Long userId = userService.getUserAuth().getId();
            Integer lockedRows = dicomServerMapper.markRoutingConfigPackageBuilt(routeConfig.getId(), routeConfig.getHospitalId(), userId);
            if (lockedRows == null || lockedRows <= 0) {
                throw new IllegalStateException("Unable to lock hospital deployment identity for this routing configuration.");
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.BUILD_CONFIG_PATH, null, null, "DicomRouting", "DICOM Routing (Build Config)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", configRows, true));
        } catch (IllegalArgumentException | IllegalStateException validationError) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (validationError.getStackTrace() != null && validationError.getStackTrace().length > 0) ? (long) validationError.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.BUILD_CONFIG_PATH, errorLine, validationError.toString(), "DicomRouting", "DICOM Routing (Build Config)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.DicomRouting.BASE_PATH + ApiConstants.DicomRouting.BUILD_CONFIG_PATH, errorLine, error.toString(), "DicomRouting", "DICOM Routing (Build Config)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            String detail = trimToNull(error.getMessage());
            String message = "Unable to build UDAYA_DICOM_SERVER deployment package"
                    + (detail == null ? " (" + error.getClass().getSimpleName() + ")." : ": " + detail);
            return ResponseMessageUtils.makeResponse(false, messageService.message(message, null, false));
        }
    }

    private Long resolveScopedHospitalId(Long requestedHospitalId) {
        return resolveHospitalId(requestedHospitalId, false);
    }

    private Long resolveScopedHospitalId(String requestedHospitalKey, Long requestedHospitalId) {
        Long resolvedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, requestedHospitalKey, null);
        return resolveHospitalId(firstPositive(resolvedHospitalId, requestedHospitalId), false);
    }

    private Long resolveOptionalScopedHospitalId(Long requestedHospitalId) {
        return resolveHospitalId(requestedHospitalId, true);
    }

    private Long resolveHospitalId(Long requestedHospitalId, boolean allowAllHospitalsForSuperAdmin) {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null) {
            throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
        }
        if (isSuperAdmin(principal.userId())) {
            if (requestedHospitalId != null && requestedHospitalId > 0) {
                return requestedHospitalId;
            }
            if (allowAllHospitalsForSuperAdmin) {
                return null;
            }
        }
        return principal.hospitalId();
    }

    private static boolean isSuperAdmin(Long userId) {
        return userId != null && userId == 1L;
    }

    private static void preserveExistingDicomServerConfig(
            HospitalDicomServerRequestUpdate requestUpdate,
            HospitalDicomServerResponse existing
    ) {
        if (existing == null) {
            return;
        }
        if (requestUpdate.getDicomwebPath() == null) requestUpdate.setDicomwebPath(existing.getDicomwebPath());
        if (requestUpdate.getPublicHealthCheckUrl() == null) requestUpdate.setPublicHealthCheckUrl(existing.getPublicHealthCheckUrl());
        if (requestUpdate.getStorageDirectory() == null) requestUpdate.setStorageDirectory(existing.getStorageDirectory());
        if (requestUpdate.getIndexDirectory() == null) requestUpdate.setIndexDirectory(existing.getIndexDirectory());
        if (requestUpdate.getMaximumStorageSize() == null) requestUpdate.setMaximumStorageSize(existing.getMaximumStorageSize());
        if (requestUpdate.getMaximumPatientCount() == null) requestUpdate.setMaximumPatientCount(existing.getMaximumPatientCount());
        if (requestUpdate.getRemoteAccessAllowed() == null) requestUpdate.setRemoteAccessAllowed(existing.getRemoteAccessAllowed());
        if (requestUpdate.getHttpServerEnabled() == null) requestUpdate.setHttpServerEnabled(existing.getHttpServerEnabled());
        if (requestUpdate.getEnableHttpCompression() == null) requestUpdate.setEnableHttpCompression(existing.getEnableHttpCompression());
        if (requestUpdate.getSslEnabled() == null) requestUpdate.setSslEnabled(existing.getSslEnabled());
        if (requestUpdate.getAuthenticationEnabled() == null) requestUpdate.setAuthenticationEnabled(existing.getAuthenticationEnabled());
        if (requestUpdate.getAuthorizationEnabled() == null) requestUpdate.setAuthorizationEnabled(existing.getAuthorizationEnabled());
        if (requestUpdate.getAuthorizationRoot() == null) requestUpdate.setAuthorizationRoot(existing.getAuthorizationRoot());
        if (requestUpdate.getAuthorizationCheckedLevel() == null) requestUpdate.setAuthorizationCheckedLevel(existing.getAuthorizationCheckedLevel());
        if (requestUpdate.getDicomAlwaysAllowEcho() == null) requestUpdate.setDicomAlwaysAllowEcho(existing.getDicomAlwaysAllowEcho());
        if (requestUpdate.getDicomAlwaysAllowFind() == null) requestUpdate.setDicomAlwaysAllowFind(existing.getDicomAlwaysAllowFind());
        if (requestUpdate.getDicomAlwaysAllowGet() == null) requestUpdate.setDicomAlwaysAllowGet(existing.getDicomAlwaysAllowGet());
        if (requestUpdate.getDicomAlwaysAllowMove() == null) requestUpdate.setDicomAlwaysAllowMove(existing.getDicomAlwaysAllowMove());
        if (requestUpdate.getDicomAlwaysAllowStore() == null) requestUpdate.setDicomAlwaysAllowStore(existing.getDicomAlwaysAllowStore());
        if (requestUpdate.getDicomCheckCalledAet() == null) requestUpdate.setDicomCheckCalledAet(existing.getDicomCheckCalledAet());
        if (requestUpdate.getDicomTlsEnabled() == null) requestUpdate.setDicomTlsEnabled(existing.getDicomTlsEnabled());
        if (requestUpdate.getDicomScpTimeout() == null) requestUpdate.setDicomScpTimeout(existing.getDicomScpTimeout());
        if (requestUpdate.getDicomPeersJson() == null) requestUpdate.setDicomPeersJson(existing.getDicomPeersJson());
        if (requestUpdate.getWorklistsEnabled() == null) requestUpdate.setWorklistsEnabled(existing.getWorklistsEnabled());
        if (requestUpdate.getWorklistsDatabase() == null) requestUpdate.setWorklistsDatabase(existing.getWorklistsDatabase());
        if (requestUpdate.getPluginsPaths() == null) requestUpdate.setPluginsPaths(existing.getPluginsPaths());
    }

    private static void applyDicomServerConfig(
            HospitalDicomServer server,
            HospitalDicomServerRequestUpdate requestUpdate,
            ResolvedDicomEndpoint endpoint
    ) {
        server.setStorageDirectory(defaultString(requestUpdate.getStorageDirectory(), DEFAULT_STORAGE_DIRECTORY));
        server.setIndexDirectory(defaultString(requestUpdate.getIndexDirectory(), DEFAULT_STORAGE_DIRECTORY));
        server.setMaximumStorageSize(defaultNonNegativeLong(requestUpdate.getMaximumStorageSize(), 0L, "Maximum storage size"));
        server.setMaximumPatientCount(defaultNonNegativeLong(requestUpdate.getMaximumPatientCount(), 0L, "Maximum patient count"));
        server.setRemoteAccessAllowed(defaultBoolean(requestUpdate.getRemoteAccessAllowed(), true));
        server.setHttpServerEnabled(defaultBoolean(requestUpdate.getHttpServerEnabled(), true));
        server.setEnableHttpCompression(defaultBoolean(requestUpdate.getEnableHttpCompression(), true));
        server.setSslEnabled(defaultBoolean(requestUpdate.getSslEnabled(), endpoint.baseUrl.toLowerCase().startsWith("https://")));
        server.setAuthenticationEnabled(defaultBoolean(requestUpdate.getAuthenticationEnabled(), true));
        server.setAuthorizationEnabled(defaultBoolean(requestUpdate.getAuthorizationEnabled(), true));
        server.setAuthorizationRoot(defaultString(requestUpdate.getAuthorizationRoot(), DEFAULT_AUTHORIZATION_ROOT));
        server.setAuthorizationCheckedLevel(defaultString(requestUpdate.getAuthorizationCheckedLevel(), DEFAULT_AUTHORIZATION_CHECKED_LEVEL));
        server.setDicomAlwaysAllowEcho(defaultBoolean(requestUpdate.getDicomAlwaysAllowEcho(), true));
        server.setDicomAlwaysAllowFind(defaultBoolean(requestUpdate.getDicomAlwaysAllowFind(), true));
        server.setDicomAlwaysAllowGet(defaultBoolean(requestUpdate.getDicomAlwaysAllowGet(), true));
        server.setDicomAlwaysAllowMove(defaultBoolean(requestUpdate.getDicomAlwaysAllowMove(), true));
        server.setDicomAlwaysAllowStore(defaultBoolean(requestUpdate.getDicomAlwaysAllowStore(), true));
        server.setDicomCheckCalledAet(defaultBoolean(requestUpdate.getDicomCheckCalledAet(), false));
        server.setDicomTlsEnabled(defaultBoolean(requestUpdate.getDicomTlsEnabled(), false));
        server.setDicomScpTimeout(defaultPositiveInteger(requestUpdate.getDicomScpTimeout(), 30, "DICOM SCP timeout"));
        server.setDicomPeersJson(normalizeJsonObject(requestUpdate.getDicomPeersJson()));
        server.setWorklistsEnabled(defaultBoolean(requestUpdate.getWorklistsEnabled(), true));
        server.setWorklistsDatabase(defaultString(requestUpdate.getWorklistsDatabase(), DEFAULT_WORKLISTS_DATABASE));
        server.setPluginsPaths(normalizeArchivePluginPaths(defaultString(requestUpdate.getPluginsPaths(), DEFAULT_PLUGINS_PATHS)));
    }

    private static String normalizeArchivePluginPaths(String value) {
        return defaultString(value, DEFAULT_PLUGINS_PATHS)
                .replace("/usr/share/UDAYA_DICOM_SERVER/plugins", "/usr/share/dicom_server/plugins")
                .replace("/usr/local/share/UDAYA_DICOM_SERVER/plugins", "/usr/local/share/dicom_server/plugins")
                .replace("/usr/share/udaya_dicom_server/plugins", "/usr/share/dicom_server/plugins")
                .replace("/usr/local/share/udaya_dicom_server/plugins", "/usr/local/share/dicom_server/plugins");
    }

    private static Boolean defaultBoolean(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static String defaultString(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static Long defaultNonNegativeLong(Long value, Long fallback, String label) {
        Long resolved = value == null ? fallback : value;
        if (resolved < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }
        return resolved;
    }

    private static Integer defaultPositiveInteger(Integer value, Integer fallback, String label) {
        Integer resolved = value == null ? fallback : value;
        if (resolved <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
        return resolved;
    }

    private static String normalizeJsonObject(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return DEFAULT_DICOM_PEERS_JSON;
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("DICOM peers must be a JSON object.");
        }
        return trimmed;
    }

    private static ResolvedDicomEndpoint resolveDicomEndpoint(
            HospitalDicomServerRequestUpdate requestUpdate,
            HospitalDicomServerResponse existing
    ) {
        Integer dicomPort = resolveDicomPort(firstNonNull(
                requestUpdate.getDicomPort(),
                existing == null ? null : existing.getDicomPort()
        ));

        boolean hasRequestedHostOrPort = hasText(requestUpdate.getIpAddress()) || requestUpdate.getPort() != null;
        String ipAddress = hasRequestedHostOrPort
                ? firstNonBlank(requestUpdate.getIpAddress(), existing == null ? null : existing.getIpAddress())
                : firstNonBlank(existing == null ? null : existing.getIpAddress(), requestUpdate.getIpAddress());
        Integer dicomWebPort = hasRequestedHostOrPort
                ? firstNonNull(requestUpdate.getPort(), existing == null ? null : existing.getPort())
                : firstNonNull(existing == null ? null : existing.getPort(), requestUpdate.getPort());
        if (ipAddress == null) {
            throw new IllegalArgumentException("DICOM server IP / host is required.");
        }
        if (dicomWebPort == null || dicomWebPort <= 0) {
            throw new IllegalArgumentException("DICOMweb port is required.");
        }

        String host = stripUrlScheme(ipAddress);
        if (!hasText(host)) {
            throw new IllegalArgumentException("DICOM server IP / host is required.");
        }
        assertSplitServerHostAllowed(host, "DICOM server IP / host");
        Boolean sslEnabled = firstNonNull(requestUpdate.getSslEnabled(), existing == null ? null : existing.getSslEnabled());
        String scheme = Boolean.TRUE.equals(sslEnabled) ? "https" : "http";
        return new ResolvedDicomEndpoint(host, dicomWebPort, dicomPort, scheme + "://" + host + ":" + dicomWebPort);
    }

    private static String normalizeOptionalHttpBaseUrl(String rawBaseUrl, String label) {
        String value = trimToNull(rawBaseUrl);
        if (value == null) {
            return null;
        }
        return parseHttpBaseUrl(value, DEFAULT_DICOM_PORT, label).baseUrl;
    }

    private static String normalizeOptionalHealthCheckUrl(String rawUrl, String fallbackBaseUrl, String label) {
        String value = firstNonBlank(rawUrl, fallbackBaseUrl);
        if (value == null) {
            return null;
        }

        String valueWithScheme = value.matches("(?i)^[a-z][a-z0-9+.-]*://.*") ? value : "http://" + value;
        URI uri;
        try {
            uri = new URI(valueWithScheme);
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException(label + " must be valid. Example: http://192.168.8.12:8042/system");
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!hasText(scheme) || !hasText(host)) {
            throw new IllegalArgumentException(label + " must include protocol and host. Example: http://192.168.8.12:8042/system");
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(label + " must use http or https.");
        }
        assertSplitServerHostAllowed(host, label);

        String path = normalizePath(uri.getPath());
        String normalizedPath = hasText(path) ? path : "/system";
        return scheme.toLowerCase() + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + normalizedPath;
    }

    private static ResolvedDicomEndpoint parseHttpBaseUrl(String rawBaseUrl, Integer dicomPort, String label) {
        String value = trimToNull(rawBaseUrl);
        if (value == null) {
            throw new IllegalArgumentException("DICOM server IP / host is required.");
        }

        String valueWithScheme = value.matches("(?i)^[a-z][a-z0-9+.-]*://.*") ? value : "http://" + value;
        URI uri;
        try {
            uri = new URI(valueWithScheme);
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException(label + " must be valid. Example: https://dicom-server.example.com");
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!hasText(scheme) || !hasText(host)) {
            throw new IllegalArgumentException(label + " must include protocol and host. Example: https://dicom-server.example.com");
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(label + " must use http or https.");
        }
        assertSplitServerHostAllowed(host, label);

        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(scheme) ? 443 : 80);
        String normalizedPath = normalizePath(uri.getPath());
        String normalizedBaseUrl = scheme.toLowerCase() + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + normalizedPath;
        return new ResolvedDicomEndpoint(host, port, dicomPort, normalizedBaseUrl);
    }

    private static Integer resolveDicomPort(Integer dicomPort) {
        if (dicomPort == null) {
            return DEFAULT_DICOM_PORT;
        }
        if (dicomPort <= 0) {
            throw new IllegalArgumentException("DICOM port must be greater than zero.");
        }
        return dicomPort;
    }

    private static String firstNonBlank(String... values) {
        return FunctionHelper.firstNonBlankOrNull(values);
    }

    private static void assertSplitServerHostAllowed(String host, String label) {
        String normalized = trimToNull(host);
        if (normalized == null) {
            return;
        }
        if (isDockerOnlyHost(normalized)) {
            throw new IllegalArgumentException(label + " must use a fixed server IP or DNS name, not a Docker-only host like " + normalized + ".");
        }
    }

    private static boolean isDockerOnlyHost(String host) {
        String normalized = host.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith(".docker.internal")
                || normalized.matches("^dicom_server_[a-z0-9-]+$")
                || normalized.matches("^udaya_dicom_server_[a-z0-9-]+$")
                || normalized.matches("^udaya-dicom-server-[a-z0-9-]+$");
    }

    private static String stripUrlScheme(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            try {
                URI uri = new URI(trimmed);
                if (hasText(uri.getHost())) {
                    return uri.getHost();
                }
            } catch (URISyntaxException ex) {
                LOGGER.debug("URI parse failed during host extraction, using fallback normalization: {}", ex.getMessage());
            }
        }
        String host = trimmed.replaceFirst("(?i)^https?://", "");
        int slashIndex = host.indexOf('/');
        if (slashIndex >= 0) {
            host = host.substring(0, slashIndex);
        }
        int portIndex = host.indexOf(':');
        if (portIndex > 0) {
            host = host.substring(0, portIndex);
        }
        return host.replaceAll("/+$", "");
    }

    private static String normalizePath(String path) {
        if (!hasText(path) || "/".equals(path.trim())) {
            return "";
        }
        return path.trim().replaceAll("/+$", "");
    }

    private static String normalizeDicomwebPath(String path) {
        String normalized = normalizePath(firstNonBlank(path, DEFAULT_DICOMWEB_PATH));
        if (!hasText(normalized)) {
            return DEFAULT_DICOMWEB_PATH;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String buildDerivedDicomwebBaseUrl(String baseUrl) {
        return buildDerivedDicomwebBaseUrl(baseUrl, DEFAULT_DICOMWEB_PATH);
    }

    private String buildDerivedDicomwebBaseUrl(String baseUrl, String dicomwebPath) {
        String normalizedBaseUrl = trimToNull(baseUrl);
        String normalizedPath = normalizeDicomwebPath(dicomwebPath);
        if (normalizedBaseUrl == null) {
            return null;
        }

        String base = normalizedBaseUrl.replaceAll("/+$", "");
        return (base + normalizedPath).replaceAll("/+$", "");
    }

    private static final class ResolvedDicomEndpoint {
        private final String ipAddress;
        private final Integer dicomWebPort;
        private final Integer dicomPort;
        private final String baseUrl;

        private ResolvedDicomEndpoint(String ipAddress, Integer dicomWebPort, Integer dicomPort, String baseUrl) {
            this.ipAddress = ipAddress;
            this.dicomWebPort = dicomWebPort;
            this.dicomPort = dicomPort;
            this.baseUrl = baseUrl;
        }
    }

    private ResponseMessage<BaseResult> validateMachineDomain(Long hospitalId, Long modalityId) {
        Long hospitalModalityCount = modalityMapper.countActiveHospitalModality(hospitalId, modalityId);
        if (hospitalModalityCount == null || hospitalModalityCount <= 0) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Modality " + modalityId + " is not assigned to this hospital.", false));
        }
        return null;
    }

    private ResponseMessage<BaseResult> validateRoutingDestinationServer(Long hospitalId, Long dicomServerId) {
        if (dicomServerId == null || dicomServerId <= 0) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Destination DICOM server is required.", false));
        }
        Long activeServerCount = dicomServerMapper.countActiveDicomServersByHospital(hospitalId, List.of(dicomServerId));
        if (activeServerCount == null || activeServerCount != 1) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("Destination DICOM server is inactive or does not belong to this hospital.", false));
        }
        return null;
    }

    private static String buildDicomRoutingRelationMessage(String subject) {
        return subject + " is related to active DICOM Routing. Remove the related DICOM Routing records first.";
    }

    private HospitalDicomMachine normalizeMachineRequest(
            HospitalDicomMachineRequestUpdate request,
            Long hospitalId,
            Long machineId,
            Long userId
    ) {
        Long modalityId = publicEntityKeyResolver.resolve(Entity.MODALITY, request.getModalityKey(), null);
        if (modalityId == null) {
            modalityId = request.getModalityId();
        }
        if (modalityId == null || modalityId <= 0) {
            throw new IllegalArgumentException("Machine modality is required.");
        }
        String machineAeTitle = normalizeMachineAeTitle(request.getMachineAeTitle());
        String machineHost = normalizeMachineHost(request.getMachineHost());
        Integer machinePort = normalizeMachinePort(request.getMachinePort());
        if (!hasText(machineAeTitle)) {
            throw new IllegalArgumentException("Machine AE title is required.");
        }
        if (!hasText(machineHost)) {
            throw new IllegalArgumentException("Machine host/IP is required.");
        }
        if (machinePort == null || machinePort <= 0 || machinePort > 65535) {
            throw new IllegalArgumentException("Machine DICOM port must be between 1 and 65535.");
        }

        HospitalDicomMachine machine = new HospitalDicomMachine();
        machine.setId(machineId);
        machine.setHospitalId(hospitalId);
        machine.setModalityId(modalityId);
        machine.setMachineName(normalizeMachineName(request.getMachineName(), machineAeTitle));
        machine.setMachineAeTitle(machineAeTitle);
        machine.setMachineHost(machineHost);
        machine.setMachinePort(machinePort);
        machine.setCreatedBy(userId);
        machine.setModifiedBy(userId);
        return machine;
    }

    private HospitalModalityServerRoute toRouteFromMachine(
            Long hospitalId,
            Long requestedModalityId,
            Long machineId,
            int routeNumber
    ) {
        if (machineId == null || machineId <= 0) {
            throw new IllegalArgumentException("Route " + routeNumber + " requires a saved machine.");
        }
        HospitalDicomMachineResponse machine = dicomServerMapper.getDicomMachineById(machineId, hospitalId);
        if (machine == null || machine.getId() == null) {
            throw new IllegalArgumentException("Route " + routeNumber + " uses an invalid saved machine.");
        }
        if (!Long.valueOf(1L).equals(machine.getIsActive())) {
            throw new IllegalArgumentException("Route " + routeNumber + " uses an inactive machine.");
        }
        if (requestedModalityId == null || !requestedModalityId.equals(machine.getModalityId())) {
            throw new IllegalArgumentException("Route " + routeNumber + " modality must match the selected machine modality.");
        }

        HospitalModalityServerRoute route = new HospitalModalityServerRoute();
        route.setMachineId(machine.getId());
        route.setModalityId(requestedModalityId);
        return route;
    }

    private List<HospitalModalityServerRoute> normalizeRoutes(Long hospitalId, List<HospitalModalityServerRouteSaveRequest.RouteItem> routeItems) {
        if (routeItems == null || routeItems.isEmpty()) {
            return List.of();
        }

        Map<Long, HospitalModalityServerRoute> routesByMachine = new LinkedHashMap<>();
        int routeNumber = 0;
        for (HospitalModalityServerRouteSaveRequest.RouteItem item : routeItems) {
            routeNumber++;
            if (item == null) {
                throw new IllegalArgumentException("Route " + routeNumber + " is required.");
            }

            Long resolvedModalityId = firstPositive(
                    publicEntityKeyResolver.resolve(Entity.MODALITY, item.getModalityKey(), null),
                    item.getModalityId()
            );
            if (resolvedModalityId == null || resolvedModalityId <= 0) {
                throw new IllegalArgumentException("Route " + routeNumber + " requires a modality.");
            }

            HospitalModalityServerRoute route = toRouteFromMachine(
                    hospitalId,
                    resolvedModalityId,
                    firstPositive(
                            publicEntityKeyResolver.resolve(Entity.DICOM_MACHINE, item.getMachineKey(), null),
                            item.getMachineId()
                    ),
                    routeNumber
            );

            if (routesByMachine.containsKey(route.getMachineId())) {
                throw new IllegalArgumentException("Route " + routeNumber + " duplicates a machine already used above.");
            }
            routesByMachine.put(route.getMachineId(), route);
        }
        return new ArrayList<>(routesByMachine.values());
    }
    private List<HospitalModalityServerRoute> normalizeUpdateRoutes(
            Long hospitalId,
            List<HospitalModalityServerRouteRequestUpdate.RouteItem> routeItems
    ) {
        if (routeItems == null || routeItems.isEmpty()) {
            return List.of();
        }
        Map<Long, HospitalModalityServerRoute> routesByMachine = new LinkedHashMap<>();
        int routeNumber = 0;
        for (HospitalModalityServerRouteRequestUpdate.RouteItem item : routeItems) {
            routeNumber++;
            if (item == null) {
                throw new IllegalArgumentException("Route " + routeNumber + " is required.");
            }
            Long modalityId = firstPositive(
                    publicEntityKeyResolver.resolve(Entity.MODALITY, item.getModalityKey(), null),
                    item.getModalityId()
            );
            if (modalityId == null || modalityId <= 0) {
                throw new IllegalArgumentException("Route " + routeNumber + " requires a modality.");
            }
            HospitalModalityServerRoute normalized = toRouteFromMachine(
                    hospitalId,
                    modalityId,
                    firstPositive(
                            publicEntityKeyResolver.resolve(Entity.DICOM_MACHINE, item.getMachineKey(), null),
                            item.getMachineId()
                    ),
                    routeNumber
            );

            if (routesByMachine.containsKey(normalized.getMachineId())) {
                throw new IllegalArgumentException("Route " + routeNumber + " duplicates a machine already used above.");
            }
            routesByMachine.put(normalized.getMachineId(), normalized);
        }
        return new ArrayList<>(routesByMachine.values());
    }

    private void enrichDicomServerRows(List<HospitalDicomServerResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (HospitalDicomServerResponse row : rows) {
            if (row == null) {
                continue;
            }
            if (!hasText(row.getDicomwebPath())) {
                row.setDicomwebPath(DEFAULT_DICOMWEB_PATH);
            }
            if (!hasText(row.getDicomwebBaseUrl())) {
                row.setDicomwebBaseUrl(buildDerivedDicomwebBaseUrl(firstNonBlank(row.getDicomServerUiBaseUrl(), row.getBaseUrl()), row.getDicomwebPath()));
            }
            row.setModalityIds(parseLongCsv(row.getModalityIdCsv()));
            row.setModalityPublicKeys(parseStringCsv(row.getModalityPublicKeyCsv()));
            row.setModalityNames(parseStringCsv(row.getModalityNameCsv()));
        }
        dicomServerHealthService.enrichDicomServerRows(rows);
    }

    private ResponseMessage<BaseResult> validateRoutesForHospital(Long hospitalId, List<HospitalModalityServerRoute> routes) {
        if (routes == null || routes.isEmpty()) {
            return ResponseMessageUtils.makeResponse(false, messageService.message("At least one route is required.", false));
        }

        Map<Long, Boolean> machineIds = new LinkedHashMap<>();
        for (HospitalModalityServerRoute route : routes) {
            Long modalityId = route.getModalityId();
            Long hospitalModalityCount = modalityMapper.countActiveHospitalModality(hospitalId, modalityId);
            if (hospitalModalityCount == null || hospitalModalityCount <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Modality " + modalityId + " is not assigned to this hospital.", false));
            }

            if (route.getMachineId() == null || route.getMachineId() <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("A saved machine is required for every route.", false));
            }
            if (machineIds.put(route.getMachineId(), Boolean.TRUE) != null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Machine " + route.getMachineId() + " is duplicated.", false));
            }
        }
        return null;
    }

    private static Long resolveActiveStatus(Long requestedStatus, Long fallbackStatus) {
        Long status = requestedStatus == null ? fallbackStatus : requestedStatus;
        if (status == null) {
            return 1L;
        }
        if (Long.valueOf(1L).equals(status) || Long.valueOf(2L).equals(status)) {
            return status;
        }
        throw new IllegalArgumentException("Active status must be 1 for active or 2 for inactive.");
    }

    private static Long firstPositive(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private int upsertRoutingChildren(Long routingConfigId, Long hospitalId, List<HospitalModalityServerRoute> routes, Long userId) {
        int affected = 0;
        if (routes == null || routes.isEmpty()) {
            return affected;
        }
        for (HospitalModalityServerRoute route : routes) {
            if (route == null || route.getModalityId() == null || route.getModalityId() <= 0) {
                continue;
            }
            Integer upsertResult = dicomServerMapper.upsertSingleRoute(
                    routingConfigId,
                    hospitalId,
                    route.getModalityId(),
                    route.getMachineId(),
                    userId,
                    userId
            );
            if (upsertResult != null) {
                affected += upsertResult;
            }
        }
        return affected;
    }
    private void enrichRoutingConfigRows(List<HospitalDicomRoutingConfigResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (HospitalDicomRoutingConfigResponse row : rows) {
            if (row == null) {
                continue;
            }
            row.setModalityIds(parseLongCsv(row.getModalityIdCsv()));
            row.setModalityPublicKeys(parseStringCsv(row.getModalityPublicKeyCsv()));
            row.setModalityNames(parseStringList(row.getModalityNameCsv()));
            row.setDicomServerIds(parseLongCsv(row.getDicomServerIdCsv()));
            row.setDicomServerPublicKeys(parseStringCsv(row.getDicomServerPublicKeyCsv()));
            row.setDicomServerNames(parseStringList(row.getDicomServerNameCsv()));
            if (row.getDicomServerId() == null && row.getDicomServerIds().size() == 1) {
                row.setDicomServerId(row.getDicomServerIds().get(0));
            }
            if (!hasText(row.getDicomServerName()) && row.getDicomServerNames().size() == 1) {
                row.setDicomServerName(row.getDicomServerNames().get(0));
            }
            if (row.getDicomServerCount() == null && row.getDicomServerId() != null) {
                row.setDicomServerCount(1L);
            }
            row.setDicomServerBaseUrls(parseStringList(row.getDicomServerBaseUrlCsv()));
            row.setAeTitles(parseStringList(row.getAeTitleCsv()));
            row.setMachineNames(parseStringList(row.getMachineNameCsv()));
            row.setMachineAeTitles(parseStringList(row.getMachineAeTitleCsv()));
            row.setMachineHosts(parseStringList(row.getMachineHostCsv()));
            row.setMachinePorts(parseIntegerList(row.getMachinePortCsv()));
        }
    }

    private void attachRoutesToRoutingConfigs(List<HospitalDicomRoutingConfigResponse> rows, Long hospitalId, Long modalityId) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> configIds = rows.stream()
                .map(HospitalDicomRoutingConfigResponse::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (configIds.isEmpty()) {
            return;
        }

        List<HospitalModalityServerRouteResponse> routeRows = dicomServerMapper.listRoutesByRoutingConfigIds(configIds, hospitalId, modalityId);
        Map<Long, List<HospitalModalityServerRouteResponse>> routesByConfigId = routeRows.stream()
                .filter(route -> route.getRoutingConfigId() != null)
                .collect(Collectors.groupingBy(
                        HospitalModalityServerRouteResponse::getRoutingConfigId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (HospitalDicomRoutingConfigResponse row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            row.setRoutes(routesByConfigId.getOrDefault(row.getId(), List.of()));
        }
    }

    private Map<String, Object> buildDicomServerConfig(
            HospitalDicomRoutingConfigResponse routeConfig,
            HospitalModalityServerRouteResponse serverRoute,
            List<HospitalModalityServerRouteResponse> routes
    ) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("Name", buildDicomServerName(serverRoute));
        config.put("StorageDirectory", defaultString(serverRoute.getStorageDirectory(), DEFAULT_STORAGE_DIRECTORY));
        config.put("IndexDirectory", defaultString(serverRoute.getIndexDirectory(), DEFAULT_STORAGE_DIRECTORY));
        config.put("MaximumStorageSize", defaultNonNegativeLong(serverRoute.getMaximumStorageSize(), 0L, "Maximum storage size"));
        config.put("MaximumPatientCount", defaultNonNegativeLong(serverRoute.getMaximumPatientCount(), 0L, "Maximum patient count"));

        boolean authorizationEnabled = true;
        config.put("HttpServerEnabled", true);
        config.put("RemoteAccessAllowed", true);
        config.put("AuthenticationEnabled", defaultBoolean(serverRoute.getAuthenticationEnabled(), true));
        config.put("CorsEnabled", true);

        Map<String, Object> authorization = new LinkedHashMap<>();
        authorization.put("Enabled", authorizationEnabled);
        authorization.put("Root", defaultString(serverRoute.getAuthorizationRoot(), DEFAULT_AUTHORIZATION_ROOT));
        authorization.put("WebServiceTokenValidationUrl", resolveCallbackApiBaseUrl(serverRoute) + VIEWER_DICOMWEB_AUTHORIZE_PATH);
        authorization.put("WebServiceTokenDecoderUrl", resolveCallbackApiBaseUrl(serverRoute) + VIEWER_DICOMWEB_DECODE_PATH);
        authorization.put("WebServiceUserProfileUrl", resolveCallbackApiBaseUrl(serverRoute) + VIEWER_DICOMWEB_PROFILE_PATH);
        authorization.put("WebServiceIdentifier", buildDicomServerName(serverRoute));
        authorization.put("TokenHttpHeaders", List.of("Authorization"));
        authorization.put("TokenGetArguments", List.of("token"));
        authorization.put("StandardConfigurations", List.of("ohif"));
        authorization.put("UncheckedResources", List.of("/system", "/instances", "/ui/app", "/ui/app/", "/ui/app/index.html"));
        authorization.put("UncheckedFolders", List.of("/ui/app/"));
        authorization.put("CheckedLevel", defaultString(serverRoute.getAuthorizationCheckedLevel(), DEFAULT_AUTHORIZATION_CHECKED_LEVEL));
        config.put("Authorization", authorization);

        config.put("RegisteredUsers", buildRegisteredUsersForConfig(serverRoute));

        config.put("DicomServerEnabled", true);
        config.put("DicomPort", DEFAULT_DICOM_PORT);
        config.put("DicomAet", resolveArchiveAeTitle(serverRoute));

        config.put("DicomAlwaysAllowEcho", defaultBoolean(serverRoute.getDicomAlwaysAllowEcho(), true));
        config.put("DicomAlwaysAllowFind", defaultBoolean(serverRoute.getDicomAlwaysAllowFind(), true));
        config.put("DicomAlwaysAllowFindWorklist", defaultBoolean(serverRoute.getWorklistsEnabled(), true));
        config.put("DicomAlwaysAllowGet", defaultBoolean(serverRoute.getDicomAlwaysAllowGet(), true));
        config.put("DicomAlwaysAllowMove", defaultBoolean(serverRoute.getDicomAlwaysAllowMove(), true));
        config.put("DicomAlwaysAllowStore", defaultBoolean(serverRoute.getDicomAlwaysAllowStore(), true));
        config.put("DicomCheckCalledAet", defaultBoolean(serverRoute.getDicomCheckCalledAet(), false));
        config.put("DicomTlsEnabled", defaultBoolean(serverRoute.getDicomTlsEnabled(), false));
        config.put("DicomScpTimeout", defaultPositiveInteger(serverRoute.getDicomScpTimeout(), 30, "DICOM SCP timeout"));

        config.put("DicomPeers", parseJsonObjectForConfig(serverRoute.getDicomPeersJson()));
        config.put("DicomModalities", buildDicomModalitiesForConfig(routes));

        config.put("HttpPort", DICOM_SERVER_CONTAINER_HTTP_PORT);
        config.put("HttpCompressionEnabled", defaultBoolean(serverRoute.getEnableHttpCompression(), true));
        config.put("SslEnabled", defaultBoolean(serverRoute.getSslEnabled(), false));

        config.put("Plugins", splitConfigPaths(serverRoute.getPluginsPaths(), DEFAULT_PLUGINS_PATHS));
        config.put("LuaScripts", List.of(CALLBACK_SCRIPT_CONTAINER_PATH));
        config.put("StableAge", 5);

        Map<String, Object> explorer = new LinkedHashMap<>();
        explorer.put("CustomTitle", "UDAYA DICOM SERVER");
        explorer.put("CustomLogoPath", DICOM_SERVER_BRAND_CONTAINER_DIRECTORY + "/" + DICOM_SERVER_BRAND_LOGO_FILE_NAME);
        explorer.put("CustomCssPath", DICOM_SERVER_BRAND_CONTAINER_DIRECTORY + "/" + DICOM_SERVER_BRAND_CSS_FILE_NAME);
        Map<String, Object> explorerUiOptions = new LinkedHashMap<>();
        explorerUiOptions.put("EnableLinkToLegacyUi", false);
        explorer.put("UiOptions", explorerUiOptions);
        config.put("OrthancExplorer2", explorer);
        config.put("UiOptions", explorerUiOptions);

        Map<String, Object> dicomWeb = new LinkedHashMap<>();
        dicomWeb.put("Enable", true);
        dicomWeb.put("Root", "/dicom-web/");
        dicomWeb.put("EnableWado", true);
        dicomWeb.put("WadoRoot", "/wado");
        dicomWeb.put("StudiesMetadata", "Full");
        dicomWeb.put("SeriesMetadata", "Full");
        config.put("DicomWeb", dicomWeb);

        Map<String, Object> worklists = new LinkedHashMap<>();
        worklists.put("Enable", defaultBoolean(serverRoute.getWorklistsEnabled(), true));
        worklists.put("Directory", defaultString(serverRoute.getWorklistsDatabase(), DEFAULT_WORKLISTS_DATABASE));
        worklists.put("FilterIssuerAet", false);
        worklists.put("LimitAnswers", 100);
        config.put("Worklists", worklists);

        return config;
    }

    private DicomServerCallbackCredential provisionDicomServerCallbackCredential(
            HospitalModalityServerRouteResponse serverRoute,
            String projectName
    ) {
        if (serverRoute == null || serverRoute.getDicomServerId() == null || serverRoute.getDicomServerId() <= 0L) {
            throw new IllegalArgumentException("DICOM server is required for UDAYA_DICOM_SERVER callback credentials.");
        }
        Long dicomServerId = serverRoute.getDicomServerId();
        String clientId = buildDicomServerCallbackClientId(projectName);
        String clientSecret = generateCallbackClientSecret();
        String clientName = "UDAYA_DICOM_SERVER Callback - " + defaultString(serverRoute.getDicomServerName(), "DICOM Server " + dicomServerId);
        Integer affected = oauth2ClientMapper.upsertDicomServerCallbackClient(
                dicomServerId,
                clientId,
                clientName,
                passwordEncoder.encode(clientSecret)
        );
        if (affected == null || affected <= 0) {
            throw new IllegalStateException("Unable to provision UDAYA_DICOM_SERVER callback credentials.");
        }
        return new DicomServerCallbackCredential(clientId, clientSecret);
    }

    private String provisionPacsResultApiKey(HospitalModalityServerRouteResponse serverRoute) {
        if (serverRoute == null || serverRoute.getDicomServerId() == null || serverRoute.getDicomServerId() <= 0L) {
            throw new IllegalArgumentException("DICOM server is required for PACS Result API key generation.");
        }
        if (serverRoute.getHospitalId() == null || serverRoute.getHospitalId() <= 0L) {
            throw new IllegalArgumentException("Hospital is required for PACS Result API key generation.");
        }
        String apiKey = generatePacsResultApiKey();
        Integer affected = dicomServerMapper.updateDicomServerPacsResultApiKeyHash(
                serverRoute.getDicomServerId(),
                serverRoute.getHospitalId(),
                passwordEncoder.encode(apiKey),
                userService.getUserAuth().getId()
        );
        if (affected == null || affected <= 0) {
            throw new IllegalStateException("Unable to provision PACS Result API key for DICOM Server #" + serverRoute.getDicomServerId() + ".");
        }
        serverRoute.setPacsResultApiKeyHash("generated");
        serverRoute.setHasPacsResultApiKey(Boolean.TRUE);
        return apiKey;
    }

    private String buildDicomServerCallbackClientId(String projectName) {
        String suffix = toSlug(defaultString(projectName, DICOM_SERVER_PREFIX + "_server")).replace('-', '_');
        if (suffix.startsWith(DICOM_SERVER_PREFIX + "_")) {
            suffix = suffix.substring((DICOM_SERVER_PREFIX + "_").length());
        }
        if (suffix.startsWith(LEGACY_DICOM_SERVER_PREFIX + "_")) {
            suffix = suffix.substring((LEGACY_DICOM_SERVER_PREFIX + "_").length());
        }
        if (suffix.isBlank()) {
            suffix = "server";
        }
        return DICOM_SERVER_PREFIX + "_" + suffix + "_callback";
    }

    private String buildCallbackEnvironmentContent(
            HospitalModalityServerRouteResponse serverRoute,
            DicomServerCallbackCredential credential,
            String pacsResultApiKey
    ) {
        String callbackApiBaseUrl = resolveCallbackApiBaseUrl(serverRoute);
        return String.join("\n",
                "# Generated for DICOM Server #" + serverRoute.getDicomServerId() + ". Keep this file private.",
                "# Building config again rotates this server-specific callback secret and PACS Result proxy API key.",
                "TZ=Asia/Phnom_Penh",
                "",
                "UDAYA_DICOM_SERVER_HTTP_PORT=" + resolveDicomServerHttpPublishPort(serverRoute),
                "UDAYA_DICOM_SERVER_DICOM_PORT=" + resolveDicomServerDicomPublishPort(serverRoute),
                "UDAYA_DICOM_SERVER_HTTP_USERNAME=" + escapeEnvironmentValue(requireDicomServerHttpUsername(serverRoute)),
                "UDAYA_DICOM_SERVER_HTTP_PASSWORD=" + escapeEnvironmentValue(requireDicomServerHttpPassword(serverRoute)),
                "",
                "PACS_RESULT_API_KEY=" + pacsResultApiKey,
                "PACS_RESULT_API_KEY_HEADER=X-PACS-RESULT-API-KEY",
                "",
                "UDAYA_PACS_API_AUTH_CALLBACK=" + callbackApiBaseUrl,
                "UDAYA_DICOM_SERVER_CALLBACK_ENABLED=true",
                "UDAYA_DICOM_SERVER_CALLBACK_URL=" + callbackApiBaseUrl + CALLBACK_RECEIVED_STUDY_PATH,
                "UDAYA_DICOM_SERVER_CALLBACK_TOKEN_URL=" + callbackApiBaseUrl + CALLBACK_TOKEN_PATH,
                "UDAYA_DICOM_SERVER_CALLBACK_CLIENT_ID=" + credential.clientId(),
                "UDAYA_DICOM_SERVER_CALLBACK_CLIENT_SECRET=" + credential.clientSecret(),
                "UDAYA_DICOM_SERVER_CALLBACK_TIMEOUT_SECONDS=30",
                "UDAYA_DICOM_SERVER_CALLBACK_MAX_ATTEMPTS=3",
                "UDAYA_DICOM_SERVER_CALLBACK_INSTANCE_WAIT_SECONDS=30",
                "UDAYA_DICOM_SERVER_CALLBACK_INSTANCE_POLL_SECONDS=2",
                ""
        );
    }

    private String requireDicomServerHttpUsername(HospitalModalityServerRouteResponse serverRoute) {
        String username = trimToNull(serverRoute == null ? null : serverRoute.getUsername());
        if (username == null) {
            throw new IllegalStateException("UDAYA_DICOM_SERVER HTTP username is required before building deployment config.");
        }
        return username;
    }

    private String requireDicomServerHttpPassword(HospitalModalityServerRouteResponse serverRoute) {
        String password = trimToNull(serverRoute == null ? null : serverRoute.getPassword());
        if (password == null) {
            throw new IllegalStateException("UDAYA_DICOM_SERVER HTTP password is required before building deployment config.");
        }
        return password;
    }

    private static String escapeEnvironmentValue(String value) {
        return defaultString(value, "").replace("\r", "").replace("\n", "");
    }

    private String buildCallbackSetupContent(DicomServerConfigBuildResponse response) {
        return String.join("\n",
                "UDAYA_DICOM_SERVER archive package for DICOM Server #" + response.getDicomServerId(),
                "",
                "1. Unzip " + response.getZipFileName() + ".",
                "2. Open the " + response.getProjectName() + " folder.",
                "3. Run: sudo bash ./scripts/deploy.sh",
                "",
                "The API callback endpoint stays fixed: " + CALLBACK_RECEIVED_STUDY_PATH,
                "The generated machine-client identity is server-specific: " + response.getCallbackClientId(),
                "The generated .env in this zip already contains this server private callback secret and PACS Result proxy API key.",
                "Building config again rotates only this DICOM Server callback secret and PACS Result proxy key.",
                ""
        );
    }

    private String buildDicomServerProjectZipBase64(DicomServerConfigBuildResponse response) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                String root = response.getProjectName() + "/";
                addZipDirectory(zip, root);
                addZipDirectory(zip, root + "config/");
                addZipDirectory(zip, root + "brand/");
                addZipDirectory(zip, root + "scripts/");
                addZipDirectory(zip, root + "worklists/");
                addZipFile(zip, root + ".env", response.getEnvironmentContent());
                addZipFile(zip, root + "Dockerfile", buildDicomServerDockerfileContent());
                addZipFile(zip, root + "docker-compose.yml", buildDicomServerDockerComposeContent(response));
                addZipFile(zip, root + "README.md", buildDicomServerProjectReadme(response));
                addZipFile(zip, root + "config/dicom_server.json", OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response.getConfig()));
                addZipFile(zip, root + "brand/" + DICOM_SERVER_BRAND_LOGO_FILE_NAME, buildDicomServerBrandLogoContent());
                addZipFile(zip, root + "brand/" + DICOM_SERVER_BRAND_CSS_FILE_NAME, buildDicomServerBrandCssContent());
                addZipFile(zip, root + "scripts/deploy.sh", buildDicomServerDeployScriptContent());
                addZipFile(zip, root + "scripts/" + CALLBACK_SCRIPT_FILE_NAME, response.getCallbackScriptContent());
                addZipFile(zip, root + "worklists/.gitkeep", "\n");
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException error) {
            throw new IllegalStateException("Unable to build UDAYA_DICOM_SERVER archive deployment zip.", error);
        }
    }

    private void addZipDirectory(ZipOutputStream zip, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.closeEntry();
    }

    private void addZipFile(ZipOutputStream zip, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        byte[] bytes = defaultString(content, "").getBytes(StandardCharsets.UTF_8);
        zip.write(bytes);
        zip.closeEntry();
    }

    private String buildDicomServerDockerComposeContent(DicomServerConfigBuildResponse response) {
        String projectName = response.getProjectName().replace('-', '_');
        String volumeName = projectName + "_data";
        String imageName = projectName + ":latest";
        String healthcheckCommand = buildDicomServerHealthcheckCommand(response.getConfig());
        return String.join("\n",
                "name: " + projectName,
                "",
                "services:",
                "  " + projectName + ":",
                "    build:",
                "      context: .",
                "      dockerfile: Dockerfile",
                "    image: ${UDAYA_DICOM_SERVER_IMAGE:-" + imageName + "}",
                "    command: [\"" + DICOM_SERVER_CONFIG_CONTAINER_PATH + "\"]",
                "    container_name: " + projectName,
                "    restart: unless-stopped",
                "    env_file:",
                "      - ./.env",
                "    environment:",
                "      DICOM_WEB_PLUGIN_ENABLED: \"true\"",
                "      WORKLISTS_PLUGIN_ENABLED: \"true\"",
                "    expose:",
                "      - \"8042\"",
                "    ports:",
                "      - \"${UDAYA_DICOM_SERVER_HTTP_PORT:-8042}:8042\"",
                "      - \"${UDAYA_DICOM_SERVER_DICOM_PORT:-4242}:4242\"",
                "    volumes:",
                "      - ./brand:" + DICOM_SERVER_BRAND_CONTAINER_DIRECTORY + ":ro",
                "      - ./config/dicom_server.json:" + DICOM_SERVER_CONFIG_CONTAINER_PATH + ":ro",
                "      - ./scripts:" + DICOM_SERVER_SCRIPT_CONTAINER_DIRECTORY + ":ro",
                "      - ./worklists:" + DEFAULT_WORKLISTS_DATABASE,
                "      - " + volumeName + ":" + DEFAULT_STORAGE_DIRECTORY,
                "    healthcheck:",
                "      test: [\"CMD-SHELL\", \"" + healthcheckCommand + "\"]",
                "      interval: 15s",
                "      timeout: 10s",
                "      retries: 10",
                "      start_period: 20s",
                "    logging:",
                "      driver: json-file",
                "      options:",
                "        max-size: \"10m\"",
                "        max-file: \"3\"",
                "",
                "volumes:",
                "  " + volumeName + ":",
                "    name: " + volumeName,
                ""
        );
    }

    private String buildDicomServerDockerfileContent() {
        return String.join("\n",
                "# syntax=docker/dockerfile:1.7",
                "",
                "FROM " + DICOM_SERVER_BASE_DOCKER_IMAGE,
                "",
                "COPY brand " + DICOM_SERVER_BRAND_CONTAINER_DIRECTORY,
                "",
                "RUN set -eu; \\",
                "    native_root=\"/usr/share/$(printf '\\157\\162\\164\\150\\141\\156\\143')\"; \\",
                "    mkdir -p /usr/share/dicom_server; \\",
                "    mkdir -p /usr/share/udaya_dicom_server; \\",
                "    rm -rf /usr/local/share/dicom_server; \\",
                "    rm -rf /usr/local/share/udaya_dicom_server; \\",
                "    ln -sfn \"${native_root}/plugins\" /usr/share/dicom_server/plugins; \\",
                "    ln -sfn \"${native_root}/plugins\" /usr/share/udaya_dicom_server/plugins",
                ""
        );
    }

    private String buildDicomServerBrandLogoContent() {
        return String.join("\n",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 2048 820\" role=\"img\" aria-labelledby=\"title\">",
                "  <title id=\"title\">UDAYA Technology</title>",
                "  <path d=\"M24 118H1804L2048 660H24Z\" fill=\"#292767\"/>",
                "  <text x=\"104\" y=\"590\" fill=\"#ffffff\" font-family=\"Arial Black, Impact, Arial, Helvetica, sans-serif\" font-size=\"520\" font-weight=\"900\" letter-spacing=\"-30\">UDAYA</text>",
                "  <path d=\"M1780 12H1860C1884 12 1902 28 1910 52L2048 660H1962C1938 660 1918 644 1912 620Z\" fill=\"#c8192e\"/>",
                "  <text x=\"104\" y=\"760\" fill=\"#000000\" font-family=\"Arial, Helvetica, sans-serif\" font-size=\"94\" font-style=\"italic\" font-weight=\"900\" letter-spacing=\"3\">TECHNOLOGY</text>",
                "</svg>",
                ""
        );
    }

    private String buildDicomServerBrandCssContent() {
        return String.join("\n",
                ".navbar-brand img,",
                ".sidebar-brand img,",
                "img[src*=\"custom-logo\"] {",
                "  width: 178px !important;",
                "  max-width: 178px !important;",
                "  max-height: 72px !important;",
                "  object-fit: contain !important;",
                "}",
                "",
                ".navbar-brand,",
                ".sidebar-brand {",
                "  min-height: 86px;",
                "}",
                "",
                ".powered-by-orthanc {",
                "  color: #ffffff !important;",
                "  font-size: 0 !important;",
                "  line-height: 1.2 !important;",
                "  margin: 6px 0 10px !important;",
                "  text-align: center !important;",
                "}",
                "",
                ".powered-by-orthanc img {",
                "  display: none !important;",
                "}",
                "",
                ".powered-by-orthanc::after {",
                "  content: \"UDAYA Technology\";",
                "  color: #ffffff;",
                "  font-size: 14px;",
                "  font-weight: 700;",
                "}",
                ""
        );
    }

    private String buildDicomServerDeployScriptContent() {
        return String.join("\n",
                "#!/usr/bin/env bash",
                "set -euo pipefail",
                "",
                "SCRIPT_DIR=\"$(cd \"$(dirname \"${BASH_SOURCE[0]}\")\" && pwd)\"",
                "PROJECT_ROOT=\"$(cd \"${SCRIPT_DIR}/..\" && pwd)\"",
                "cd \"${PROJECT_ROOT}\"",
                "",
                "BASE_IMAGE=\"${UDAYA_DICOM_SERVER_BASE_IMAGE:-" + DICOM_SERVER_BASE_DOCKER_IMAGE + "}\"",
                "UPSTREAM_IMAGE=\"${UDAYA_DICOM_SERVER_UPSTREAM_IMAGE:-" + DICOM_SERVER_UPSTREAM_DOCKER_IMAGE + "}\"",
                "",
                "echo \"Preparing UDAYA_DICOM_SERVER base image: ${BASE_IMAGE}\"",
                "if ! docker image inspect \"${BASE_IMAGE}\" >/dev/null 2>&1; then",
                "  docker pull \"${UPSTREAM_IMAGE}\"",
                "  docker tag \"${UPSTREAM_IMAGE}\" \"${BASE_IMAGE}\"",
                "  docker image rm \"${UPSTREAM_IMAGE}\" >/dev/null 2>&1 || true",
                "fi",
                "",
                "docker compose up -d --build --force-recreate",
                "docker compose ps",
                ""
        );
    }

    private String buildDicomServerHealthcheckCommand(Map<String, Object> config) {
        String fallback = "python3 -c \\\"import urllib.request; urllib.request.urlopen('http://localhost:8042/system', timeout=5).read()\\\" >/dev/null || exit 1";
        if (config == null || !Boolean.TRUE.equals(config.get("AuthenticationEnabled"))) {
            return fallback;
        }
        Object usersObject = config.get("RegisteredUsers");
        if (!(usersObject instanceof Map<?, ?> users) || users.isEmpty()) {
            return fallback;
        }
        Map.Entry<?, ?> firstUser = users.entrySet().iterator().next();
        String username = trimToNull(firstUser.getKey() == null ? null : String.valueOf(firstUser.getKey()));
        String password = trimToNull(firstUser.getValue() == null ? null : String.valueOf(firstUser.getValue()));
        if (username == null || password == null) {
            return fallback;
        }
        return "python3 -c \\\"import base64,json,urllib.request; c=json.load(open('" + DICOM_SERVER_CONFIG_CONTAINER_PATH + "')); users=c.get('RegisteredUsers') or {}; u=next(iter(users)); p=users[u]; req=urllib.request.Request('http://localhost:8042/system'); req.add_header('Authorization','Basic '+base64.b64encode((str(u)+':'+str(p)).encode()).decode()); urllib.request.urlopen(req, timeout=5).read()\\\" >/dev/null || exit 1";
    }

    private String buildDicomServerProjectReadme(DicomServerConfigBuildResponse response) {
        return String.join("\n",
                "# " + response.getProjectName(),
                "",
                "Ready-to-run UDAYA_DICOM_SERVER archive deployment generated from PACS DICOM Routing.",
                "",
                "- Hospital: " + defaultString(response.getHospitalName(), "-"),
                "- DICOM Server: " + defaultString(response.getDicomServerName(), "-") + " (#" + response.getDicomServerId() + ")",
                "- Callback client: " + response.getCallbackClientId(),
                "",
                "## Start",
                "",
                "```bash",
                "sudo sed -i 's/\\r$//' .env Dockerfile docker-compose.yml ./scripts/*.sh",
                "sudo bash ./scripts/deploy.sh",
                "```",
                "",
                "The deploy script prepares the local shared base image `dicom_server_base:latest`, then builds and starts this server-specific image.",
                "If the base already exists, it will reuse it and deploy immediately.",
                "",
                "After startup, configure DICOM Server Management with the real archive server URL:",
                "`http://<archive-server-ip>:" + defaultPositiveInteger(response.getConfig() == null ? null : (Integer) response.getConfig().get("HttpPort"), DICOM_SERVER_CONTAINER_HTTP_PORT, "HTTP port") + "` or `http://localhost:${UDAYA_DICOM_SERVER_HTTP_PORT}` when API and the archive run on the same host.",
                "",
                "UDAYA_DICOM_SERVER archive login uses the private credentials in `.env`: `UDAYA_DICOM_SERVER_HTTP_USERNAME` and `UDAYA_DICOM_SERVER_HTTP_PASSWORD`.",
                "Direct viewer DICOMweb access is protected by the archive authorization plugin. The browser receives only a short-lived viewer token; it never receives archive HTTP credentials.",
                "The UDAYA_PACS_API still uses the configured DICOM Server host, port, and credentials for server-side worklist/callback checks when that archive endpoint requires Basic Auth.",
                "The private `.env` file in this folder is generated for this exact DICOM Server.",
                "Keep this zip and `.env` private. Building config again rotates the callback secret and keeps the archive HTTP credentials synchronized.",
                "",
                "## Files",
                "",
                "- `config/dicom_server.json`: UDAYA_DICOM_SERVER archive configuration.",
                "- `brand/`: UDAYA Explorer logo and UI brand overrides.",
                "- `scripts/deploy.sh`: prepares the base image and runs Docker Compose.",
                "- `scripts/notify-emr.lua`: stable-study callback script.",
                "- `Dockerfile`: server-specific deploy image built from the shared DICOM server base image.",
                "- `.env`: generated ports and callback credentials.",
                ""
        );
    }

    private String buildDicomServerProjectName(
            HospitalDicomRoutingConfigResponse routeConfig,
            HospitalModalityServerRouteResponse serverRoute,
            boolean includeServerName
    ) {
        String hospitalSlug = compactHospitalSlug(routeConfig == null ? null : routeConfig.getHospitalName());
        if (!includeServerName) {
            return DICOM_SERVER_PREFIX + "_" + hospitalSlug.replace('-', '_');
        }
        String serverText = firstNonBlank(
                serverRoute == null ? null : serverRoute.getDicomServerName(),
                "server-" + (serverRoute == null ? "" : serverRoute.getDicomServerId())
        ).replaceAll("(?i)\\b(?:udaya[\\s_-]*)?dicom[\\s_-]*server\\b", " ");
        String serverSlug = toSlug(serverText);
        if (DICOM_SERVER_PREFIX.equals(serverSlug) || LEGACY_DICOM_SERVER_PREFIX.equals(serverSlug) || hospitalSlug.equals(serverSlug)) {
            serverSlug = "server-" + (serverRoute == null ? "" : serverRoute.getDicomServerId());
        }
        if (serverSlug.startsWith(hospitalSlug)) {
            return DICOM_SERVER_PREFIX + "_" + serverSlug.replace('-', '_');
        }
        return (DICOM_SERVER_PREFIX + "_" + hospitalSlug + "_" + serverSlug).replace('-', '_');
    }

    private String compactHospitalSlug(String hospitalName) {
        String text = firstNonBlank(hospitalName, "hospital");
        String[] split = text.split("\\s+-\\s+|\\s+/\\s+");
        if (split.length > 0 && trimToNull(split[0]) != null) {
            text = split[0];
        }
        text = text.replaceAll("(?i)\\b(hospital|clinic|medical|center|centre)\\b", " ");
        String slug = toSlug(text);
        return trimToNull(slug) == null ? "hospital" : slug;
    }

    private String resolveCallbackApiBaseUrl(HospitalModalityServerRouteResponse serverRoute) {
        String resolved = trimToNull(serverRoute == null ? null : serverRoute.getPacsApiCallbackBaseUrl());
        if (resolved == null) {
            resolved = trimToNull(apiAuthUrl);
        }
        if (resolved == null) {
            resolved = DEFAULT_CALLBACK_API_BASE_URL;
        }
        return resolved.replaceAll("/+$", "");
    }

    private Integer resolveDicomServerHttpPublishPort(HospitalModalityServerRouteResponse serverRoute) {
        Integer publicPort = extractHttpUrlPort(firstNonBlank(
                serverRoute == null ? null : serverRoute.getDicomServerUiBaseUrl(),
                serverRoute == null ? null : serverRoute.getDicomwebBaseUrl()
        ));
        return defaultPositiveInteger(
                publicPort == null ? (serverRoute == null ? null : serverRoute.getPort()) : publicPort,
                DICOM_SERVER_CONTAINER_HTTP_PORT,
                "HTTP publish port"
        );
    }

    private Integer resolveDicomServerDicomPublishPort(HospitalModalityServerRouteResponse serverRoute) {
        return defaultPositiveInteger(
                serverRoute == null ? null : serverRoute.getDicomPort(),
                DEFAULT_DICOM_PORT,
                "DICOM publish port"
        );
    }

    private Integer extractHttpUrlPort(String rawUrl) {
        String value = trimToNull(rawUrl);
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value);
            if (uri.getPort() > 0) {
                return uri.getPort();
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return 443;
            }
            if ("http".equalsIgnoreCase(uri.getScheme())) {
                return 80;
            }
        } catch (URISyntaxException ex) {
            LOGGER.debug("URI parse failed resolving DICOM server port: {}", ex.getMessage());
            return null;
        }
        return null;
    }

    private String loadCallbackScript() {
        try {
            return new String(
                    new ClassPathResource(CALLBACK_SCRIPT_RESOURCE).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException error) {
            throw new IllegalStateException("UDAYA_DICOM_SERVER callback script template is unavailable.", error);
        }
    }

    private void ensureDicomServerHttpCredential(HospitalModalityServerRouteResponse serverRoute) {
        if (serverRoute == null || serverRoute.getDicomServerId() == null || serverRoute.getDicomServerId() <= 0L) {
            throw new IllegalArgumentException("DICOM server is required for UDAYA_DICOM_SERVER HTTP credentials.");
        }
        if (serverRoute.getHospitalId() == null || serverRoute.getHospitalId() <= 0L) {
            throw new IllegalArgumentException("Hospital is required for UDAYA_DICOM_SERVER HTTP credentials.");
        }

        String existingUsername = trimToNull(serverRoute.getUsername());
        String existingPassword = trimToNull(serverRoute.getPassword());
        String username = existingUsername == null || isLegacyDicomServerUsername(existingUsername)
                ? buildDefaultDicomServerUsername(serverRoute)
                : existingUsername;
        String password = existingPassword == null ? generateDicomServerHttpPassword() : existingPassword;

        boolean shouldPersist =
                existingUsername == null
                        || isLegacyDicomServerUsername(existingUsername)
                        || existingPassword == null
                        || !Boolean.TRUE.equals(serverRoute.getHttpServerEnabled())
                        || !Boolean.TRUE.equals(serverRoute.getRemoteAccessAllowed())
                        || !Boolean.TRUE.equals(serverRoute.getAuthenticationEnabled())
                        || !Boolean.TRUE.equals(serverRoute.getAuthorizationEnabled());

        if (shouldPersist) {
            Integer updated = dicomServerMapper.updateDicomServerHttpCredential(
                    serverRoute.getDicomServerId(),
                    serverRoute.getHospitalId(),
                    username,
                    password,
                    currentUserIdOrNull()
            );
            if (updated == null || updated <= 0) {
                throw new IllegalStateException("Unable to provision UDAYA_DICOM_SERVER HTTP credentials for DICOM Server #" + serverRoute.getDicomServerId() + ".");
            }
        }

        serverRoute.setUsername(username);
        serverRoute.setPassword(password);
        serverRoute.setHasPassword(true);
        serverRoute.setHttpServerEnabled(true);
        serverRoute.setRemoteAccessAllowed(true);
        serverRoute.setAuthenticationEnabled(true);
        serverRoute.setAuthorizationEnabled(true);
        serverRoute.setAuthorizationRoot(defaultString(serverRoute.getAuthorizationRoot(), DEFAULT_AUTHORIZATION_ROOT));
        serverRoute.setAuthorizationCheckedLevel(defaultString(serverRoute.getAuthorizationCheckedLevel(), DEFAULT_AUTHORIZATION_CHECKED_LEVEL));
    }

    private String buildDefaultDicomServerUsername(HospitalModalityServerRouteResponse serverRoute) {
        String hospitalSlug = compactHospitalSlug(serverRoute == null ? null : serverRoute.getHospitalName());
        String serverText = firstNonBlank(
                serverRoute == null ? null : serverRoute.getDicomServerName(),
                serverRoute == null ? null : serverRoute.getAeTitle(),
                serverRoute == null || serverRoute.getDicomServerId() == null ? null : "server-" + serverRoute.getDicomServerId(),
                hospitalSlug
        );
        String serverSlug = stripArchiveNamePrefix(toSlug(serverText));
        if (!hasText(serverSlug) || "server".equals(serverSlug)) {
            serverSlug = hospitalSlug;
        }
        String resolved = serverSlug.startsWith(hospitalSlug)
                ? serverSlug
                : hospitalSlug + "-" + serverSlug;
        return (DICOM_SERVER_PREFIX + "_" + resolved).replace('-', '_').replaceAll("_+", "_");
    }

    private static boolean isLegacyDicomServerUsername(String username) {
        String value = trimToNull(username);
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return LEGACY_DICOM_SERVER_PREFIX.equals(normalized)
                || normalized.startsWith(LEGACY_DICOM_SERVER_PREFIX + "_")
                || DICOM_SERVER_PREFIX.equals(normalized)
                || normalized.startsWith("udaya" + "-dicom-server-");
    }

    private String resolveArchiveAeTitle(HospitalModalityServerRouteResponse serverRoute) {
        String aeTitle = trimToNull(serverRoute == null ? null : serverRoute.getAeTitle());
        if (aeTitle == null || "UDAYA_DICOM_SERVER".equalsIgnoreCase(aeTitle)) {
            return DEFAULT_DICOM_AE_TITLE;
        }
        String normalized = aeTitle.replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
        return normalized.length() <= 16 ? normalized : normalized.substring(0, 16);
    }

    private Long currentUserIdOrNull() {
        try {
            var userAuth = userService == null ? null : userService.getUserAuth();
            return userAuth == null ? null : userAuth.getId();
        } catch (Exception ex) {
            LOGGER.debug("Could not resolve current user ID: {}", ex.getMessage());
            return null;
        }
    }

    private static String generateCallbackClientSecret() {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String generateDicomServerHttpPassword() {
        byte[] randomBytes = new byte[36];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String generatePacsResultApiKey() {
        byte[] randomBytes = new byte[PACS_RESULT_API_KEY_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return "pacs_result_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private Map<String, String> buildRegisteredUsersForConfig(HospitalModalityServerRouteResponse serverRoute) {
        Map<String, String> registeredUsers = new LinkedHashMap<>();
        String username = trimToNull(serverRoute == null ? null : serverRoute.getUsername());
        String password = trimToNull(serverRoute == null ? null : serverRoute.getPassword());
        if (username == null || password == null) {
            throw new IllegalStateException("UDAYA_DICOM_SERVER HTTP username and password are required before building deployment config.");
        }
        registeredUsers.put(username, password);
        return registeredUsers;
    }

    private void redactDicomServerBuildResponse(DicomServerConfigBuildResponse response) {
        if (response == null) {
            return;
        }
        response.setConfig(redactDicomServerConfigForResponse(response.getConfig()));
        response.setEnvironmentContent(null);
        response.setCallbackScriptContent(null);
        response.setSetupContent(null);
    }

    private Map<String, Object> redactDicomServerConfigForResponse(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Map<String, Object> redacted = OBJECT_MAPPER.convertValue(config, new TypeReference<LinkedHashMap<String, Object>>() {});
        Object usersObject = redacted.get("RegisteredUsers");
        if (usersObject instanceof Map<?, ?> users) {
            Map<String, String> maskedUsers = new LinkedHashMap<>();
            for (Object key : users.keySet()) {
                if (key != null) {
                    maskedUsers.put(String.valueOf(key), "********");
                }
            }
            redacted.put("RegisteredUsers", maskedUsers);
        }
        return redacted;
    }

    private Map<String, Object> parseJsonObjectForConfig(String json) {
        String trimmed = trimToNull(json);
        if (trimmed == null) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(trimmed, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            LOGGER.debug("JSON parse failed for DICOM config map, returning empty: {}", ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Map<String, List<Object>> buildDicomModalitiesForConfig(List<HospitalModalityServerRouteResponse> routes) {
        Map<String, List<Object>> modalities = new LinkedHashMap<>();
        if (routes == null || routes.isEmpty()) {
            return modalities;
        }
        for (HospitalModalityServerRouteResponse route : routes) {
            if (route == null) {
                continue;
            }
            String key = buildModalityKey(route);
            if (modalities.containsKey(key)) {
                key = key + "_" + route.getId();
            }
            modalities.put(key, List.of(
                    defaultString(route.getMachineAeTitle(), "AE_TITLE").toUpperCase(Locale.ROOT),
                    defaultString(route.getMachineHost(), "DICOM_HOST"),
                    defaultPositiveInteger(route.getMachinePort(), DEFAULT_DICOM_PORT, "Machine DICOM port")
            ));
        }
        return modalities;
    }

    private List<String> splitConfigPaths(String value, String fallback) {
        String source = normalizeConfigPathDelimiters(defaultString(value, fallback));
        return Arrays.stream(source.split("[\\r\\n,;]+"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private String normalizeConfigPathDelimiters(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value
                .replace("/usr/share/dicom_server/plugins/usr/local/share/dicom_server/plugins",
                        "/usr/share/dicom_server/plugins\n/usr/local/share/dicom_server/plugins")
                .replace("/usr/share/udaya_dicom_server/plugins/usr/local/share/udaya_dicom_server/plugins",
                        "/usr/share/dicom_server/plugins\n/usr/local/share/dicom_server/plugins")
                .replace("\\usr\\share\\dicom_server\\plugins\\usr\\local\\share\\dicom_server\\plugins",
                        "\\usr\\share\\dicom_server\\plugins\n\\usr\\local\\share\\dicom_server\\plugins")
                .replace("\\usr\\share\\udaya_dicom_server\\plugins\\usr\\local\\share\\udaya_dicom_server\\plugins",
                        "\\usr\\share\\dicom_server\\plugins\n\\usr\\local\\share\\dicom_server\\plugins");
    }

    private String buildDicomServerConfigFileName(
            HospitalDicomRoutingConfigResponse routeConfig,
            HospitalModalityServerRouteResponse serverRoute
    ) {
        String hospitalPart = toSlug(firstNonBlank(
                routeConfig == null ? null : routeConfig.getHospitalName(),
                routeConfig == null || routeConfig.getHospitalId() == null ? null : "hospital-" + routeConfig.getHospitalId()
        ));
        String serverPart = stripArchiveNamePrefix(toSlug(firstNonBlank(
                serverRoute == null ? null : serverRoute.getDicomServerName(),
                serverRoute == null || serverRoute.getDicomServerId() == null ? null : "dicom-server-" + serverRoute.getDicomServerId()
        )));
        if (!hasText(serverPart)) {
            serverPart = serverRoute == null || serverRoute.getDicomServerId() == null
                    ? "server"
                    : "server_" + serverRoute.getDicomServerId();
        }
        String fileName = DICOM_SERVER_PREFIX + "_" + hospitalPart + "_" + serverPart + ".json";
        return fileName.replace('-', '_').replaceAll("_+", "_");
    }

    private String buildDicomServerName(HospitalModalityServerRouteResponse serverRoute) {
        return buildDefaultDicomServerUsername(serverRoute);
    }

    private String stripArchiveNamePrefix(String slug) {
        String value = trimToNull(slug);
        if (value == null) {
            return "";
        }
        return value
                .replaceFirst("(?i)^udaya_dicom_server_?", "")
                .replaceFirst("(?i)^udaya" + "-dicom-server-?", "")
                .replaceFirst("(?i)^dicom_server_?", "")
                .replaceFirst("(?i)^dicom-server-?", "");
    }

    private String buildModalityKey(HospitalModalityServerRouteResponse route) {
        String key = firstNonBlank(
                route.getMachineAeTitle(),
                route.getModalityAbbr(),
                route.getModalityName(),
                route.getModalityId() == null ? null : "MODALITY_" + route.getModalityId()
        );
        String normalized = key == null ? "" : key
                .trim()
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? "MODALITY" : normalized;
    }

    private record DicomServerCallbackCredential(String clientId, String clientSecret) {
    }

    private String toSlug(String value) {
        String normalized = defaultString(value, DICOM_SERVER_PREFIX)
                .trim()
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-")
                .toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? DICOM_SERVER_PREFIX : normalized;
    }

    private static String normalizeMachineAeTitle(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String normalizeMachineName(String machineName, String aeTitle) {
        return firstNonBlank(machineName, aeTitle, "Machine");
    }

    private static String normalizeMachineHost(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String host = stripUrlScheme(trimmed);
        assertSplitServerHostAllowed(host, "Machine host/IP");
        return host;
    }

    private static Integer normalizeMachinePort(Integer value) {
        return value == null ? null : value;
    }

    private List<Integer> parseIntegerList(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(java.util.regex.Pattern.quote(MULTI_VALUE_DELIMITER)))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(value -> value != null && value > 0)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> parseLongCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(value -> value != null && value > 0)
                .collect(Collectors.toList());
    }

    private List<String> parseStringCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> parseStringList(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(java.util.regex.Pattern.quote(MULTI_VALUE_DELIMITER)))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}
