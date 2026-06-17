package com.ut.emrPacs.service.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import com.ut.emrPacs.authentication.util.JwtTokenService;
import com.ut.emrPacs.authentication.util.PublicViewerAttemptGuard;
import com.ut.emrPacs.authentication.util.ViewerAccessKeyService;
import com.ut.emrPacs.authentication.util.ViewerAccessKeyService.ViewerAccessClaims;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.config.DicomTagConstants;
import com.ut.emrPacs.config.WorklistConstants;
import com.ut.emrPacs.helper.FunctionCodeGenerate;
import com.ut.emrPacs.helper.FunctionHelper;
import static com.ut.emrPacs.helper.FunctionHelper.firstNonBlank;
import static com.ut.emrPacs.helper.FunctionHelper.hasText;
import static com.ut.emrPacs.helper.dicomServer.DicomResponseReadHelper.firstDicomServerSeriesId;
import static com.ut.emrPacs.helper.dicomServer.DicomResponseReadHelper.normalizedOrEmpty;
import static com.ut.emrPacs.helper.dicomServer.DicomResponseReadHelper.parseDicomStudyDate;
import static com.ut.emrPacs.helper.dicomServer.DicomResponseReadHelper.putIfHasText;
import static com.ut.emrPacs.helper.dicomServer.DicomResponseReadHelper.readDicomServerInstanceCount;
import static com.ut.emrPacs.helper.dicomServer.DicomResponseReadHelper.readDicomTag;
import com.ut.emrPacs.helper.http.RequestClientInfoHelper;
import com.ut.emrPacs.helper.dicomServer.DicomServerWorklistMapperHelper;
import com.ut.emrPacs.helper.dicomServer.DicomServerWorklistMapperHelper.SyncedWorklistFields;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.pacs.DicomServerCallbackLogMapper;
import com.ut.emrPacs.mapper.pacs.PatientMapper;
import com.ut.emrPacs.mapper.pacs.PacsResultMapper;
import com.ut.emrPacs.mapper.pacs.WorklistMapper;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.mapper.auth.RevokedTokenMapper;
import com.ut.emrPacs.mapper.hospital.HospitalMapper;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.WorklistFilter;
import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerFindRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerWorklistCreateRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerSeriesResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistActionRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistAssignRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistReceivedStudyRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistRouteAvailabilityRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistRoutedModalityListRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistSendToPacsRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistUpdateRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistViewStudyRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistDicomWorklistUpdateRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.PublicViewerAuthorizeRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultFindByWorklistRequest;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsViewerStateRequest;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistActionResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistItemRefResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistListResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistMachineRouteChoiceResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistRouteAvailabilityResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistRouteServerOptionResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistViewerInstanceResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistViewerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDicomWorklistResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.ViewerInfoResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultResponse;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsViewerStateResponse;
import com.ut.emrPacs.model.dto.response.pacs.patient.PatientResponse;
import com.ut.emrPacs.model.dto.response.authentication.token.AccessTokenResponse;
import com.ut.emrPacs.model.dto.response.dropDown.DropDownModelResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalModalityServerRouteResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistResponse;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.modality.ModalityResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import com.ut.emrPacs.model.enums.WorklistStatus;
import com.ut.emrPacs.model.enums.StudyStatus;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import com.ut.emrPacs.service.service.RealtimeNotificationService;
import com.ut.emrPacs.service.service.WorklistService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import static com.ut.emrPacs.authentication.util.AuthorityUtils.isAdminUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.stream.Collectors;

@Service
public class WorklistServiceImpl implements WorklistService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorklistServiceImpl.class);
    private static final int MAX_VIEWER_PREVIEW_INSTANCES = 48;
    private static final String DEFAULT_DICOM_AE_TITLE = "UDAYA";
    private static final String HTTP_METHOD_GET = "GET";
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final String PARAM_TOKEN = "token";
    /** JSON fields of the UDAYA_DICOM_SERVER auth-callback contract. */
    private static final String AUTH_FIELD_TOKEN_VALUE_KEBAB = "token-value";
    private static final String AUTH_FIELD_TOKEN_VALUE = "tokenValue";
    private static final String AUTH_FIELD_AUTHORIZATION_LOWER = "authorization";
    private static final String AUTH_FIELD_AUTHORIZATION = "Authorization";
    private static final String AUTH_FIELD_VALIDITY = "validity";
    private static final String AUTH_FIELD_GRANTED = "success";
    private static final int AUTH_VALIDITY_SECONDS = 60;
    private static final String DEFAULT_DICOMWEB_PATH = "/dicom-web";
    private static final String VIEWER_DICOMWEB_PROXY_PATH = "/pacs-dicomweb";
    private static final String VIEWER_DICOMWEB_SCOPE = "pacs.viewer.dicomweb";
    private static final String VIEWER_DICOMWEB_CLIENT_ID = "pacs-viewer-dicomweb";
    private static final String DEFAULT_VIEWER_STATE_TYPE = "OHIF_VIEWER_STATE";
    private static final String RESULT_STATUS_COMPLETED = "COMPLETED";

    @Autowired
    private WorklistMapper WorklistMapper;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private PatientMapper patientMapper;
    @Autowired
    private PacsResultMapper pacsResultMapper;
    @Autowired
    private DicomServerMapper dicomServerMapper;
    @Autowired
    private StudyMapper studyMapper;
    @Autowired
    private ModalityMapper modalityMapper;
    @Autowired
    private HospitalMapper hospitalMapper;
    @Autowired
    private DicomServerClientService dicomServerClientService;
    @Autowired
    private DicomServerCallbackLogMapper dicomServerCallbackLogMapper;
    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private ViewerAccessKeyService viewerAccessKeyService;
    @Autowired(required = false)
    private RevokedTokenMapper revokedTokenMapper;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;
    @Autowired
    private PublicViewerAttemptGuard publicViewerAttemptGuard;
    @Autowired(required = false)
    private RealtimeNotificationService realtimeNotificationService;

    @Value("${pacs.viewer.dicomweb.token-ms:86400000}")
    private long viewerDicomwebTokenMs;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ResponseMessage<BaseResult> list(WorklistFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            WorklistFilter safeFilter = filter == null ? new WorklistFilter() : filter;
            safeFilter.applyOperationalWorklistDefaultStatuses();
            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, safeFilter.getHospitalKey(), null);
            Long hospitalId = resolveOptionalHospitalId(requestedHospitalId);
            boolean cursorMode = safeFilter.getLastWorklistId() != null && safeFilter.getLastWorklistId() > 0L;

            Pagination pagination;
            if (cursorMode) {
                // For deep pagination on very large tables, skip COUNT(*) and rely on cursor windowing.
                pagination = PaginationHelper.buildAndApplyOffsetOrDefault(safeFilter);
            } else {
                pagination = PaginationHelper.buildAndApplyOffset(safeFilter, WorklistMapper.countList(hospitalId, safeFilter));
            }
            List<WorklistListResponse> WorklistList = WorklistMapper.list(hospitalId, safeFilter);

            if (WorklistList != null && !WorklistList.isEmpty()) {
                for (WorklistListResponse Worklist : WorklistList) {
                    if (Worklist == null) continue;
WorklistItemRefResponse modality = new WorklistItemRefResponse();
                    modality.setId(Worklist.getModalityId());
                    modality.setPublicKey(Worklist.getModalityPublicKey());
                    modality.setName(Worklist.getModalityName());
                    Worklist.setModality(modality);

                }
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.LIST_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (List)", WorklistConstants.ACTION_VIEW, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, WorklistList, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.LIST_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (List)", WorklistConstants.ACTION_VIEW, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> assignWorklist(WorklistAssignRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request != null) {
                request.setPatientId(publicEntityKeyResolver.resolve(Entity.PATIENT, request.getPatientKey(), request.getPatientId()));
                request.setModalityId(publicEntityKeyResolver.resolve(Entity.MODALITY, request.getModalityKey(), request.getModalityId()));
                request.setDicomServerId(publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, request.getDicomServerKey(), request.getDicomServerId()));
            }
            if (request == null || request.getPatientId() == null || request.getModalityId() == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("patientId and modalityId are required.", false));
            }

            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), request.getHospitalId());
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            Long userId = currentUserId();

            if (patientMapper.findById(hospitalId, request.getPatientId()) == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Patient not found or inactive.", false));
            }

            Long activeModality = modalityMapper.countActiveModalitiesByIds(List.of(request.getModalityId()));
            if (activeModality == null || activeModality <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Modality not found or inactive.", false));
            }

            request.setStudyDescription(resolveStudyDescription(request.getStudyDescription(), null, request.getModalityId()));
            request.setScheduledDate(resolveScheduledDate(request.getScheduledDate(), null));
            request.setScheduledTime(resolveScheduledTime(request.getScheduledTime(), null));

            validateWorklistModalityForHospital(hospitalId, request.getModalityId());
            List<HospitalModalityServerRouteResponse> routingServers =
                    dicomServerMapper.listActiveRoutesByHospitalAndModality(hospitalId, request.getModalityId());
            Long resolvedDicomServerId = resolveDicomServerIdForWorklistAssign(request.getDicomServerId(), routingServers);
            if (request.getDicomServerId() != null && resolvedDicomServerId == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Selected Dicom server is not routed to this modality.", false));
            }
            request.setDicomServerId(resolvedDicomServerId);

            Long duplicateWorklist = WorklistMapper.countPatientModalityActiveWorklist(hospitalId, request.getPatientId(), request.getModalityId());
            if (duplicateWorklist != null && duplicateWorklist > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Patient already has an active Worklist for this modality.", false));
            }

            String visitCode = null;
            Boolean inserted = false;
            for (int attempt = 0; attempt < 10; attempt++) {
                visitCode = generateVisitCode(hospitalId, request.getModalityId());
                if (WorklistMapper.findWorklistByVisitCodeAnyHospital(visitCode) != null) {
                    continue;
                }
                try {
                    inserted = WorklistMapper.assignWorklist(hospitalId, userId, visitCode, request);
                    if (Boolean.TRUE.equals(inserted)) {
                        break;
                    }
                } catch (DuplicateKeyException duplicate) {
                    LOGGER.warn("visit_code collision detected for hospitalId={} visitCode={}, retrying", hospitalId, visitCode);
                }
            }
            if (!Boolean.TRUE.equals(inserted)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to assign patient to Worklist.", false));
            }

            WorklistDetailRow Worklist = WorklistMapper.findWorklistByVisitCode(hospitalId, visitCode);
            if (Worklist != null) {
                WorklistMapper.insertHistory(hospitalId, Worklist.getId(), Worklist.getPatientId(), WorklistStatus.WAITING.code(), WorklistStatus.WAITING.code(), WorklistConstants.ACTION_ASSIGN, request.getNotes(), userId);
            }

            WorklistActionResponse response = new WorklistActionResponse();
            if (Worklist != null) {
                response.setWorklistId(Worklist.getId());
                response.setPublicKey(Worklist.getPublicKey());
                response.setVisitCode(Worklist.getVisitCode());
                response.setStudyUuid(Worklist.getStudyUuid());
                response.setStatus(Worklist.getStatus());
            } else {
                response.setVisitCode(visitCode);
                response.setStatus(WorklistStatus.WAITING.name());
            }
            response.setMessage("Patient assigned to Worklist successfully.");

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.ASSIGN_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (Assign)", WorklistConstants.ACTION_ASSIGN, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, List.of(response), true));
        } catch (Exception error) {
            LOGGER.error("Worklist-assign failed: {}", error.toString(), error);
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.ASSIGN_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (Assign)", WorklistConstants.ACTION_ASSIGN, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> findWorklistById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return findWorklistById(id, null, httpServletRequest);
    }

    @Override
    public ResponseMessage<BaseResult> findWorklistById(Long id, Long requestedHospitalId, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0L) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_ID_REQUIRED, false));
            }

            Long hospitalId = resolveHospitalId(requestedHospitalId);
            WorklistDetailRow Worklist = WorklistMapper.findWorklistById(hospitalId, id);
            if (Worklist == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_WORKLIST_NOT_FOUND, false));
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.FIND_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (Find)", WorklistConstants.ACTION_FIND, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, List.of(Worklist), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.FIND_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (Find)", WorklistConstants.ACTION_FIND, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> listRoutedModalities(WorklistRoutedModalityListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long requestedHospitalId = request == null
                    ? null
                    : publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            List<DropDownModelResponse> modalities = dicomServerMapper.listActiveRoutedModalityOptionsByHospital(hospitalId);
            if (modalities == null) {
                modalities = Collections.emptyList();
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.ROUTED_MODALITY_LIST_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (Routed Modality List)", WorklistConstants.ACTION_VIEW, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, modalities, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.ROUTED_MODALITY_LIST_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (Routed Modality List)", WorklistConstants.ACTION_VIEW, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> checkRouteAvailability(WorklistRouteAvailabilityRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request != null) {
                request.setModalityId(publicEntityKeyResolver.resolve(Entity.MODALITY, request.getModalityKey(), null));
            }
            if (request == null || request.getModalityId() == null || request.getModalityId() <= 0L) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("modalityId is required.", false));
            }

            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            validateWorklistModalityForHospital(hospitalId, request.getModalityId());

            List<WorklistRouteServerOptionResponse> servers =
                    dicomServerMapper.listActiveRouteServerOptionsByHospitalAndModality(hospitalId, request.getModalityId());
            if (servers == null) {
                servers = Collections.emptyList();
            }

            long routeCount = servers.stream()
                    .map(WorklistRouteServerOptionResponse::getRouteCount)
                    .filter(value -> value != null && value > 0L)
                    .mapToLong(Long::longValue)
                    .sum();
            long machineCount = servers.stream()
                    .map(WorklistRouteServerOptionResponse::getMachineCount)
                    .filter(value -> value != null && value > 0L)
                    .mapToLong(Long::longValue)
                    .sum();

            WorklistRouteAvailabilityResponse response = new WorklistRouteAvailabilityResponse();
            response.setHospitalId(hospitalId);
            response.setModalityId(request.getModalityId());
            response.setHasActiveRouting(routeCount > 0L);
            response.setRouteCount(routeCount);
            response.setMachineCount(machineCount);
            response.setDicomServerCount((long) servers.size());
            response.setDicomServers(servers);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.ROUTE_AVAILABILITY_PATH, null, null, WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_ROUTE_AVAILABILITY, "Check", WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, List.of(response), true));
        } catch (IllegalArgumentException error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.ROUTE_AVAILABILITY_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_ROUTE_AVAILABILITY, "Check", WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(error.getMessage(), false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.ROUTE_AVAILABILITY_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_ROUTE_AVAILABILITY, "Check", WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> updateWorklist(Long id, WorklistUpdateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request != null) {
                request.setModalityId(publicEntityKeyResolver.resolve(Entity.MODALITY, request.getModalityKey(), request.getModalityId()));
                request.setDicomServerId(publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, request.getDicomServerKey(), request.getDicomServerId()));
            }
            if (id == null || id <= 0L || request == null || request.getModalityId() == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("id and modalityId are required.", false));
            }

            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), request.getHospitalId());
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            Long userId = currentUserId();
            WorklistDetailRow Worklist = WorklistMapper.findWorklistById(hospitalId, id);
            if (Worklist == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_WORKLIST_NOT_FOUND, false));
            }

            WorklistStatus currentStatus = WorklistStatus.fromValue(Worklist.getStatus());
            if (currentStatus == WorklistStatus.WAITING || currentStatus == WorklistStatus.FAILED) {
                validateWorklistModalityForHospital(hospitalId, request.getModalityId());
                List<HospitalModalityServerRouteResponse> routes =
                        dicomServerMapper.listActiveRoutesByHospitalAndModality(hospitalId, request.getModalityId());
                if (routes == null || routes.isEmpty()) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("No active DICOM machine route is configured for this hospital and modality.", false));
                }
                Long resolvedDicomServerId = resolveDicomServerIdForWorklistUpdate(Worklist.getDicomServerId(), request.getDicomServerId(), routes);
                if (request.getDicomServerId() != null && resolvedDicomServerId == null) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Selected DICOM server is not routed to this modality.", false));
                }

                String studyDescription = resolveStudyDescription(request.getStudyDescription(), Worklist, request.getModalityId());
                LocalDate scheduledDate = resolveScheduledDate(request.getScheduledDate(), Worklist.getScheduledDate());
                LocalTime scheduledTime = resolveScheduledTime(request.getScheduledTime(), Worklist.getScheduledTime());

                int updated = WorklistMapper.updateWorklistEditableFieldsById(
                        hospitalId,
                        id,
                        request,
                        resolvedDicomServerId,
                        studyDescription,
                        scheduledDate,
                        scheduledTime,
                        userId
                );
                if (updated <= 0) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to update Worklist.", false));
                }

                WorklistMapper.insertHistory(
                        hospitalId,
                        Worklist.getId(),
                        Worklist.getPatientId(),
                        currentStatus.code(),
                        currentStatus.code(),
                        WorklistConstants.ACTION_UPDATE,
                        request.getNotes(),
                        userId
                );

                WorklistDetailRow updatedWorklist = WorklistMapper.findWorklistById(hospitalId, id);
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.UPDATE_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (Update)", WorklistConstants.ACTION_UPDATE, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, updatedWorklist == null ? null : List.of(updatedWorklist), true));
            }

            if (currentStatus == WorklistStatus.IN_PROGRESS) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("This Worklist is read-only because scan process has already started.", false));
            }
            if (currentStatus == WorklistStatus.CANCELLED) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("This Worklist cannot be updated in the current status.", false));
            }
            return ResponseMessageUtils.makeResponse(false, messageService.message("Only WAITING or FAILED Worklist can be updated.", false));
        } catch (HttpClientErrorException.NotFound error) {
            markWorklistFailed(id, WorklistConstants.ACTION_UPDATE, "DicomServer worklist not found.");
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.UPDATE_PATH, WorklistConstants.ACTION_UPDATE, "DicomServer worklist not found.", error);
        } catch (HttpClientErrorException.Unauthorized error) {
            markWorklistFailed(id, WorklistConstants.ACTION_UPDATE, WorklistConstants.MSG_DICOM_SERVER_UNAUTHORIZED);
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.UPDATE_PATH, WorklistConstants.ACTION_UPDATE, WorklistConstants.MSG_DICOM_SERVER_UNAUTHORIZED, error);
        } catch (ResourceAccessException error) {
            markWorklistFailed(id, WorklistConstants.ACTION_UPDATE, WorklistConstants.MSG_DICOM_SERVER_UNREACHABLE);
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.UPDATE_PATH, WorklistConstants.ACTION_UPDATE, WorklistConstants.MSG_DICOM_SERVER_UNREACHABLE, error);
        } catch (RestClientException error) {
            markWorklistFailed(id, WorklistConstants.ACTION_UPDATE, "Failed to update DicomServer worklist.");
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.UPDATE_PATH, WorklistConstants.ACTION_UPDATE, "Failed to update DicomServer worklist.", error);
        } catch (IllegalArgumentException validationError) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.UPDATE_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (Update)", WorklistConstants.ACTION_UPDATE, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> sendToPacs(WorklistSendToPacsRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        WorklistDetailRow Worklist = null;
        Long actorId = null;
        try {
            Long worklistId = resolveWorklistId(request);
            String visitCode = request != null ? request.getVisitCode() : null;
            if ((worklistId == null || worklistId <= 0L) && (visitCode == null || visitCode.trim().isEmpty())) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("worklistId or visitCode is required.", false));
            }

            Long requestedHospitalId = request == null
                    ? null
                    : publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Worklist = findWorklistByIdentifier(resolveHospitalId(requestedHospitalId), worklistId, visitCode);
            if (Worklist == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_WORKLIST_NOT_FOUND, false));
            }

            WorklistStatus currentWorklistStatus = WorklistStatus.fromValue(Worklist.getStatus());
            if (currentWorklistStatus != WorklistStatus.WAITING && currentWorklistStatus != WorklistStatus.FAILED) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Only WAITING or FAILED Worklist can be sent to PACS. Current status: " + currentWorklistStatus.name(), false));
            }

            String accessionNumber = buildAccessionNumber(Worklist);
            if (!hasText(accessionNumber)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Missing accession number.", false));
            }

            String modalityCode = DicomServerWorklistMapperHelper.normalizeModality(firstNonBlank(Worklist.getModalityCode(), Worklist.getModalityName()));
            String studyDescription = resolveStudyDescription(Worklist.getStudyDescription(), Worklist, Worklist.getModalityId());
            LocalDate scheduledDate = resolveScheduledDate(Worklist.getScheduledDate(), null);
            LocalTime scheduledTime = resolveScheduledTime(Worklist.getScheduledTime(), null);
            List<HospitalModalityServerRouteResponse> routes =
                    dicomServerMapper.listActiveRoutesByHospitalAndModality(Worklist.getHospitalId(), Worklist.getModalityId());
            HospitalModalityServerRouteResponse selectedRoute = resolveRouteForSend(Worklist, request, routes);
            if (selectedRoute == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(machineRouteSelectionMessage(routes), false));
            }
            HospitalDicomServerResponse targetDicomServer = resolveTargetDicomServerByRoute(selectedRoute);
            if (targetDicomServer == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Selected machine route does not have an active DICOM server.", false));
            }
            String scheduledStationAeTitle = normalizeScheduledStationAeTitle(
                    firstNonBlank(selectedRoute.getMachineAeTitle(), Worklist.getMachineAeTitle(), targetDicomServer.getAeTitle(), DEFAULT_DICOM_AE_TITLE),
                    Worklist,
                    targetDicomServer
            );
            DicomServerWorklistCreateRequest worklistPayload = DicomServerWorklistMapperHelper.toCreateRequest(
                    Worklist,
                    accessionNumber,
                    modalityCode,
                    studyDescription,
                    scheduledDate,
                    scheduledTime,
                    scheduledStationAeTitle
            );
            var worklistResponse = postToDicomServerWorklist(worklistPayload, targetDicomServer);

            WorklistStatus fromStatus = WorklistStatus.fromValue(Worklist.getStatus());
            actorId = currentUserId();
            WorklistStatus toStatus = WorklistStatus.IN_PROGRESS;
            int updated = WorklistMapper.updateWorklistSentToPacsById(
                    Worklist.getHospitalId(),
                    Worklist.getId(),
                    toStatus.code(),
                    targetDicomServer.getId(),
                    selectedRoute.getId(),
                    accessionNumber,
                    modalityCode,
                    scheduledStationAeTitle,
                    studyDescription,
                    scheduledDate,
                    scheduledTime,
                    worklistResponse != null ? worklistResponse.getId() : null,
                    worklistResponse != null ? worklistResponse.getPath() : null,
                    actorId
            );
            if (updated <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_UNABLE_TO_UPDATE_STATUS, false));
            }

            WorklistMapper.insertHistory(
                    Worklist.getHospitalId(),
                    Worklist.getId(),
                    Worklist.getPatientId(),
                    fromStatus.code(),
                    toStatus.code(),
                    WorklistConstants.ACTION_SEND_WORKLIST,
                    "accessionNumber=" + accessionNumber,
                    actorId
            );

            Worklist.setStatus(toStatus.name());
            Worklist.setDicomServerId(targetDicomServer.getId());
            Worklist.setAccessionNumber(accessionNumber);
            Worklist.setModalityCode(modalityCode);
            Worklist.setMachineAeTitle(scheduledStationAeTitle);
            Worklist.setStudyDescription(studyDescription);
            Worklist.setScheduledDate(scheduledDate);
            Worklist.setScheduledTime(scheduledTime);
            Worklist.setDicomServerWorklistId(worklistResponse != null ? worklistResponse.getId() : null);
            Worklist.setDicomServerWorklistPath(worklistResponse != null ? worklistResponse.getPath() : null);
            WorklistDicomWorklistResponse actionResponse = DicomServerWorklistMapperHelper.toWorklistDicomWorklistResponse(
                    Worklist,
                    null,
                    "Worklist sent to DicomServer worklist and moved to IN_PROGRESS successfully."
            );

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.SEND_TO_PACS_PATH, null, null, WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_SEND_TO_DICOM_SERVER, WorklistConstants.ACTION_SEND_WORKLIST, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Worklist sent to PACS successfully.", List.of(actionResponse), true));
        } catch (HttpClientErrorException.Unauthorized error) {
            markWorklistFailedAfterSendAttempt(Worklist, actorId, WorklistConstants.MSG_DICOM_SERVER_UNAUTHORIZED);
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.SEND_TO_PACS_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_SEND_TO_DICOM_SERVER, WorklistConstants.ACTION_SEND_WORKLIST, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_DICOM_SERVER_UNAUTHORIZED, false));
        } catch (HttpClientErrorException.NotFound error) {
            markWorklistFailedAfterSendAttempt(Worklist, actorId, "DicomServer worklists/create not found (404).");
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.SEND_TO_PACS_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_SEND_TO_DICOM_SERVER, WorklistConstants.ACTION_SEND_WORKLIST, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("DicomServer worklists/create not found (404). Worklists plugin may be disabled.", false));
        } catch (ResourceAccessException error) {
            markWorklistFailedAfterSendAttempt(Worklist, actorId, WorklistConstants.MSG_DICOM_SERVER_UNREACHABLE);
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.SEND_TO_PACS_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_SEND_TO_DICOM_SERVER, WorklistConstants.ACTION_SEND_WORKLIST, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_DICOM_SERVER_UNREACHABLE, false));
        } catch (RestClientException error) {
            markWorklistFailedAfterSendAttempt(Worklist, actorId, "Failed to send Worklist to DicomServer.");
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.SEND_TO_PACS_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_SEND_TO_DICOM_SERVER, WorklistConstants.ACTION_SEND_WORKLIST, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("Failed to send Worklist to DicomServer.", false));
        } catch (Exception error) {
            markWorklistFailedAfterSendAttempt(Worklist, actorId, "An unexpected error occurred while sending to PACS.");
            LOGGER.error("Worklist-send-to-dicom_server failed: {}", error.toString(), error);
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.SEND_TO_PACS_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_SEND_TO_DICOM_SERVER, WorklistConstants.ACTION_SEND_WORKLIST, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> listMachineRoutesForSend(WorklistSendToPacsRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long worklistId = resolveWorklistId(request);
            String visitCode = request != null ? request.getVisitCode() : null;
            Long requestedHospitalId = request == null
                    ? null
                    : publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long hospitalId = resolveHospitalId(requestedHospitalId);

            List<WorklistMachineRouteChoiceResponse> routes;
            if ((worklistId != null && worklistId > 0L) || (visitCode != null && !visitCode.trim().isEmpty())) {
                WorklistDetailRow Worklist = findWorklistByIdentifier(hospitalId, worklistId, visitCode);
                if (Worklist == null) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_WORKLIST_NOT_FOUND, false));
                }

                WorklistStatus currentWorklistStatus = WorklistStatus.fromValue(Worklist.getStatus());
                if (currentWorklistStatus != WorklistStatus.WAITING && currentWorklistStatus != WorklistStatus.FAILED) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Machine selection is available only for WAITING or FAILED Worklists.", false));
                }

                routes = dicomServerMapper.listActiveRouteChoicesByHospitalAndModality(Worklist.getHospitalId(), Worklist.getModalityId());
            } else {
                Long modalityId = request == null
                        ? null
                        : publicEntityKeyResolver.resolve(Entity.MODALITY, request.getModalityKey(), null);
                if (modalityId == null || modalityId <= 0L) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("worklistId, visitCode, or modalityId is required.", false));
                }
                validateWorklistModalityForHospital(hospitalId, modalityId);
                routes = dicomServerMapper.listActiveRouteChoicesByHospitalAndModality(hospitalId, modalityId);
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.MACHINE_ROUTES_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (Machine Routes)", WorklistConstants.ACTION_VIEW, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, routes == null ? List.of() : routes, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.MACHINE_ROUTES_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (Machine Routes)", WorklistConstants.ACTION_VIEW, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> syncWorklistResult(WorklistActionRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_ID_REQUIRED, false));
            }
            request.setId(publicEntityKeyResolver.resolve(Entity.WORKLIST, request.getPublicKey(), null));
            if (request.getId() == null || request.getId() <= 0L) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_ID_REQUIRED, false));
            }

            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            Long userId = currentUserId();
            WorklistDetailRow Worklist = WorklistMapper.findWorklistById(hospitalId, request.getId());
            if (Worklist == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_WORKLIST_NOT_FOUND, false));
            }

            WorklistDetailRow syncedWorklist = syncWorklistStudyResultIfAvailable(Worklist, userId);
            String message = hasText(syncedWorklist.getStudyInstanceUid()) || hasText(syncedWorklist.getDicomServerStudyId())
                    ? "Image synced from DicomServer successfully and moved to Study Archive."
                    : "Image not received yet.";

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.SYNC_RESULT_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (Sync Result)", WorklistConstants.ACTION_SYNC_PACS_RESULT, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(message, List.of(syncedWorklist), true));
        } catch (HttpClientErrorException.Unauthorized error) {
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.SYNC_RESULT_PATH, WorklistConstants.ACTION_SYNC_PACS_RESULT, WorklistConstants.MSG_DICOM_SERVER_UNAUTHORIZED, error);
        } catch (HttpClientErrorException.NotFound error) {
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.SYNC_RESULT_PATH, WorklistConstants.ACTION_SYNC_PACS_RESULT, "DicomServer study endpoint not found.", error);
        } catch (ResourceAccessException error) {
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.SYNC_RESULT_PATH, WorklistConstants.ACTION_SYNC_PACS_RESULT, WorklistConstants.MSG_DICOM_SERVER_UNREACHABLE, error);
        } catch (RestClientException error) {
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.SYNC_RESULT_PATH, WorklistConstants.ACTION_SYNC_PACS_RESULT, "Failed to sync image result from DicomServer.", error);
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.SYNC_RESULT_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (Sync Result)", WorklistConstants.ACTION_SYNC_PACS_RESULT, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getWorklist(Long worklistId, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return findWorklistById(worklistId, httpServletRequest);
    }

    @Override
    public ResponseMessage<BaseResult> updateWorklist(Long worklistId, WorklistDicomWorklistUpdateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        WorklistUpdateRequest mappedRequest = new WorklistUpdateRequest();
        if (request != null) {
            mappedRequest.setModalityKey(request.getModalityKey());
            mappedRequest.setStudyDescription(request.getStudyDescription());
            mappedRequest.setScheduledDate(request.getScheduledDate());
            mappedRequest.setScheduledTime(request.getScheduledTime());
        }
        return updateWorklist(worklistId, mappedRequest, httpServletRequest);
    }

    @Override
    public ResponseMessage<BaseResult> deleteWorklist(Long worklistId, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (worklistId == null || worklistId <= 0L) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("worklistId is required.", false));
            }

            Long hospitalId = currentHospitalId();
            Long userId = currentUserId();
            WorklistDetailRow Worklist = WorklistMapper.findWorklistById(hospitalId, worklistId);
            if (Worklist == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_WORKLIST_NOT_FOUND, false));
            }
            if (!hasText(Worklist.getDicomServerWorklistId())) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Worklist has no DicomServer worklist ID.", false));
            }

            WorklistStatus currentStatus = WorklistStatus.fromValue(Worklist.getStatus());
            if (currentStatus == WorklistStatus.CANCELLED) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Cancelled Worklist cannot delete DicomServer worklist.", false));
            }

            HospitalDicomServerResponse targetDicomServer = resolveTargetDicomServer(Worklist, userId);
            deleteDicomServerWorklist(Worklist.getDicomServerWorklistId(), targetDicomServer);
            int updated = WorklistMapper.updateWorklistStatusById(hospitalId, worklistId, WorklistStatus.CANCELLED.code(), userId);
            if (updated <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to cancel Worklist after worklist delete.", false));
            }

            WorklistMapper.insertHistory(
                    hospitalId,
                    worklistId,
                    Worklist.getPatientId(),
                    currentStatus.code(),
                    WorklistStatus.CANCELLED.code(),
                    WorklistConstants.ACTION_WORKLIST_DELETE,
                    "Deleted DicomServer worklist " + Worklist.getDicomServerWorklistId(),
                    userId
            );

            WorklistDetailRow refreshedWorklist = WorklistMapper.findWorklistById(hospitalId, worklistId);
            WorklistDicomWorklistResponse response = DicomServerWorklistMapperHelper.toWorklistDicomWorklistResponse(
                    refreshedWorklist == null ? Worklist : refreshedWorklist,
                    null,
                    "DicomServer worklist deleted and Worklist cancelled successfully."
            );
            response.setStatus(WorklistStatus.CANCELLED.name());

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.WORKLIST_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (Worklist Delete)", WorklistConstants.ACTION_WORKLIST_DELETE, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, List.of(response), true));
        } catch (HttpClientErrorException.NotFound error) {
            Long hospitalId = currentHospitalId();
            Long userId = currentUserId();
            WorklistDetailRow Worklist = WorklistMapper.findWorklistById(hospitalId, worklistId);
            WorklistDicomWorklistResponse response = null;
            if (Worklist != null) {
                WorklistStatus currentStatus = WorklistStatus.fromValue(Worklist.getStatus());
                WorklistMapper.updateWorklistStatusById(hospitalId, worklistId, WorklistStatus.CANCELLED.code(), userId);
                WorklistMapper.insertHistory(
                        hospitalId,
                        worklistId,
                        Worklist.getPatientId(),
                        currentStatus.code(),
                        WorklistStatus.CANCELLED.code(),
                        WorklistConstants.ACTION_WORKLIST_DELETE,
                        "DicomServer worklist already missing: " + Worklist.getDicomServerWorklistId(),
                        userId
                );
                WorklistDetailRow refreshedWorklist = WorklistMapper.findWorklistById(hospitalId, worklistId);
                response = DicomServerWorklistMapperHelper.toWorklistDicomWorklistResponse(
                        refreshedWorklist == null ? Worklist : refreshedWorklist,
                        null,
                        "DicomServer worklist already missing. Worklist marked as cancelled."
                );
                response.setStatus(WorklistStatus.CANCELLED.name());
            }
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.WORKLIST_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (" + WorklistConstants.ACTION_WORKLIST_DELETE + ")", WorklistConstants.ACTION_WORKLIST_DELETE, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, response == null ? null : List.of(response), true));
        } catch (HttpClientErrorException.Unauthorized error) {
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.WORKLIST_PATH, WorklistConstants.ACTION_WORKLIST_DELETE, WorklistConstants.MSG_DICOM_SERVER_UNAUTHORIZED, error);
        } catch (ResourceAccessException error) {
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.WORKLIST_PATH, WorklistConstants.ACTION_WORKLIST_DELETE, WorklistConstants.MSG_DICOM_SERVER_UNREACHABLE, error);
        } catch (RestClientException error) {
            return WorklistDicomServerClientError(startDuration, httpServletRequest, ApiConstants.Worklist.WORKLIST_PATH, WorklistConstants.ACTION_WORKLIST_DELETE, "Failed to delete DicomServer worklist.", error);
        } catch (IllegalArgumentException validationError) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            return WorklistDicomServerUnexpectedError(startDuration, httpServletRequest, ApiConstants.Worklist.WORKLIST_PATH, WorklistConstants.ACTION_WORKLIST_DELETE, "An unexpected error occurred. Please try again.", error);
        }
    }

    @Override
    public ResponseMessage<BaseResult> receivedStudy(WorklistReceivedStudyRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        String receivedAtIso = OffsetDateTime.now().toString();
        try {
            if (!isAuthorizedDicomServerCallback(httpServletRequest)) {
                insertDicomServerCallbackLog(request, false, "Unauthorized DICOM server callback.", null, receivedAtIso);
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.RECEIVED_STUDY_PATH, null, "Unauthorized DICOM server callback.", WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_RECEIVED_STUDY, WorklistConstants.ACTION_RECEIVED_STUDY, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_REJECTED, startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(false, 401, "UNAUTHORIZED", "Unauthorized");
            }

            String accessionNumber = normalizedOrEmpty(request == null ? null : request.getAccessionNumber());
            String visitCode = normalizedOrEmpty(request == null ? null : request.getVisitCode());
            String requestedDicomServerStudyId = normalizedOrEmpty(request == null ? null : request.getDicomServerStudyId());
            String requestedStudyInstanceUid = firstNonBlank(
                    normalizedOrEmpty(request == null ? null : request.getStudyInstanceUid()),
                    normalizedOrEmpty(request == null ? null : request.getStudyUuid())
            );
            if (!hasText(accessionNumber) && !hasText(visitCode) && !hasText(requestedDicomServerStudyId) && !hasText(requestedStudyInstanceUid)) {
                insertDicomServerCallbackLog(request, false, "accessionNumber, visitCode, or study identifier is required.", null, receivedAtIso);
                return ResponseMessageUtils.makeResponse(false, messageService.message("accessionNumber, visitCode, or study identifier is required.", false));
            }

            Long callbackDicomServerId = resolveCallbackDicomServerId();
            HospitalDicomServerResponse callbackServer = resolveCallbackDicomServer(callbackDicomServerId);
            WorklistDetailRow Worklist = resolveCallbackWorklist(accessionNumber, visitCode, requestedStudyInstanceUid, requestedDicomServerStudyId, callbackServer);
            if (Worklist == null) {
                String notFoundMessage = "Callback acknowledged without a matching Worklist. Direct Study uploads are saved by the upload flow.";
                insertDicomServerCallbackLog(request, true, null, notFoundMessage, receivedAtIso);
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.RECEIVED_STUDY_PATH, null, notFoundMessage, WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_RECEIVED_STUDY, WorklistConstants.ACTION_RECEIVED_STUDY, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message(notFoundMessage, true));
            }

            if (!isCallbackAllowedForWorklist(callbackDicomServerId, callbackServer, Worklist)) {
                String message = "Callback DICOM server does not match this Worklist route.";
                insertDicomServerCallbackLog(request, false, message, null, receivedAtIso);
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.RECEIVED_STUDY_PATH, null, message, WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_RECEIVED_STUDY, WorklistConstants.ACTION_RECEIVED_STUDY, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_REJECTED, startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Forbidden");
            }

            WorklistStatus currentStatus = safeWorklistStatus(Worklist.getStatus());
            String warningMessage = null;
            WorklistStatus responseStatus = currentStatus;

            if (currentStatus == WorklistStatus.CANCELLED) {
                String message = "Callback received but Worklist is already " + currentStatus.name() + ".";
                insertDicomServerCallbackLog(request, true, null, message, receivedAtIso);
                WorklistActionResponse response = buildWorklistActionResponse(Worklist, firstNonBlank(Worklist.getStudyInstanceUid(), Worklist.getStudyUuid(), Worklist.getDicomServerStudyId()), currentStatus.name(), message);
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.RECEIVED_STUDY_PATH, null, null, WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_RECEIVED_STUDY, WorklistConstants.ACTION_RECEIVED_STUDY, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(true, messageService.message(message, List.of(response), true));
            }

            HospitalDicomServerResponse targetServer = resolveTargetDicomServer(Worklist, null);
            DicomServerStudyResponse verifiedStudy;
            try {
                verifiedStudy = resolveVerifiedCallbackStudy(Worklist, request, targetServer);
            } catch (IllegalArgumentException validationError) {
                String message = validationError.getMessage();
                insertDicomServerCallbackLog(request, false, message, null, receivedAtIso);
                LocalTime endDuration = LocalTime.now();
                activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.RECEIVED_STUDY_PATH, null, message, WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_RECEIVED_STUDY, WorklistConstants.ACTION_RECEIVED_STUDY, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_REJECTED, startDuration, endDuration, httpServletRequest);
                return ResponseMessageUtils.makeResponse(false, messageService.message(message, false));
            }
            LinkedStudyContext linkedStudy = buildCallbackLinkedStudyContext(Worklist, request, targetServer, verifiedStudy, receivedAtIso);
            String resolvedStudyUuid = firstNonBlank(
                    linkedStudy.studyInstanceUid(),
                    normalizedOrEmpty(request == null ? null : request.getStudyUuid()),
                    linkedStudy.dicomServerStudyId(),
                    Worklist.getStudyInstanceUid(),
                    Worklist.getStudyUuid(),
                    Worklist.getDicomServerStudyId()
            );

            WorklistStatus targetStatus = currentStatus;
            boolean insertHistory = false;
            String successMessage;
            if (currentStatus == WorklistStatus.WAITING) {
                targetStatus = WorklistStatus.IN_PROGRESS;
                insertHistory = true;
                warningMessage = "Image received before send-to-pacs status.";
                successMessage = "Image received and moved to Study Archive.";
            } else if (currentStatus == WorklistStatus.IN_PROGRESS) {
                targetStatus = WorklistStatus.IN_PROGRESS;
                successMessage = "Image received and moved to Study Archive.";
            } else if (currentStatus == WorklistStatus.FAILED) {
                targetStatus = WorklistStatus.IN_PROGRESS;
                insertHistory = true;
                warningMessage = "Image received while Worklist was marked FAILED.";
                successMessage = "Image received and moved to Study Archive.";
            } else {
                warningMessage = "Callback received for Worklist status " + currentStatus.name() + ".";
                successMessage = "Callback received.";
            }

            int updated = WorklistMapper.updateWorklistReceivedFromCallbackById(
                    Worklist.getHospitalId(),
                    Worklist.getId(),
                    linkedStudy.studyId(),
                    targetStatus.code(),
                    null,
                    receivedAtIso
            );
            if (updated <= 0) {
                insertDicomServerCallbackLog(request, false, WorklistConstants.MSG_UNABLE_TO_UPDATE_STATUS, warningMessage, receivedAtIso);
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_UNABLE_TO_UPDATE_STATUS, false));
            }

            persistWorklistStudyLink(Worklist, linkedStudy, null);
            if (insertHistory && targetStatus != currentStatus) {
                WorklistMapper.insertHistory(
                        Worklist.getHospitalId(),
                        Worklist.getId(),
                        Worklist.getPatientId(),
                        currentStatus.code(),
                        targetStatus.code(),
                        WorklistConstants.ACTION_RECEIVED_STUDY,
                        firstNonBlank(warningMessage, "DicomServer callback accessionNumber=" + firstNonBlank(accessionNumber, Worklist.getAccessionNumber())),
                        null
                );
            }

            responseStatus = targetStatus;
            WorklistDetailRow refreshedWorklist = WorklistMapper.findWorklistById(Worklist.getHospitalId(), Worklist.getId());
            WorklistDetailRow responseWorklist = refreshedWorklist == null ? Worklist : refreshedWorklist;
            WorklistActionResponse response = buildWorklistActionResponse(
                    responseWorklist,
                    firstNonBlank(responseWorklist.getStudyInstanceUid(), responseWorklist.getStudyUuid(), resolvedStudyUuid),
                    responseStatus.name(),
                    successMessage
            );
            insertDicomServerCallbackLog(request, true, null, warningMessage, receivedAtIso);
            if (realtimeNotificationService != null) {
                realtimeNotificationService.publishImageReceived(
                        responseWorklist,
                        linkedStudy.studyId(),
                        responseWorklist.getStudyPublicKey(),
                        successMessage
                );
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.RECEIVED_STUDY_PATH, null, null, WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_RECEIVED_STUDY, WorklistConstants.ACTION_RECEIVED_STUDY, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(successMessage, List.of(response), true));
        } catch (Exception error) {
            LOGGER.error("Worklist-received-study failed for accessionNumber={} visitCode={} dicomServerStudyId={} error={}",
                    request == null ? null : request.getAccessionNumber(),
                    request == null ? null : request.getVisitCode(),
                    request == null ? null : request.getDicomServerStudyId(),
                    error.toString(),
                    error);
            insertDicomServerCallbackLog(request, false, firstNonBlank(error.getMessage(), "Unexpected callback error."), null, receivedAtIso);
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.RECEIVED_STUDY_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, WorklistConstants.LABEL_RECEIVED_STUDY, WorklistConstants.ACTION_RECEIVED_STUDY, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> authorizePublicViewer(
            PublicViewerAuthorizeRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        try {
            if (request == null
                    || !hasText(request.getHospitalKey())
                    || (!hasText(request.getWorklistKey()) && !hasText(request.getStudyKey()))
                    || !hasText(request.getPhoneNumber())) {
                return publicViewerAccessDenied();
            }
            String linkKey = firstNonBlank(request.getWorklistKey(), request.getStudyKey(), "");
            if (publicViewerAttemptGuard.isBlocked(request.getHospitalKey(), linkKey)) {
                return publicViewerAccessDenied();
            }

            if (hasText(request.getStudyKey()) && !hasText(request.getWorklistKey())) {
                return authorizePublicStudyViewer(request, httpServletRequest, linkKey);
            }
            return authorizePublicWorklistViewer(request, httpServletRequest, linkKey);
        } catch (Exception error) {
            LOGGER.warn("Public viewer authorization failed: {}", error.toString());
            return publicViewerAccessDenied();
        }
    }

    private ResponseMessage<BaseResult> authorizePublicWorklistViewer(
            PublicViewerAuthorizeRequest request,
            HttpServletRequest httpServletRequest,
            String linkKey
    ) throws UnknownHostException {
            Long hospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long worklistId = publicEntityKeyResolver.resolve(Entity.WORKLIST, request.getWorklistKey(), null);
            WorklistDetailRow worklist = hospitalId == null || worklistId == null
                    ? null
                    : WorklistMapper.findWorklistById(hospitalId, worklistId);
            PatientResponse patient = worklist == null || worklist.getPatientId() == null
                    ? null
                    : patientMapper.findById(hospitalId, worklist.getPatientId());

            String suppliedPhone = normalizePhoneNumber(request.getPhoneNumber());
            String storedPhone = normalizePhoneNumber(patient == null ? null : patient.getPhoneNumber());
            boolean verified = suppliedPhone.length() >= 7
                    && storedPhone.length() >= 7
                    && constantTimeEquals(storedPhone, suppliedPhone);
            if (!verified) {
                publicViewerAttemptGuard.recordFailure(request.getHospitalKey(), linkKey);
                return publicViewerAccessDenied();
            }

            ResponseMessage<BaseResult> response = getViewerInfoInternal(
                    worklistId,
                    hospitalId,
                    request.getMode(),
                    ViewerAccessKeyService.ACCESS_PUBLIC,
                    httpServletRequest,
                    true
            );
            if (response != null && response.isSuccess()) {
                publicViewerAttemptGuard.clear(request.getHospitalKey(), linkKey);
            }
            return response != null && response.isSuccess() ? response : publicViewerAccessDenied();
    }

    private ResponseMessage<BaseResult> authorizePublicStudyViewer(
            PublicViewerAuthorizeRequest request,
            HttpServletRequest httpServletRequest,
            String linkKey
    ) {
        Long hospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
        Long studyId = publicEntityKeyResolver.resolve(Entity.STUDY, request.getStudyKey(), null);
        StudyResponse study = hospitalId == null || studyId == null
                ? null
                : studyMapper.findById(hospitalId, studyId);
        PatientResponse patient = study == null || study.getPatientId() == null
                ? null
                : patientMapper.findById(hospitalId, study.getPatientId());

        String suppliedPhone = normalizePhoneNumber(request.getPhoneNumber());
        String storedPhone = normalizePhoneNumber(patient == null ? null : patient.getPhoneNumber());
        boolean verified = suppliedPhone.length() >= 7
                && storedPhone.length() >= 7
                && constantTimeEquals(storedPhone, suppliedPhone);
        if (!verified) {
            publicViewerAttemptGuard.recordFailure(request.getHospitalKey(), linkKey);
            return publicViewerAccessDenied();
        }

        ViewerInfoResponse response = buildPublicStudyViewerInfo(study, request.getMode(), httpServletRequest);
        if (response == null) {
            return publicViewerAccessDenied();
        }
        publicViewerAttemptGuard.clear(request.getHospitalKey(), linkKey);
        return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(response), true));
    }

    private ResponseMessage<BaseResult> publicViewerAccessDenied() {
        return ResponseMessageUtils.makeResponse(
                false,
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Unable to verify viewer access."
        );
    }

    private static String normalizePhoneNumber(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        byte[] left = firstNonBlank(expected, "").getBytes(StandardCharsets.UTF_8);
        byte[] right = firstNonBlank(provided, "").getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    @Override
    public ResponseMessage<BaseResult> getViewerInfo(Long worklistId, Long requestedHospitalId, String mode, String requestedViewerAccess, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return getViewerInfoInternal(
                worklistId,
                requestedHospitalId,
                mode,
                requestedViewerAccess,
                httpServletRequest,
                false
        );
    }

    private ResponseMessage<BaseResult> getViewerInfoInternal(
            Long worklistId,
            Long requestedHospitalId,
            String mode,
            String requestedViewerAccess,
            HttpServletRequest httpServletRequest,
            boolean publicAccess
    ) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (worklistId == null || worklistId <= 0L) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("worklistId is required.", false));
            }

            Long hospitalId = publicAccess
                    ? (requestedHospitalId != null && requestedHospitalId > 0L ? requestedHospitalId : null)
                    : resolveHospitalId(requestedHospitalId);
            WorklistDetailRow Worklist = findWorklistByIdentifier(hospitalId, worklistId, null);
            if (!publicAccess && Worklist == null && isAdminUser() && (requestedHospitalId == null || requestedHospitalId <= 0L)) {
                Worklist = findWorklistByIdentifier(null, worklistId, null);
            }
            if (Worklist == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_WORKLIST_NOT_FOUND, false));
            }
            if (safeWorklistStatus(Worklist.getStatus()) == WorklistStatus.CANCELLED) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Cancelled Worklists cannot open the viewer.", false));
            }

            String studyInstanceUid = resolveWorklistStudyInstanceUid(Worklist);
            String dicomServerStudyId = resolveWorklistDicomServerStudyId(Worklist);
            if (!hasText(studyInstanceUid) && !hasText(dicomServerStudyId)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study is not received yet.", false));
            }

            Long actorId = publicAccess ? null : currentUserId();
            HospitalDicomServerResponse targetServer = resolveTargetDicomServer(Worklist, actorId);
            DicomServerStudyResponse studyResponse = null;
            if (!hasText(studyInstanceUid)) {
                studyResponse = getDicomServerStudy(dicomServerStudyId, targetServer);
                if (studyResponse == null) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Study is not available in DicomServer yet.", false));
                }
                studyInstanceUid = readDicomTag(studyResponse, DicomTagConstants.STUDY_INSTANCE_UID);
                if (!hasText(studyInstanceUid)) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("DicomServer study is missing Study Instance UID.", false));
                }

                String receivedAtIso = OffsetDateTime.now().toString();
                LinkedStudyContext linkedStudy = buildLinkedStudyContext(Worklist, dicomServerStudyId, studyResponse, targetServer, actorId, receivedAtIso);
                WorklistMapper.updateWorklistViewerStudyIdentifiers(
                        Worklist.getHospitalId(),
                        Worklist.getId(),
                        linkedStudy.studyId(),
                        actorId
                );
                persistWorklistStudyLink(Worklist, linkedStudy, actorId);
                WorklistDetailRow refreshedWorklist = WorklistMapper.findWorklistById(Worklist.getHospitalId(), Worklist.getId());
                if (refreshedWorklist != null) {
                    Worklist = refreshedWorklist;
                }
                studyInstanceUid = resolveWorklistStudyInstanceUid(Worklist);
                dicomServerStudyId = resolveWorklistDicomServerStudyId(Worklist);
            }

            String viewerBaseUrl = resolvePublicViewerBaseUrl(targetServer);
            if (!hasText(viewerBaseUrl)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Viewer base URL is not configured for this DICOM server.", false));
            }
            String dicomwebBaseUrl = resolveInternalDicomwebBaseUrl(targetServer);
            if (!hasText(dicomwebBaseUrl)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("DICOMweb gateway cannot resolve the DICOM server endpoint.", false));
            }

            dicomServerStudyId = firstNonBlank(dicomServerStudyId, resolveDicomServerStudyIdForViewer(Worklist, targetServer));
            if (hasText(dicomServerStudyId) && studyResponse == null) {
                studyResponse = getDicomServerStudy(dicomServerStudyId, targetServer);
            }
            if (!hasText(dicomServerStudyId) || studyResponse == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study is not available in DicomServer yet.", false));
            }
            Integer totalInstances = resolveStudyInstanceCount(dicomServerStudyId, studyResponse, targetServer);
            if (totalInstances == null || totalInstances <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study has no image instances yet.", false));
            }
            Integer seriesCount = studyResponse.getSeries() == null ? 0 : studyResponse.getSeries().size();
            studyInstanceUid = firstNonBlank(studyInstanceUid, readDicomTag(studyResponse, DicomTagConstants.STUDY_INSTANCE_UID));

            StudyStatus studyStatus = resolveViewerStudyStatus(Worklist);
            if (studyStatus == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study status is not ready for viewer launch.", false));
            }

            ViewerInfoResponse response = buildViewerInfoResponse(
                    Worklist,
                    studyStatus,
                    targetServer,
                    resolvePublicPacsApiBaseUrl(targetServer, httpServletRequest),
                    studyResponse,
                    studyInstanceUid,
                    dicomServerStudyId,
                    mode,
                    requestedViewerAccess,
                    totalInstances,
                    seriesCount,
                    actorId,
                    publicAccess ? null : currentUsername()
            );

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_INFO_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (Viewer Info)", WorklistConstants.ACTION_VIEW_STUDY, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, List.of(response), true));
        } catch (Exception error) {
            LOGGER.error("Worklist-viewer-info failed for worklistId={} error={}", worklistId, error.toString(), error);
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_INFO_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (Viewer Info)", WorklistConstants.ACTION_VIEW_STUDY, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> viewStudy(WorklistViewStudyRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long requestedHospitalId = request == null
                    ? null
                    : publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            WorklistDetailRow Worklist = findWorklistByIdentifier(
                    hospitalId,
                    resolveWorklistId(request),
                    request != null ? request.getVisitCode() : null
            );
            if (Worklist == null && isAdminUser() && (requestedHospitalId == null || requestedHospitalId <= 0L)) {
                Worklist = findWorklistByIdentifier(
                        null,
                        resolveWorklistId(request),
                        request != null ? request.getVisitCode() : null
                );
            }
            if (Worklist == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_WORKLIST_NOT_FOUND, false));
            }
            if (!hasText(firstNonBlank(Worklist.getStudyInstanceUid(), Worklist.getStudyUuid(), Worklist.getDicomServerStudyId()))) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study is not received yet.", false));
            }

            Long actorId = currentUserId();
            HospitalDicomServerResponse targetServer = resolveTargetDicomServer(Worklist, actorId);
            String dicomServerStudyId = resolveDicomServerStudyIdForViewer(Worklist, targetServer);
            if (!hasText(dicomServerStudyId)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study is not available in DicomServer yet.", false));
            }
            DicomServerStudyResponse studyResponse = getDicomServerStudy(dicomServerStudyId, targetServer);
            if (studyResponse == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study preview is not available yet.", false));
            }
            if (!hasAvailableStudyInstances(dicomServerStudyId, studyResponse, targetServer)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study has no image instances yet.", false));
            }

            WorklistViewerStudyResponse response = buildWorklistViewerResponse(
                    Worklist,
                    dicomServerStudyId,
                    studyResponse,
                    targetServer,
                    resolvePublicPacsApiBaseUrl(targetServer, httpServletRequest)
            );

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEW_STUDY_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (View Study)", WorklistConstants.ACTION_VIEW_STUDY, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, List.of(response), true));
        } catch (Exception error) {
            LOGGER.error(
                    "Worklist-view-study failed for worklistId={} visitCode={} error={}",
                    resolveWorklistId(request),
                    request != null ? request.getVisitCode() : null,
                    error.toString(),
                    error
            );
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEW_STUDY_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (View Study)", WorklistConstants.ACTION_VIEW_STUDY, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseEntity<byte[]> viewStudyPreview(Long worklistId, String instanceId, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (worklistId == null || worklistId <= 0L || !hasText(instanceId)) {
                return ResponseEntity.badRequest().build();
            }

            Long hospitalId = currentHospitalId();
            Long actorId = currentUserId();
            WorklistDetailRow Worklist = findWorklistByIdentifier(hospitalId, worklistId, null);
            if (Worklist == null && isAdminUser()) {
                Worklist = findWorklistByIdentifier(null, worklistId, null);
            }
            if (Worklist == null) {
                return ResponseEntity.notFound().build();
            }

            HospitalDicomServerResponse targetServer = resolveTargetDicomServer(Worklist, actorId);
            String dicomServerStudyId = resolveDicomServerStudyIdForViewer(Worklist, targetServer);
            if (!hasText(dicomServerStudyId)) {
                return ResponseEntity.notFound().build();
            }

            DicomServerStudyResponse studyResponse = getDicomServerStudy(dicomServerStudyId, targetServer);
            if (studyResponse == null) {
                return ResponseEntity.notFound().build();
            }
            List<String> instanceIds = resolveViewerInstanceIds(dicomServerStudyId, studyResponse, targetServer);
            if (!instanceIds.contains(instanceId.trim())) {
                return ResponseEntity.notFound().build();
            }

            ResponseEntity<byte[]> upstream = getDicomServerInstancePreview(instanceId.trim(), targetServer);
            MediaType contentType = upstream.getHeaders().getContentType();
            MediaType safeContentType = contentType != null ? contentType : MediaType.IMAGE_JPEG;

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEW_STUDY_PREVIEW_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (View Study Preview)", WorklistConstants.ACTION_VIEW_STUDY, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseEntity
                    .status(upstream.getStatusCode())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(safeContentType)
                    .body(upstream.getBody());
        } catch (Exception error) {
            LOGGER.error(
                    "Worklist-view-study-preview failed for worklistId={} instanceId={} error={}",
                    worklistId,
                    instanceId,
                    error.toString(),
                    error
            );
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEW_STUDY_PREVIEW_PATH, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (View Study Preview)", WorklistConstants.ACTION_VIEW_STUDY, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Override
    public ResponseEntity<StreamingResponseBody> proxyViewerDicomWeb(
            String viewerToken,
            Long hospitalId,
            Long worklistId,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        try {
            if (!hasText(viewerToken) || hospitalId == null || hospitalId <= 0L || worklistId == null || worklistId <= 0L) {
                return ResponseEntity.badRequest().build();
            }

            ViewerDicomwebTokenClaims claims = validateViewerDicomwebToken(viewerToken, hospitalId, worklistId);
            String pathAndQuery = buildViewerDicomwebPathAndQuery(httpServletRequest, viewerToken, hospitalId, worklistId, claims.studyInstanceUid());
            return proxyViewerDicomWebToDicomServer(claims, hospitalId, worklistId, pathAndQuery, httpServletRequest);
        } catch (JwtException error) {
            LOGGER.warn("Viewer DICOMweb token rejected for worklistId={}: {}", worklistId, error.getMessage());
            return ResponseEntity.status(401).build();
        } catch (SecurityException error) {
            LOGGER.warn("Viewer DICOMweb request forbidden for worklistId={}: {}", worklistId, error.getMessage());
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException error) {
            LOGGER.warn("Viewer DICOMweb request rejected for worklistId={}: {}", worklistId, error.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (HttpClientErrorException error) {
            return ResponseEntity.status(error.getStatusCode()).build();
        } catch (Exception error) {
            LOGGER.error("Viewer DICOMweb proxy failed for worklistId={} error={}", worklistId, error.toString(), error);
            return ResponseEntity.status(502).build();
        }
    }

    @Override
    public ResponseEntity<StreamingResponseBody> proxyViewerDicomWeb(HttpServletRequest httpServletRequest) throws UnknownHostException {
        Long worklistId = null;
        Long studyId = null;
        try {
            String viewerToken = readViewerDicomwebTokenValue(httpServletRequest);
            ViewerDicomwebTokenClaims claims = decodeViewerDicomwebToken(viewerToken);
            worklistId = claims.worklistId();
            studyId = claims.studyId();
            String pathAndQuery = buildViewerDicomwebProxyPathAndQuery(httpServletRequest, claims.studyInstanceUid());
            if (claims.worklistId() != null && claims.worklistId() > 0L) {
                return proxyViewerDicomWebToDicomServer(claims, claims.hospitalId(), claims.worklistId(), pathAndQuery, httpServletRequest);
            }
            return proxyViewerStudyDicomWebToDicomServer(claims, claims.hospitalId(), claims.studyId(), pathAndQuery, httpServletRequest);
        } catch (JwtException error) {
            LOGGER.warn("Viewer DICOMweb token-routed proxy rejected for worklistId={} studyId={}: {}", worklistId, studyId, error.getMessage());
            return ResponseEntity.status(401).build();
        } catch (SecurityException error) {
            LOGGER.warn("Viewer DICOMweb token-routed request forbidden for worklistId={} studyId={}: {}", worklistId, studyId, error.getMessage());
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException error) {
            LOGGER.warn("Viewer DICOMweb token-routed request rejected for worklistId={} studyId={}: {}", worklistId, studyId, error.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (HttpClientErrorException error) {
            return ResponseEntity.status(error.getStatusCode()).build();
        } catch (Exception error) {
            LOGGER.error("Viewer DICOMweb token-routed proxy failed for worklistId={} studyId={} error={}", worklistId, studyId, error.toString(), error);
            return ResponseEntity.status(502).build();
        }
    }

    private ResponseEntity<StreamingResponseBody> proxyViewerDicomWebToDicomServer(
            ViewerDicomwebTokenClaims claims,
            Long hospitalId,
            Long worklistId,
            String pathAndQuery,
            HttpServletRequest httpServletRequest
    ) {
        if (claims == null || hospitalId == null || hospitalId <= 0L || worklistId == null || worklistId <= 0L) {
            return ResponseEntity.badRequest().build();
        }

        try {
            WorklistDetailRow Worklist = WorklistMapper.findWorklistById(hospitalId, worklistId);
            if (Worklist == null) {
                return ResponseEntity.notFound().build();
            }

            HospitalDicomServerResponse targetServer = resolveTargetDicomServer(Worklist, null);
            if (targetServer == null) {
                return ResponseEntity.notFound().build();
            }

            String dicomServerStudyId = resolveDicomServerStudyIdForViewer(Worklist, targetServer);
            if (!hasText(dicomServerStudyId)) {
                return ResponseEntity.notFound().build();
            }

            DicomServerStudyResponse studyResponse = getDicomServerStudy(dicomServerStudyId, targetServer);
            if (studyResponse == null) {
                return ResponseEntity.notFound().build();
            }
            if (!hasAvailableStudyInstances(dicomServerStudyId, studyResponse, targetServer)) {
                return ResponseEntity.status(409).build();
            }

            String resolvedStudyInstanceUid = firstNonBlank(
                    resolveWorklistStudyInstanceUid(Worklist),
                    readDicomTag(studyResponse, DicomTagConstants.STUDY_INSTANCE_UID)
            );
            if (!claims.studyInstanceUid().equals(resolvedStudyInstanceUid)) {
                return ResponseEntity.status(403).build();
            }

            String dicomwebBaseUrl = resolveInternalDicomwebBaseUrl(targetServer);
            if (!hasText(dicomwebBaseUrl)) {
                return ResponseEntity.status(502).build();
            }

            return dicomServerClientService.proxyDicomWeb(
                    dicomwebBaseUrl,
                    targetServer.getUsername(),
                    targetServer.getPassword(),
                    pathAndQuery,
                    httpServletRequest == null ? null : httpServletRequest.getHeader(HttpHeaders.ACCEPT)
            );
        } catch (JwtException error) {
            LOGGER.warn("Viewer DICOMweb token rejected for worklistId={}: {}", worklistId, error.getMessage());
            return ResponseEntity.status(401).build();
        } catch (SecurityException error) {
            LOGGER.warn("Viewer DICOMweb request forbidden for worklistId={}: {}", worklistId, error.getMessage());
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException error) {
            LOGGER.warn("Viewer DICOMweb request rejected for worklistId={}: {}", worklistId, error.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (HttpClientErrorException error) {
            return ResponseEntity.status(error.getStatusCode()).build();
        } catch (Exception error) {
            LOGGER.error("Viewer DICOMweb proxy failed for worklistId={} error={}", worklistId, error.toString(), error);
            return ResponseEntity.status(502).build();
        }
    }

    private ResponseEntity<StreamingResponseBody> proxyViewerStudyDicomWebToDicomServer(
            ViewerDicomwebTokenClaims claims,
            Long hospitalId,
            Long studyId,
            String pathAndQuery,
            HttpServletRequest httpServletRequest
    ) {
        if (claims == null || hospitalId == null || hospitalId <= 0L || studyId == null || studyId <= 0L) {
            return ResponseEntity.badRequest().build();
        }

        try {
            StudyResponse study = studyMapper.findById(hospitalId, studyId);
            if (study == null) {
                return ResponseEntity.notFound().build();
            }
            if (!claims.studyInstanceUid().equals(study.getStudyInstanceUid())) {
                return ResponseEntity.status(403).build();
            }

            HospitalDicomServerResponse targetServer = resolveStudyDicomServer(study, hospitalId);
            if (targetServer == null) {
                return ResponseEntity.notFound().build();
            }

            String dicomwebBaseUrl = resolveInternalDicomwebBaseUrl(targetServer);
            if (!hasText(dicomwebBaseUrl)) {
                return ResponseEntity.status(502).build();
            }

            return dicomServerClientService.proxyDicomWeb(
                    dicomwebBaseUrl,
                    targetServer.getUsername(),
                    targetServer.getPassword(),
                    pathAndQuery,
                    httpServletRequest == null ? null : httpServletRequest.getHeader(HttpHeaders.ACCEPT)
            );
        } catch (JwtException error) {
            LOGGER.warn("Viewer DICOMweb token rejected for studyId={}: {}", studyId, error.getMessage());
            return ResponseEntity.status(401).build();
        } catch (SecurityException error) {
            LOGGER.warn("Viewer DICOMweb request forbidden for studyId={}: {}", studyId, error.getMessage());
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException error) {
            LOGGER.warn("Viewer DICOMweb request rejected for studyId={}: {}", studyId, error.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (HttpClientErrorException error) {
            return ResponseEntity.status(error.getStatusCode()).build();
        } catch (Exception error) {
            LOGGER.error("Viewer DICOMweb proxy failed for studyId={} error={}", studyId, error.toString(), error);
            return ResponseEntity.status(502).build();
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> authorizeViewerDicomWeb(Map<String, Object> request) {
        boolean granted = false;
        try {
            String tokenValue = firstNonBlank(
                    readStringValue(request, AUTH_FIELD_TOKEN_VALUE_KEBAB),
                    readStringValue(request, AUTH_FIELD_TOKEN_VALUE),
                    readStringValue(request, AUTH_FIELD_AUTHORIZATION_LOWER),
                    readStringValue(request, AUTH_FIELD_AUTHORIZATION)
            );
            if (isBasicCredentialToken(tokenValue)) {
                granted = findAuthorizedDicomServerHttpClient(request) != null;
                Map<String, Object> body = new HashMap<>();
                body.put("granted", granted);
                body.put(AUTH_FIELD_VALIDITY, granted ? AUTH_VALIDITY_SECONDS : 0);
                return ResponseEntity.ok(body);
            }
            ViewerDicomwebTokenClaims claims = decodeViewerDicomwebToken(tokenValue);
            String uri = firstNonBlank(
                    readStringValue(request, "uri"),
                    readStringValue(request, "url"),
                    readStringValue(request, "resource"),
                    readStringValue(request, "path")
            );
            String method = firstNonBlank(readStringValue(request, "method"), readStringValue(request, "http-method"), HTTP_METHOD_GET);
            String level = firstNonBlank(readStringValue(request, "level"), "");
            String requestedStudyUid = firstNonBlank(
                    findFirstStringByKey(request, DicomTagConstants.STUDY_INSTANCE_UID),
                    findFirstStringByKey(request, "StudyInstanceUIDs"),
                    findFirstStringByKey(request, "study-instance-uid"),
                    findFirstStringByKey(request, "studyInstanceUid")
            );
            String requestedDicomUid = findFirstStringByKey(request, "dicom-uid");
            if (!hasText(requestedStudyUid) && ("study".equalsIgnoreCase(level) || !hasText(uri))) {
                requestedStudyUid = requestedDicomUid;
            }

            boolean requestedStudyUidMatches = hasText(requestedStudyUid) && requestedStudyUid.trim().equals(claims.studyInstanceUid());
            boolean uidMatches = !hasText(requestedStudyUid) || requestedStudyUidMatches;
            boolean studyFindRequest = viewerDicomwebIsStudyFindRequest(uri, requestedStudyUidMatches);
            boolean studyFindResourceCheck = requestedStudyUidMatches && "study".equalsIgnoreCase(level) && !hasText(uri);
            boolean allowedMethod = HTTP_METHOD_GET.equalsIgnoreCase(method)
                    || "HEAD".equalsIgnoreCase(method)
                    || "OPTIONS".equalsIgnoreCase(method)
                    || ("POST".equalsIgnoreCase(method) && (studyFindRequest || studyFindResourceCheck));
            boolean uriMatches = studyFindRequest
                    || studyFindResourceCheck
                    || viewerDicomwebUriMatchesStudy(uri, claims.studyInstanceUid(), uidMatches);
            granted = allowedMethod && uidMatches && uriMatches;
        } catch (JwtException | IllegalArgumentException error) {
            LOGGER.warn("Direct DicomServer viewer DICOMweb token rejected: {}", error.getMessage());
        } catch (Exception error) {
            LOGGER.warn("Direct DicomServer viewer DICOMweb authorization failed: {}", error.toString());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("granted", granted);
        body.put(AUTH_FIELD_VALIDITY, granted ? AUTH_VALIDITY_SECONDS : 0);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Void> authorizeViewerDicomWebProxy(HttpServletRequest request) {
        try {
            String originalUri = firstNonBlank(
                    request == null ? null : request.getHeader("X-Original-URI"),
                    request == null ? null : request.getRequestURI()
            );
            String method = firstNonBlank(
                    request == null ? null : request.getHeader("X-Original-Method"),
                    request == null ? null : request.getMethod(),
                    HTTP_METHOD_GET
            );
            String tokenValue = firstNonBlank(
                    request == null ? null : request.getHeader("X-PACS-DICOMWEB-TOKEN")
            );
            ViewerDicomwebTokenClaims claims = decodeViewerDicomwebToken(tokenValue);
            validateViewerDicomwebProxyRequest(originalUri, method, claims.studyInstanceUid());
            return ResponseEntity.noContent().build();
        } catch (JwtException | IllegalArgumentException | SecurityException error) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception error) {
            LOGGER.warn("Viewer DICOMweb proxy authorization failed: {}", error.toString());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> decodeViewerDicomWeb(Map<String, Object> request) {
        Map<String, Object> body = new HashMap<>();
        try {
            String tokenValue = firstNonBlank(
                    readStringValue(request, AUTH_FIELD_TOKEN_VALUE_KEBAB),
                    readStringValue(request, AUTH_FIELD_TOKEN_VALUE),
                    readStringValue(request, AUTH_FIELD_AUTHORIZATION_LOWER),
                    readStringValue(request, AUTH_FIELD_AUTHORIZATION)
            );
            ViewerDicomwebTokenClaims claims = decodeViewerDicomwebToken(tokenValue);
            Map<String, Object> resource = new HashMap<>();
            resource.put("dicom-uid", claims.studyInstanceUid());
            resource.put("dicom_uid", claims.studyInstanceUid());
            resource.put("DicomUid", claims.studyInstanceUid());
            resource.put("level", "study");
            resource.put("Level", "study");

            body.put("token-type", "ohif-viewer-publication");
            body.put("resources", List.of(resource));
            body.put(AUTH_FIELD_VALIDITY, AUTH_VALIDITY_SECONDS);
            return ResponseEntity.ok(body);
        } catch (JwtException | IllegalArgumentException error) {
            LOGGER.warn("Direct DicomServer viewer DICOMweb token decode rejected: {}", error.getMessage());
        } catch (Exception error) {
            LOGGER.warn("Direct DicomServer viewer DICOMweb token decode failed: {}", error.toString());
        }

        body.put("token-type", "invalid");
        body.put("resources", List.of());
        body.put("error-code", "invalid");
        body.put(AUTH_FIELD_VALIDITY, 0);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Map<String, Object>> profileViewerDicomWeb(Map<String, Object> request) {
        Map<String, Object> body = new HashMap<>();
        String tokenValue = firstNonBlank(
                readStringValue(request, AUTH_FIELD_TOKEN_VALUE_KEBAB),
                readStringValue(request, AUTH_FIELD_TOKEN_VALUE),
                readStringValue(request, AUTH_FIELD_AUTHORIZATION_LOWER),
                readStringValue(request, AUTH_FIELD_AUTHORIZATION)
        );
        boolean authorized = false;
        String profileName = "UDAYA_DICOM_SERVER Viewer";
        List<String> permissions = List.of();

        if (isBasicCredentialToken(tokenValue)) {
            HospitalDicomServerResponse server = findAuthorizedDicomServerHttpClient(request);
            authorized = server != null;
            profileName = "UDAYA_DICOM_SERVER Archive API Client";
            permissions = authorized ? List.of("all") : List.of();
        } else {
            try {
                decodeViewerDicomwebToken(tokenValue);
                authorized = true;
                permissions = List.of("view", "download");
            } catch (JwtException | IllegalArgumentException error) {
                LOGGER.debug("Viewer DICOMweb profile token rejected: {}", error.getMessage());
            }
        }
        body.put("name", profileName);
        body.put("authorized-labels", authorized ? List.of("*") : List.of());
        body.put("permissions", permissions);
        body.put(AUTH_FIELD_VALIDITY, authorized ? 60 : 0);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Map<String, Object>> renewViewerDicomWeb(Map<String, Object> request) {
        Map<String, Object> body = new HashMap<>();
        try {
            ViewerDicomwebTokenClaims claims = resolveViewerRenewClaims(request);
            String nextToken = issueViewerDicomwebToken(claims.hospitalId(), claims.worklistId(), claims.studyId(), claims.studyInstanceUid());
            if (!hasText(nextToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        AUTH_FIELD_GRANTED, false,
                        "message", "Unable to renew viewer token."
                ));
            }
            body.put(AUTH_FIELD_GRANTED, true);
            body.put(PARAM_TOKEN, nextToken);
            body.put("tokenType", "Bearer");
            body.put("expiresInSeconds", viewerDicomwebTokenMs / 1000L);
            appendRenewedViewerAccessToken(body, request, claims);
            return ResponseEntity.ok(body);
        } catch (JwtException | IllegalArgumentException error) {
            LOGGER.warn("Viewer DICOMweb token renew rejected: {}", error.getMessage());
            body.put(AUTH_FIELD_GRANTED, false);
            body.put("message", "Viewer token is invalid or expired.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        } catch (Exception error) {
            LOGGER.warn("Viewer DICOMweb token renew failed: {}", error.toString());
            body.put(AUTH_FIELD_GRANTED, false);
            body.put("message", "Unable to renew viewer token.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
    }

    @Override
    public ResponseEntity<Void> revokeViewerDicomWeb(Map<String, Object> request) {
        try {
            ViewerDicomwebTokenClaims claims = decodeViewerDicomwebToken(readViewerLifecycleTokenValue(request));
            revokeViewerToken(claims);
        } catch (JwtException | IllegalArgumentException error) {
            LOGGER.debug("Viewer DICOMweb token revoke ignored: {}", error.getMessage());
        } catch (Exception error) {
            LOGGER.warn("Viewer DICOMweb token revoke failed: {}", error.toString());
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseMessage<BaseResult> updateStatus(WorklistActionRequest request, String status, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            request.setId(publicEntityKeyResolver.resolve(Entity.WORKLIST, request.getPublicKey(), request.getId()));
            if (request.getId() == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }

            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            WorklistStatus targetStatus = WorklistStatus.fromValue(normalizeStatus(status));
            WorklistDetailRow Worklist = WorklistMapper.findWorklistById(hospitalId, request.getId());
            if (Worklist == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(WorklistConstants.MSG_WORKLIST_NOT_FOUND, false));
            }

            Long actorId = currentUserId();
            WorklistStatus currentStatus = WorklistStatus.fromValue(Worklist.getStatus());

            if (targetStatus == WorklistStatus.CANCELLED) {
                if (currentStatus == WorklistStatus.WAITING || currentStatus == WorklistStatus.FAILED) {
                    int updated = WorklistMapper.updateWorklistWorkflowStatusById(hospitalId, Worklist.getId(), WorklistStatus.CANCELLED.code(), null, actorId);
                    if (updated <= 0) {
                        return ResponseMessageUtils.makeResponse(false, messageService.message("Update failed", false));
                    }
                    WorklistMapper.insertHistory(hospitalId, Worklist.getId(), Worklist.getPatientId(), currentStatus.code(), WorklistStatus.CANCELLED.code(), WorklistConstants.ACTION_CANCEL, request.getNotes(), actorId);
                    LocalTime endDuration = LocalTime.now();
                    activityLogService.insert(ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.CANCEL_PATH, null, null, WorklistConstants.MODULE_CODE, "Worklist (Cancel)", WorklistConstants.ACTION_CANCEL, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
                    return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, true));
                }

                if (currentStatus == WorklistStatus.IN_PROGRESS) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Cannot cancel because scan process is already in progress.", false));
                }
                return ResponseMessageUtils.makeResponse(false, messageService.message("This Worklist cannot be cancelled in the current status.", false));
            }

            if (!currentStatus.canTransitionTo(targetStatus)) {
                return ResponseMessageUtils.makeResponse(false, 403, "FORBIDDEN", "Invalid Worklist status transition");
            }

            int updated = WorklistMapper.updateWorklistWorkflowStatusById(hospitalId, Worklist.getId(), targetStatus.code(), null, actorId);
            if (updated <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Update failed", false));
            }

            WorklistMapper.insertHistory(
                    hospitalId,
                    request.getId(),
                    Worklist.getPatientId(),
                    currentStatus.code(),
                    targetStatus.code(),
                    toWorklistAction(targetStatus),
                    request.getNotes(),
                    actorId
            );

            String endpoint = toWorklistEndpoint(targetStatus);
            String action = toWorklistAction(targetStatus);
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(endpoint, null, null, WorklistConstants.MODULE_CODE, "Worklist (" + action + ")", action, WorklistConstants.LOG_STATUS_SUCCESS, WorklistConstants.RESULT_SUCCESS, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(WorklistConstants.RESULT_SUCCESS, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            WorklistStatus resolved = safeWorklistStatus(status);
            String endpoint = toWorklistEndpoint(resolved);
            String action = toWorklistAction(resolved);
            activityLogService.insert(endpoint, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (" + action + ")", action, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private String generateVisitCode(Long hospitalId, Long modalityId) {
        String dateToken = FunctionCodeGenerate.currentVisitDateToken();
        String visitPrefix = resolveVisitPrefix(modalityId);
        String hospitalToken = resolveVisitHospitalToken(hospitalId);
        String sequenceKey = FunctionCodeGenerate.buildVisitSequenceKey(visitPrefix, hospitalToken, dateToken);
        Long nextSequence = WorklistMapper.nextVisitSequence(hospitalId, sequenceKey);
        long sequence = nextSequence == null ? 1L : nextSequence;
        return FunctionCodeGenerate.buildVisitCode(visitPrefix, hospitalToken, dateToken, sequence);
    }

    private String resolveVisitHospitalToken(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0L) {
            return "HOSP";
        }
        try {
            List<HospitalResponseDetail> rows = hospitalMapper.getHospitalById(hospitalId);
            if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
                HospitalResponseDetail hospital = rows.get(0);
                String token = FunctionHelper.normalizeHospitalToken(hospital.getAbbr());
                if (FunctionHelper.isValidHospitalToken(token)) {
                    return token;
                }
            }
        } catch (Exception ignored) {
            // Fall back to the numeric hospital id if hospital lookup is temporarily unavailable.
        }
        return "H" + hospitalId;
    }

    private String resolveVisitPrefix(Long modalityId) {
        if (modalityId == null || modalityId <= 0) {
            return "OT";
        }
        try {
            List<com.ut.emrPacs.model.dto.response.systemSettings.modality.ModalityResponse> rows = modalityMapper.getModalityById(modalityId);
            if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
                String modalityAbbr = rows.get(0).getAbbr();
                if (modalityAbbr != null && !modalityAbbr.isBlank()) {
                    return FunctionHelper.normalizeModalityToken(modalityAbbr);
                }
                return FunctionHelper.normalizeModalityToken(rows.get(0).getName());
            }
            return "OT";
        } catch (Exception ignored) {
            return "OT";
        }
    }

    private com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistCreateResponse postToDicomServerWorklist(
            DicomServerWorklistCreateRequest payload,
            HospitalDicomServerResponse server
    ) {
        if (server == null) {
            throw new IllegalArgumentException("Active DICOM server routing is not configured for this Worklist.");
        }
        String worklistUrl = buildDicomServerWorklistUrl(server);
        return dicomServerClientService.postToDicomServerWorklist(worklistUrl, server.getUsername(), server.getPassword(), payload);
    }

    private HospitalDicomServerResponse resolveTargetDicomServer(WorklistDetailRow Worklist, Long modifiedBy) {
        if (Worklist == null || Worklist.getHospitalId() == null || Worklist.getId() == null) {
            return null;
        }

        HospitalDicomServerResponse targetServer =
                dicomServerMapper.findActiveDicomServerByWorklist(Worklist.getHospitalId(), Worklist.getId());
        if (targetServer != null) {
            return targetServer;
        }

        if (Worklist.getModalityId() != null && Worklist.getModalityId() > 0L) {
            List<HospitalModalityServerRouteResponse> routes =
                    dicomServerMapper.listActiveRoutesByHospitalAndModality(Worklist.getHospitalId(), Worklist.getModalityId());
            HospitalModalityServerRouteResponse selectedRoute = resolveRouteForSend(Worklist, null, routes);
            if (selectedRoute != null) {
                targetServer = resolveTargetDicomServerByRoute(selectedRoute);
                if (targetServer != null) {
                    backfillWorklistDicomRouteId(Worklist, selectedRoute.getId(), modifiedBy);
                    return targetServer;
                }
            }
        }

        if (hasText(Worklist.getMachineAeTitle())) {
            targetServer = dicomServerMapper.findActiveDicomServerByHospitalAndAeTitle(
                    Worklist.getHospitalId(),
                    Worklist.getMachineAeTitle().trim()
            );
            if (targetServer != null) {
                return targetServer;
            }
        }

        return null;
    }

    private HospitalDicomServerResponse resolveStudyDicomServer(StudyResponse study, Long hospitalId) {
        if (study != null && study.getDicomServerId() != null && study.getDicomServerId() > 0L) {
            List<HospitalDicomServerResponse> servers =
                    dicomServerMapper.getDicomServerById(study.getDicomServerId(), hospitalId);
            if (servers != null && !servers.isEmpty()) {
                return servers.get(0);
            }
        }
        return dicomServerMapper.findPrimaryActiveDicomServerByHospital(hospitalId);
    }

    private void backfillWorklistDicomRouteId(WorklistDetailRow Worklist, Long dicomRouteId, Long modifiedBy) {
        if (Worklist == null || dicomRouteId == null || dicomRouteId <= 0L || modifiedBy == null) {
            return;
        }
        if (Worklist.getDicomRouteId() != null && Worklist.getDicomRouteId().equals(dicomRouteId)) {
            return;
        }
        WorklistMapper.updateWorklistDicomRouteIdById(Worklist.getHospitalId(), Worklist.getId(), dicomRouteId, modifiedBy);
        Worklist.setDicomRouteId(dicomRouteId);
    }

    private DicomServerWorklistResponse getDicomServerWorklist(String worklistId, HospitalDicomServerResponse server) {
        if (server == null) {
            throw new IllegalArgumentException("Active DICOM server routing is not configured for this Worklist.");
        }
        return dicomServerClientService.getWorklistById(resolveDicomServerBaseUrl(server), server.getUsername(), server.getPassword(), worklistId);
    }

    private DicomServerWorklistResponse updateDicomServerWorklist(String worklistId, DicomServerWorklistCreateRequest payload, HospitalDicomServerResponse server) {
        if (server == null) {
            throw new IllegalArgumentException("Active DICOM server routing is not configured for this Worklist.");
        }
        return dicomServerClientService.updateWorklistById(resolveDicomServerBaseUrl(server), server.getUsername(), server.getPassword(), worklistId, payload);
    }

    private void deleteDicomServerWorklist(String worklistId, HospitalDicomServerResponse server) {
        if (server == null) {
            throw new IllegalArgumentException("Active DICOM server routing is not configured for this Worklist.");
        }
        dicomServerClientService.deleteWorklistById(resolveDicomServerBaseUrl(server), server.getUsername(), server.getPassword(), worklistId);
    }

    private HospitalModalityServerRouteResponse resolveRouteForSend(
            WorklistDetailRow Worklist,
            WorklistSendToPacsRequest request,
            List<HospitalModalityServerRouteResponse> routes
    ) {
        if (routes == null || routes.isEmpty()) {
            return null;
        }

        Long requestedRouteId = request == null
                ? null
                : publicEntityKeyResolver.resolve(Entity.DICOM_ROUTE, request.getRouteKey(), null);
        if (requestedRouteId != null && requestedRouteId > 0L) {
            for (HospitalModalityServerRouteResponse route : routes) {
                if (route != null && requestedRouteId.equals(route.getId())) {
                    return route;
                }
            }
            return null;
        }
        if (request != null && hasText(request.getRouteKey())) {
            return null;
        }

        Long requestedServerId = request == null
                ? null
                : publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, request.getDicomServerKey(), null);
        if (requestedServerId != null && requestedServerId > 0L) {
            List<HospitalModalityServerRouteResponse> matchingServerRoutes = routes.stream()
                    .filter(route -> route != null && requestedServerId.equals(route.getDicomServerId()))
                    .toList();
            if (matchingServerRoutes.size() == 1) {
                return matchingServerRoutes.get(0);
            }
            return null;
        }
        if (request != null && hasText(request.getDicomServerKey())) {
            return null;
        }

        if (Worklist != null && hasText(Worklist.getMachineAeTitle())) {
            for (HospitalModalityServerRouteResponse route : routes) {
                if (route != null
                        && hasText(route.getMachineAeTitle())
                        && route.getMachineAeTitle().equalsIgnoreCase(Worklist.getMachineAeTitle())) {
                    return route;
                }
            }
        }

        if (routes.size() == 1) {
            return routes.get(0);
        }
        return null;
    }

    private String machineRouteSelectionMessage(List<HospitalModalityServerRouteResponse> routes) {
        if (routes == null || routes.isEmpty()) {
            return "No active machine route is configured for this hospital and modality.";
        }
        return "Multiple active machine routes are configured for this modality. Please choose the target machine before sending to PACS.";
    }

    private HospitalDicomServerResponse resolveTargetDicomServerByRoute(HospitalModalityServerRouteResponse route) {
        if (route == null || route.getHospitalId() == null || route.getDicomServerId() == null) {
            return null;
        }
        List<HospitalDicomServerResponse> servers =
                dicomServerMapper.getDicomServerById(route.getDicomServerId(), route.getHospitalId());
        if (servers == null || servers.isEmpty()) {
            return null;
        }
        return servers.get(0);
    }

    private Long resolveDicomServerIdForWorklistAssign(Long requestedDicomServerId, List<HospitalModalityServerRouteResponse> routes) {
        if (requestedDicomServerId != null && requestedDicomServerId > 0) {
            if (routes == null || routes.isEmpty()) {
                return null;
            }
            for (HospitalModalityServerRouteResponse route : routes) {
                if (route != null && requestedDicomServerId.equals(route.getDicomServerId())) {
                    return requestedDicomServerId;
                }
            }
            return null;
        }

        if (routes == null || routes.isEmpty()) {
            return null;
        }

        Long singleServerId = null;
        for (HospitalModalityServerRouteResponse route : routes) {
            if (route == null || route.getDicomServerId() == null) {
                continue;
            }
            if (singleServerId == null) {
                singleServerId = route.getDicomServerId();
                continue;
            }
            if (!singleServerId.equals(route.getDicomServerId())) {
                return null;
            }
        }
        return singleServerId;
    }

    private Long resolveDicomServerIdForWorklistUpdate(Long existingDicomServerId, Long requestedDicomServerId, List<HospitalModalityServerRouteResponse> routes) {
        if (requestedDicomServerId != null && requestedDicomServerId > 0) {
            return resolveDicomServerIdForWorklistAssign(requestedDicomServerId, routes);
        }
        if (existingDicomServerId != null && existingDicomServerId > 0) {
            Long resolved = resolveDicomServerIdForWorklistAssign(existingDicomServerId, routes);
            if (resolved != null) {
                return resolved;
            }
        }
        return resolveDicomServerIdForWorklistAssign(null, routes);
    }

    private String buildDicomServerWorklistUrl(HospitalDicomServerResponse server) {
        String configuredBaseUrl = firstNonBlank(server == null ? null : server.getBaseUrl(), "");
        if (hasText(configuredBaseUrl)) {
            return normalizeWorklistUrl(configuredBaseUrl);
        }

        String ipAddress = firstNonBlank(server == null ? null : server.getIpAddress(), "");
        if (hasText(ipAddress)) {
            String protocol = ipAddress.startsWith("https://") ? "" : "http://";
            String host = ipAddress.replaceFirst("^https?://", "");
            Integer port = server == null ? null : server.getPort();
            String endpoint = protocol + host + (port != null && port > 0 ? ":" + port : "");
            return normalizeWorklistUrl(endpoint);
        }

        String uiBaseUrl = firstNonBlank(server == null ? null : server.getDicomServerUiBaseUrl(), "");
        if (hasText(uiBaseUrl)) {
            return normalizeWorklistUrl(uiBaseUrl);
        }

        throw new IllegalArgumentException("DICOM server base URL is not configured.");
    }

    private String resolveDicomServerBaseUrl(HospitalDicomServerResponse server) {
        String worklistUrl = buildDicomServerWorklistUrl(server);
        if (worklistUrl.endsWith("/worklists/create")) {
            return resolveInternalDicomServerBaseUrl(
                    server,
                    worklistUrl.substring(0, worklistUrl.length() - "/worklists/create".length())
            );
        }
        return resolveInternalDicomServerBaseUrl(server, worklistUrl);
    }

    private String resolveInternalDicomServerBaseUrl(HospitalDicomServerResponse server, String baseUrl) {
        return normalizePublicBaseUrl(baseUrl);
    }

    private String resolvePublicDicomServerUiBaseUrl(HospitalDicomServerResponse server) {
        if (server == null) {
            return null;
        }
        String serverBaseUrl = normalizePublicBaseUrl(server.getDicomServerUiBaseUrl());
        if (hasText(serverBaseUrl)) {
            return serverBaseUrl;
        }
        return normalizePublicBaseUrl(resolveDicomServerBaseUrl(server));
    }

    private String resolvePublicViewerBaseUrl(HospitalDicomServerResponse server) {
        return normalizePublicBaseUrl(server == null ? null : server.getViewerBaseUrl());
    }

    private String resolvePublicPacsApiBaseUrl(HospitalDicomServerResponse server, HttpServletRequest request) {
        String configuredBaseUrl = normalizePublicBaseUrl(server == null ? null : server.getPacsApiCallbackBaseUrl());
        if (hasText(configuredBaseUrl)) {
            return configuredBaseUrl;
        }
        return normalizePublicBaseUrl(buildRequestBaseUrl(request));
    }

    private String buildRequestBaseUrl(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String protocol = firstNonBlank(firstForwardedHeader(request, "X-Forwarded-Proto"), request.getScheme());
        String hostAndPort = firstNonBlank(firstForwardedHeader(request, "X-Forwarded-Host"), request.getHeader(HttpHeaders.HOST));
        if (!hasText(hostAndPort) && hasText(request.getServerName())) {
            hostAndPort = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && !isDefaultPort(protocol, port)) {
                hostAndPort = hostAndPort + ":" + port;
            }
        }
        if (!hasText(protocol) || !hasText(hostAndPort)) {
            return null;
        }

        String forwardedPrefix = normalizeContextPath(firstForwardedHeader(request, "X-Forwarded-Prefix"));
        String contextPath = hasText(forwardedPrefix)
                ? forwardedPrefix
                : normalizeContextPath(request.getContextPath());
        return protocol.trim().toLowerCase(Locale.ROOT) + "://" + hostAndPort.trim() + contextPath;
    }

    private String firstForwardedHeader(HttpServletRequest request, String name) {
        if (request == null || !hasText(name)) {
            return "";
        }
        String value = request.getHeader(name);
        if (!hasText(value)) {
            return "";
        }
        String[] parts = value.split(",", 2);
        return parts.length > 0 ? parts[0].trim() : "";
    }

    private String normalizeContextPath(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        if ("/".equals(normalized)) {
            return "";
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.replaceAll("/+$", "");
    }

    private boolean isDefaultPort(String protocol, int port) {
        String normalizedProtocol = protocol == null ? "" : protocol.trim().toLowerCase(Locale.ROOT);
        return ("http".equals(normalizedProtocol) && port == 80)
                || ("https".equals(normalizedProtocol) && port == 443);
    }

    private String resolvePublicDicomwebBaseUrl(HospitalDicomServerResponse server) {
        String viewerDicomwebProxyBaseUrl = appendPublicPath(resolvePublicViewerBaseUrl(server), VIEWER_DICOMWEB_PROXY_PATH);
        if (hasText(viewerDicomwebProxyBaseUrl)) {
            return viewerDicomwebProxyBaseUrl;
        }

        String configuredDicomwebBaseUrl = normalizePublicBaseUrl(server == null ? null : server.getDicomwebBaseUrl());
        String configuredViewerBaseUrl = normalizePublicBaseUrl(server == null ? null : server.getViewerBaseUrl());
        String internalServerBaseUrl = server == null ? null : normalizePublicBaseUrl(resolveDicomServerBaseUrl(server));
        boolean configuredLooksLikeViewerRoute = hasText(configuredViewerBaseUrl)
                && hasText(configuredDicomwebBaseUrl)
                && configuredDicomwebBaseUrl.startsWith(configuredViewerBaseUrl + "/");
        boolean configuredLooksLikeInternalRoute = hasText(internalServerBaseUrl)
                && hasText(configuredDicomwebBaseUrl)
                && configuredDicomwebBaseUrl.startsWith(internalServerBaseUrl + "/");

        if (hasText(configuredDicomwebBaseUrl) && !configuredLooksLikeViewerRoute && !configuredLooksLikeInternalRoute) {
            return configuredDicomwebBaseUrl;
        }
        String resolvedDicomServerUiBaseUrl = resolvePublicDicomServerUiBaseUrl(server);
        if (hasText(resolvedDicomServerUiBaseUrl)) {
            return appendPublicPath(resolvedDicomServerUiBaseUrl, DEFAULT_DICOMWEB_PATH);
        }

        if (hasText(configuredDicomwebBaseUrl) && !configuredLooksLikeViewerRoute) {
            return configuredDicomwebBaseUrl;
        }

        return null;
    }

    private String resolveInternalDicomwebBaseUrl(HospitalDicomServerResponse server) {
        if (server == null) {
            return null;
        }
        String internalServerBaseUrl = resolveDicomServerBaseUrl(server);
        if (hasText(internalServerBaseUrl)) {
            return appendPublicPath(internalServerBaseUrl, DEFAULT_DICOMWEB_PATH);
        }

        String configuredDicomwebBaseUrl = normalizePublicBaseUrl(server.getDicomwebBaseUrl());
        String configuredViewerBaseUrl = normalizePublicBaseUrl(server.getViewerBaseUrl());
        boolean configuredLooksLikeViewerGateway = hasText(configuredDicomwebBaseUrl)
                && ((hasText(configuredViewerBaseUrl) && configuredDicomwebBaseUrl.startsWith(configuredViewerBaseUrl + "/pacs-dicomweb/"))
                || configuredDicomwebBaseUrl.contains(ApiConstants.Worklist.BASE_PATH + "/viewer-dicom-web/"));
        if (hasText(configuredDicomwebBaseUrl) && !configuredLooksLikeViewerGateway) {
            return configuredDicomwebBaseUrl;
        }
        return null;
    }

    private String buildViewerDicomwebGatewayBaseUrl(
            String pacsApiBaseUrl,
            Long hospitalId,
            Long worklistId,
            String studyInstanceUid,
            String viewerToken
    ) {
        if (!hasText(pacsApiBaseUrl) || hospitalId == null || hospitalId <= 0L
                || worklistId == null || worklistId <= 0L || !hasText(studyInstanceUid)) {
            return null;
        }
        if (!hasText(viewerToken)) {
            return null;
        }
        return appendPublicPath(
                pacsApiBaseUrl,
                ApiConstants.Worklist.BASE_PATH + "/viewer-dicom-web/" + viewerToken.trim() + "/" + hospitalId + "/" + worklistId
        );
    }

    private String issueViewerDicomwebToken(Long hospitalId, Long worklistId, String studyInstanceUid) {
        return issueViewerDicomwebToken(hospitalId, worklistId, null, studyInstanceUid);
    }

    private String issueViewerDicomwebToken(Long hospitalId, Long worklistId, Long studyId, String studyInstanceUid) {
        if (jwtTokenService == null || hospitalId == null || hospitalId <= 0L
                || ((worklistId == null || worklistId <= 0L) && (studyId == null || studyId <= 0L))
                || !hasText(studyInstanceUid)) {
            return null;
        }
        AccessTokenResponse tokenResponse = jwtTokenService.issueViewerDicomwebToken(
                hospitalId,
                worklistId,
                studyId,
                studyInstanceUid.trim(),
                viewerDicomwebTokenMs
        );
        return tokenResponse == null ? null : tokenResponse.getAccessToken();
    }

    private String issueViewerApiKey(
            Long hospitalId,
            Long worklistId,
            Long studyId,
            Long modalityId,
            String studyInstanceUid,
            Long userId,
            String username,
            String accessMode
    ) {
        if (viewerAccessKeyService == null || hospitalId == null || hospitalId <= 0L || !hasText(studyInstanceUid)) {
            return null;
        }
        return viewerAccessKeyService.issue(
                hospitalId,
                worklistId,
                studyId,
                modalityId,
                studyInstanceUid,
                userId,
                username,
                accessMode
        );
    }

    private ViewerDicomwebTokenClaims validateViewerDicomwebToken(String viewerToken, Long hospitalId, Long worklistId) {
        ViewerDicomwebTokenClaims claims = decodeViewerDicomwebToken(viewerToken);
        if (!hospitalId.equals(claims.hospitalId()) || !worklistId.equals(claims.worklistId())) {
            throw new JwtException("Viewer DICOMweb token binding is not valid.");
        }
        return claims;
    }

    private ViewerDicomwebTokenClaims decodeViewerDicomwebToken(String viewerToken) {
        Jwt jwt = jwtDecoder.decode(extractBearerTokenValue(viewerToken));
        if (jwt == null) {
            throw new JwtException("Viewer DICOMweb token is not valid.");
        }
        String clientId = jwt.getClaimAsString("clientId");
        String scope = jwt.getClaimAsString("scope");
        String studyInstanceUid = jwt.getClaimAsString("studyInstanceUid");
        Long tokenHospitalId = readLongClaim(jwt, "hospitalId");
        Long tokenWorklistId = readLongClaim(jwt, "worklistId");
        Long tokenStudyId = readLongClaim(jwt, "studyId");
        String jti = jwt.getId();
        rejectRevokedViewerToken(jti);

        if (!VIEWER_DICOMWEB_CLIENT_ID.equals(clientId) || !hasScope(scope, VIEWER_DICOMWEB_SCOPE)) {
            throw new JwtException("Viewer DICOMweb token scope is not valid.");
        }
        if (tokenHospitalId == null || tokenHospitalId <= 0L
                || ((tokenWorklistId == null || tokenWorklistId <= 0L) && (tokenStudyId == null || tokenStudyId <= 0L))
                || !hasText(studyInstanceUid)) {
            throw new JwtException("Viewer DICOMweb token binding is not valid.");
        }
        return new ViewerDicomwebTokenClaims(tokenHospitalId, tokenWorklistId, tokenStudyId, studyInstanceUid.trim(), jti, jwt.getExpiresAt());
    }

    private static String extractBearerTokenValue(String tokenValue) {
        return RequestClientInfoHelper.extractBearerToken(tokenValue);
    }

    private String readViewerDicomwebTokenValue(HttpServletRequest request) {
        String tokenValue = firstNonBlank(
                request == null ? null : request.getHeader("X-PACS-DICOMWEB-TOKEN"),
                request == null ? null : request.getHeader(HttpHeaders.AUTHORIZATION),
                request == null ? null : request.getParameter(PARAM_TOKEN),
                request == null ? null : request.getParameter("viewerToken"),
                request == null ? null : request.getParameter("dicomwebToken")
        );
        if (!hasText(tokenValue)) {
            throw new IllegalArgumentException("Viewer token is required.");
        }
        return tokenValue;
    }

    private String readViewerLifecycleTokenValue(Map<String, Object> request) {
        String tokenValue = firstNonBlank(
                readStringValue(request, PARAM_TOKEN),
                readStringValue(request, "viewerToken"),
                readStringValue(request, "viewer_token"),
                readStringValue(request, "dicomwebToken"),
                readStringValue(request, "dicomweb_token"),
                readStringValue(request, AUTH_FIELD_TOKEN_VALUE_KEBAB),
                readStringValue(request, AUTH_FIELD_TOKEN_VALUE),
                readStringValue(request, AUTH_FIELD_AUTHORIZATION_LOWER),
                readStringValue(request, AUTH_FIELD_AUTHORIZATION)
        );
        if (!hasText(tokenValue)) {
            throw new IllegalArgumentException("Viewer token is required.");
        }
        return tokenValue;
    }

    private String readViewerAccessTokenValue(Map<String, Object> request) {
        return firstNonBlank(
                readStringValue(request, "viewerAccessToken"),
                readStringValue(request, "viewerApiKey"),
                readStringValue(request, "apiKey"),
                readStringValue(request, "viewer_access_token")
        );
    }

    private ViewerDicomwebTokenClaims resolveViewerRenewClaims(Map<String, Object> request) {
        try {
            return decodeViewerDicomwebToken(readViewerLifecycleTokenValue(request));
        } catch (JwtException | IllegalArgumentException tokenError) {
            String viewerAccessToken = readViewerAccessTokenValue(request);
            if (!hasText(viewerAccessToken) || viewerAccessKeyService == null) {
                throw tokenError;
            }
            ViewerAccessClaims accessClaims = viewerAccessKeyService.decode(viewerAccessToken);
            if (!ViewerAccessKeyService.canRead(accessClaims)
                    || accessClaims.hospitalId() == null || accessClaims.hospitalId() <= 0L
                    || ((accessClaims.worklistId() == null || accessClaims.worklistId() <= 0L)
                    && (accessClaims.studyId() == null || accessClaims.studyId() <= 0L))
                    || !hasText(accessClaims.studyInstanceUid())) {
                throw new JwtException("Viewer access token binding is not valid.");
            }
            return new ViewerDicomwebTokenClaims(
                    accessClaims.hospitalId(),
                    accessClaims.worklistId(),
                    accessClaims.studyId(),
                    accessClaims.studyInstanceUid(),
                    null,
                    null
            );
        }
    }

    private void appendRenewedViewerAccessToken(
            Map<String, Object> body,
            Map<String, Object> request,
            ViewerDicomwebTokenClaims dicomwebClaims
    ) {
        ViewerAccessClaims accessClaims = readViewerAccessClaimsForRenew(request);
        if (body == null || accessClaims == null || dicomwebClaims == null) {
            return;
        }
        if (!ViewerAccessKeyService.matchesScope(
                accessClaims,
                dicomwebClaims.hospitalId(),
                dicomwebClaims.worklistId(),
                dicomwebClaims.studyId(),
                null,
                dicomwebClaims.studyInstanceUid()
        )) {
            return;
        }

        String nextViewerAccessToken = issueViewerApiKey(
                accessClaims.hospitalId(),
                accessClaims.worklistId(),
                accessClaims.studyId(),
                accessClaims.modalityId(),
                accessClaims.studyInstanceUid(),
                accessClaims.userId(),
                accessClaims.username(),
                accessClaims.accessMode()
        );
        if (!hasText(nextViewerAccessToken)) {
            return;
        }

        body.put("viewerAccessToken", nextViewerAccessToken);
        body.put("viewerAccess", accessClaims.accessMode());
        ViewerEditCapabilities editCapabilities = resolveViewerEditCapabilities(accessClaims);
        body.put("canEditResult", editCapabilities.canEditResult());
        body.put("canEditViewerState", editCapabilities.canEditViewerState());
    }

    private ViewerAccessClaims readViewerAccessClaimsForRenew(Map<String, Object> request) {
        String viewerAccessToken = readViewerAccessTokenValue(request);
        if (!hasText(viewerAccessToken) || viewerAccessKeyService == null) {
            return null;
        }
        try {
            return viewerAccessKeyService.decode(viewerAccessToken);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void rejectRevokedViewerToken(String jti) {
        if (!hasText(jti) || revokedTokenMapper == null) {
            return;
        }
        Long count = revokedTokenMapper.countByJti(jti.trim());
        if (count != null && count > 0L) {
            throw new JwtException("Viewer DICOMweb token has been revoked.");
        }
    }

    private void revokeViewerToken(ViewerDicomwebTokenClaims claims) {
        if (claims == null || !hasText(claims.jti()) || revokedTokenMapper == null) {
            return;
        }
        Instant expiresAt = claims.expiresAt();
        LocalDateTime tokenExpiresAt = expiresAt != null
                ? LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
                : LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30);
        revokedTokenMapper.revokeToken(claims.jti().trim(), currentUserId(), tokenExpiresAt);
    }

    private HospitalDicomServerResponse findAuthorizedDicomServerHttpClient(Map<String, Object> request) {
        String tokenValue = firstNonBlank(
                readStringValue(request, AUTH_FIELD_TOKEN_VALUE_KEBAB),
                readStringValue(request, AUTH_FIELD_TOKEN_VALUE),
                readStringValue(request, AUTH_FIELD_AUTHORIZATION_LOWER),
                readStringValue(request, AUTH_FIELD_AUTHORIZATION)
        );
        BasicCredential credential = parseBasicCredential(tokenValue);
        if (credential == null || !hasText(credential.username()) || !hasText(credential.password())) {
            return null;
        }
        List<HospitalDicomServerResponse> candidates =
                dicomServerMapper.listActiveDicomServersByHttpUsername(credential.username().trim());
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        String requestedServerId = firstNonBlank(
                readStringValue(request, "server-id"),
                readStringValue(request, "serverId"),
                readStringValue(request, "server_id")
        );
        for (HospitalDicomServerResponse candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (!credential.username().trim().equalsIgnoreCase(candidate.getUsername() == null ? "" : candidate.getUsername().trim())) {
                continue;
            }
            if (!credential.password().equals(firstNonBlank(candidate.getPassword(), ""))) {
                continue;
            }
            if (hasText(requestedServerId) && !matchesArchiveAuthorizationName(requestedServerId, candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static BasicCredential parseBasicCredential(String tokenValue) {
        if (!hasText(tokenValue)) {
            return null;
        }
        String value = tokenValue.trim();
        if (!value.regionMatches(true, 0, BASIC_AUTH_PREFIX, 0, BASIC_AUTH_PREFIX.length())) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value.substring(BASIC_AUTH_PREFIX.length()).trim());
            String credential = new String(decoded, StandardCharsets.UTF_8);
            int separator = credential.indexOf(':');
            if (separator <= 0) {
                return null;
            }
            return new BasicCredential(
                    credential.substring(0, separator),
                    credential.substring(separator + 1)
            );
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static boolean isBasicCredentialToken(String tokenValue) {
        return hasText(tokenValue) && tokenValue.trim().regionMatches(true, 0, BASIC_AUTH_PREFIX, 0, BASIC_AUTH_PREFIX.length());
    }

    private static boolean matchesArchiveAuthorizationName(String requestedServerId, HospitalDicomServerResponse server) {
        String requested = toSlug(requestedServerId).replace('-', '_');
        return requested.equals(buildDicomServerAuthorizationName(server))
                || requested.equals(buildLegacyDicomServerAuthorizationName(server));
    }

    private static String buildDicomServerAuthorizationName(HospitalDicomServerResponse server) {
        String serverName = firstNonBlank(
                server == null ? null : server.getName(),
                server == null ? null : server.getAeTitle(),
                "server"
        );
        String legacyCompactBrand = "udaya" + "pacs";
        String legacySlugBrand = "udaya-" + "pacs";
        String suffix = toSlug(serverName)
                .replace(legacyCompactBrand, "dicom_server")
                .replace(legacySlugBrand, "dicom_server")
                .replace("dicomserver", "dicom_server")
                .replace("dicom-server", "dicom_server")
                .replace("udayadicomserver", "dicom_server")
                .replace("udaya-dicom-server", "dicom_server")
                .replace('-', '_');
        if (suffix.startsWith("dicom_server_")) {
            suffix = suffix.substring("dicom_server_".length());
        }
        if (!hasText(suffix)) {
            suffix = "server";
        }
        return "dicom_server_" + suffix;
    }

    private static String buildLegacyDicomServerAuthorizationName(HospitalDicomServerResponse server) {
        String serverName = firstNonBlank(
                server == null ? null : server.getName(),
                server == null ? null : server.getAeTitle(),
                "udaya_dicom_server"
        );
        String suffix = toSlug(serverName)
                .replace("dicomserver", "udaya_dicom_server")
                .replace("dicom-server", "udaya_dicom_server")
                .replace("udayadicomserver", "udaya_dicom_server")
                .replace("udaya-dicom-server", "udaya_dicom_server")
                .replace('-', '_');
        if (suffix.startsWith("udaya_dicom_server_")) {
            suffix = suffix.substring("udaya_dicom_server_".length());
        }
        if (!hasText(suffix)) {
            suffix = "server";
        }
        return "udaya_dicom_server_" + suffix;
    }

    private static String toSlug(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-+", "-");
    }

    private static Long readLongClaim(Jwt jwt, String claimName) {
        if (jwt == null || claimName == null) {
            return null;
        }
        Object value = jwt.getClaim(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean hasScope(String scope, String expectedScope) {
        if (!hasText(scope) || !hasText(expectedScope)) {
            return false;
        }
        for (String token : scope.trim().split("\\s+")) {
            if (expectedScope.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private String buildViewerDicomwebPathAndQuery(
            HttpServletRequest request,
            String viewerToken,
            Long hospitalId,
            Long worklistId,
            String studyInstanceUid
    ) {
        String path = extractViewerDicomwebPath(request, viewerToken, hospitalId, worklistId);
        String normalizedPath = normalizeViewerDicomwebPath(path, studyInstanceUid);
        String query = request == null ? null : request.getQueryString();
        String normalizedQuery = normalizeViewerDicomwebQuery(normalizedPath, query, studyInstanceUid);
        return normalizedPath + (hasText(normalizedQuery) ? "?" + normalizedQuery : "");
    }

    private String buildViewerDicomwebProxyPathAndQuery(
            HttpServletRequest request,
            String studyInstanceUid
    ) {
        String path = extractViewerDicomwebProxyPath(request);
        String normalizedPath = normalizeViewerDicomwebPath(path, studyInstanceUid);
        String query = request == null ? null : request.getQueryString();
        String normalizedQuery = normalizeViewerDicomwebQuery(normalizedPath, query, studyInstanceUid);
        return normalizedPath + (hasText(normalizedQuery) ? "?" + normalizedQuery : "");
    }

    private String extractViewerDicomwebPath(
            HttpServletRequest request,
            String viewerToken,
            Long hospitalId,
            Long worklistId
    ) {
        if (request == null || !hasText(request.getRequestURI())) {
            throw new IllegalArgumentException(WorklistConstants.MSG_VIEWER_DICOMWEB_PATH_REQUIRED);
        }
        String marker = ApiConstants.Worklist.BASE_PATH
                + "/viewer-dicom-web/"
                + viewerToken
                + "/"
                + hospitalId
                + "/"
                + worklistId;
        String requestUri = request.getRequestURI();
        int markerIndex = requestUri.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalArgumentException("Viewer DICOMweb path is invalid.");
        }
        String path = requestUri.substring(markerIndex + marker.length());
        if (!hasText(path) || "/".equals(path.trim())) {
            throw new IllegalArgumentException("Viewer DICOMweb resource path is required.");
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String extractViewerDicomwebProxyPath(HttpServletRequest request) {
        if (request == null || !hasText(request.getRequestURI())) {
            throw new IllegalArgumentException(WorklistConstants.MSG_VIEWER_DICOMWEB_PATH_REQUIRED);
        }
        String marker = ApiConstants.Worklist.BASE_PATH + "/viewer-dicom-web-proxy";
        String requestUri = request.getRequestURI();
        int markerIndex = requestUri.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalArgumentException("Viewer DICOMweb proxy path is invalid.");
        }
        String path = requestUri.substring(markerIndex + marker.length());
        if (!hasText(path) || "/".equals(path.trim())) {
            throw new IllegalArgumentException("Viewer DICOMweb resource path is required.");
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String normalizeViewerDicomwebPath(String path, String studyInstanceUid) {
        if (!hasText(path)) {
            throw new IllegalArgumentException(WorklistConstants.MSG_VIEWER_DICOMWEB_PATH_REQUIRED);
        }
        String normalizedPath = path.trim();
        if (normalizedPath.contains("\\") || normalizedPath.contains("..")) {
            throw new SecurityException("Unsafe DICOMweb path.");
        }
        if ("/studies".equals(normalizedPath) || "/studies/".equals(normalizedPath)) {
            return "/studies";
        }
        if (!normalizedPath.startsWith("/studies/")) {
            throw new SecurityException("Viewer DICOMweb token is limited to one study.");
        }

        String remainder = normalizedPath.substring("/studies/".length());
        String pathStudyUid = remainder.contains("/") ? remainder.substring(0, remainder.indexOf('/')) : remainder;
        String decodedStudyUid = decodePathSegment(pathStudyUid);
        if (!studyInstanceUid.equals(decodedStudyUid)) {
            throw new SecurityException("Requested study does not match the viewer token.");
        }
        return normalizedPath;
    }

    private String normalizeViewerDicomwebQuery(String path, String rawQuery, String studyInstanceUid) {
        String safeQuery = stripViewerTokenQueryParameters(rawQuery);
        if (!"/studies".equals(path)) {
            return safeQuery;
        }
        String studyParam = "StudyInstanceUID=" + URLEncoder.encode(studyInstanceUid, StandardCharsets.UTF_8);
        if (!hasText(safeQuery)) {
            return studyParam;
        }

        String[] params = safeQuery.split("&");
        boolean foundStudyParam = false;
        for (String param : params) {
            String[] parts = param.split("=", 2);
            String name = decodePathSegment(parts[0]);
            if (DicomTagConstants.STUDY_INSTANCE_UID.equals(name)) {
                foundStudyParam = true;
                String value = parts.length > 1 ? decodePathSegment(parts[1]) : "";
                if (!studyInstanceUid.equals(value)) {
                    throw new SecurityException("Requested study query does not match the viewer token.");
                }
            }
        }
        return foundStudyParam ? safeQuery : safeQuery + "&" + studyParam;
    }

    private String stripViewerTokenQueryParameters(String rawQuery) {
        if (!hasText(rawQuery)) {
            return rawQuery;
        }
        return Arrays.stream(rawQuery.split("&"))
                .filter(param -> {
                    if (!hasText(param)) {
                        return false;
                    }
                    String[] parts = param.split("=", 2);
                    String name = decodePathSegment(parts[0]).trim();
                    return !Set.of(PARAM_TOKEN, "viewerToken", "dicomwebToken").contains(name);
                })
                .collect(Collectors.joining("&"));
    }

    private void validateViewerDicomwebProxyRequest(String originalUri, String method, String studyInstanceUid) {
        if (!hasText(originalUri) || !hasText(studyInstanceUid)) {
            throw new SecurityException("Viewer DICOMweb proxy request is not bound to a study.");
        }
        String normalizedMethod = firstNonBlank(method, HTTP_METHOD_GET).toUpperCase(Locale.ROOT);
        if (!HTTP_METHOD_GET.equals(normalizedMethod) && !"HEAD".equals(normalizedMethod) && !"OPTIONS".equals(normalizedMethod)) {
            throw new SecurityException("Viewer DICOMweb proxy request method is not allowed.");
        }

        URI uri = URI.create(originalUri.startsWith("http://") || originalUri.startsWith("https://")
                ? originalUri
                : "http://viewer" + (originalUri.startsWith("/") ? originalUri : "/" + originalUri));
        String path = firstNonBlank(uri.getPath(), "");
        if (!path.equals(VIEWER_DICOMWEB_PROXY_PATH) && !path.startsWith(VIEWER_DICOMWEB_PROXY_PATH + "/")) {
            throw new SecurityException("Viewer DICOMweb proxy path is not allowed.");
        }

        String dicomwebPath = path.substring(VIEWER_DICOMWEB_PROXY_PATH.length());
        if (!hasText(dicomwebPath)) {
            dicomwebPath = "/";
        }
        String normalizedPath = normalizeViewerDicomwebPath(dicomwebPath, studyInstanceUid.trim());
        if ("/studies".equals(normalizedPath)
                && !queryContainsStudyInstanceUid(uri.getRawQuery(), studyInstanceUid.trim())) {
            throw new SecurityException("Viewer DICOMweb study query is required.");
        }
    }

    private static boolean queryContainsStudyInstanceUid(String rawQuery, String studyInstanceUid) {
        if (!hasText(rawQuery) || !hasText(studyInstanceUid)) {
            return false;
        }
        boolean sawStudyFilter = false;
        for (String param : rawQuery.split("&")) {
            if (!hasText(param)) {
                continue;
            }
            String[] parts = param.split("=", 2);
            String name = decodePathSegment(parts[0]);
            if (!DicomTagConstants.STUDY_INSTANCE_UID.equals(name) && !"StudyInstanceUIDs".equals(name)) {
                continue;
            }
            sawStudyFilter = true;
            String value = parts.length > 1 ? decodePathSegment(parts[1]) : "";
            if (studyInstanceUid.equals(value)) {
                return true;
            }
        }
        if (sawStudyFilter) {
            throw new SecurityException("Requested study query does not match the viewer token.");
        }
        return false;
    }

    private static String decodePathSegment(String value) {
        if (value == null) {
            return "";
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String safeUrlDecode(String value) {
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static String readStringValue(Map<String, Object> request, String key) {
        if (request == null || key == null) {
            return null;
        }
        Object value = request.get(key);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static String findFirstStringByKey(Object node, String key) {
        if (node == null || key == null) {
            return null;
        }
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && key.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                    Object value = entry.getValue();
                    if (value != null && !(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
                        return String.valueOf(value);
                    }
                }
            }
            for (Object value : map.values()) {
                String nested = findFirstStringByKey(value, key);
                if (hasText(nested)) {
                    return nested;
                }
            }
        }
        if (node instanceof List<?> values) {
            for (Object value : values) {
                String nested = findFirstStringByKey(value, key);
                if (hasText(nested)) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static boolean viewerDicomwebUriMatchesStudy(String uri, String studyInstanceUid, boolean requestStudyUidMatches) {
        if (!hasText(studyInstanceUid)) {
            return false;
        }
        if (!hasText(uri)) {
            return requestStudyUidMatches;
        }
        String decoded = safeUrlDecode(uri);
        String value = hasText(decoded) ? decoded : uri;
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.contains("/dicom-web")) {
            return false;
        }
        return value.contains(studyInstanceUid.trim()) || requestStudyUidMatches;
    }

    private static boolean viewerDicomwebIsStudyFindRequest(String uri, boolean requestStudyUidMatches) {
        if (!requestStudyUidMatches || !hasText(uri)) {
            return false;
        }
        String decoded = safeUrlDecode(uri);
        String value = hasText(decoded) ? decoded : uri;
        String normalized = value.toLowerCase(Locale.ROOT);
        return "/tools/find".equals(normalized) || normalized.endsWith("/tools/find");
    }

    private record ViewerDicomwebTokenClaims(
            Long hospitalId,
            Long worklistId,
            Long studyId,
            String studyInstanceUid,
            String jti,
            Instant expiresAt
    ) {
    }

    private record BasicCredential(String username, String password) {
    }

    private String normalizePublicBaseUrl(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("/+$", "");
    }

    private String appendPublicPath(String baseUrl, String path) {
        String normalizedBaseUrl = normalizePublicBaseUrl(baseUrl);
        String normalizedPath = hasText(path) ? path.trim() : "";
        if (!hasText(normalizedBaseUrl)) {
            return null;
        }
        if (!hasText(normalizedPath)) {
            return normalizedBaseUrl;
        }
        return normalizedBaseUrl + (normalizedPath.startsWith("/") ? normalizedPath : "/" + normalizedPath);
    }

    private String normalizeWorklistUrl(String endpoint) {
        String normalized = endpoint == null ? "" : endpoint.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        if (normalized.endsWith("/worklists/create")) {
            return normalized;
        }
        if (normalized.endsWith("/")) {
            return normalized + "worklists/create";
        }
        return normalized + "/worklists/create";
    }

    private String resolveStudyDescription(String requestedStudyDescription, WorklistDetailRow Worklist, Long modalityId) {
        if (hasText(requestedStudyDescription)) {
            return requestedStudyDescription.trim();
        }
        if (Worklist != null && hasText(Worklist.getStudyDescription())) {
            return Worklist.getStudyDescription().trim();
        }
        return DicomServerWorklistMapperHelper.defaultStudyDescription(null, resolveModalityName(modalityId, Worklist == null ? null : Worklist.getModalityName()));
    }


    private LocalDate resolveScheduledDate(LocalDate requestedScheduledDate, LocalDate currentScheduledDate) {
        if (requestedScheduledDate != null) {
            return requestedScheduledDate;
        }
        if (currentScheduledDate != null) {
            return currentScheduledDate;
        }
        return LocalDate.now();
    }

    private LocalTime resolveScheduledTime(LocalTime requestedScheduledTime, LocalTime currentScheduledTime) {
        if (requestedScheduledTime != null) {
            return requestedScheduledTime.withNano(0);
        }
        if (currentScheduledTime != null) {
            return currentScheduledTime.withNano(0);
        }
        return LocalTime.now().plusMinutes(5).withSecond(0).withNano(0);
    }

    private static String buildAccessionNumber(WorklistDetailRow Worklist) {
        if (Worklist == null) {
            return null;
        }
        if (hasText(Worklist.getAccessionNumber())) {
            return Worklist.getAccessionNumber().trim();
        }
        return firstNonBlank(Worklist.getVisitCode(), "VISIT");
    }

    private void validateWorklistModalityForHospital(Long hospitalId, Long modalityId) {
        if (modalityId == null || modalityId <= 0L) {
            throw new IllegalArgumentException("modalityId is required.");
        }
        Long activeModality = modalityMapper.countActiveModalitiesByIds(List.of(modalityId));
        if (activeModality == null || activeModality <= 0L) {
            throw new IllegalArgumentException("Modality not found or inactive.");
        }
    }

    private String resolveScheduledStationAeTitle(
            WorklistDetailRow Worklist,
            Long modalityId,
            HospitalDicomServerResponse targetDicomServer
    ) {
        // Once a worklist exists, keep using the saved Worklist AE title/fallback.
        // Do not depend on current routing rows because routes may be changed later.
        if (Worklist != null && hasText(Worklist.getDicomServerWorklistId())) {
            return normalizeScheduledStationAeTitle(firstNonBlank(
                    Worklist.getMachineAeTitle(),
                    targetDicomServer == null ? null : targetDicomServer.getAeTitle(),
                    DEFAULT_DICOM_AE_TITLE
            ), Worklist, targetDicomServer);
        }

        if (Worklist != null && Worklist.getHospitalId() != null && modalityId != null && modalityId > 0L) {
            Long targetServerId = targetDicomServer == null ? Worklist.getDicomServerId() : targetDicomServer.getId();
            try {
                List<HospitalModalityServerRouteResponse> routes =
                        dicomServerMapper.listActiveRoutesByHospitalAndModality(Worklist.getHospitalId(), modalityId);
                if (routes != null && !routes.isEmpty()) {
                    for (HospitalModalityServerRouteResponse route : routes) {
                        if (route == null) {
                            continue;
                        }
                        boolean sameServer = targetServerId == null
                                || route.getDicomServerId() == null
                                || targetServerId.equals(route.getDicomServerId());
                        if (sameServer && hasText(route.getMachineAeTitle())) {
                            String routeAeTitle = normalizeScheduledStationAeTitle(route.getMachineAeTitle(), Worklist, targetDicomServer);
                            if (hasText(routeAeTitle)) {
                                return routeAeTitle;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fallback to global AE title if routing metadata cannot be loaded.
            }
        }
        return normalizeScheduledStationAeTitle(firstNonBlank(
                Worklist == null ? null : Worklist.getMachineAeTitle(),
                targetDicomServer == null ? null : targetDicomServer.getAeTitle(),
                DEFAULT_DICOM_AE_TITLE
        ), Worklist, targetDicomServer);
    }

    private String normalizeScheduledStationAeTitle(
            String aeTitle,
            WorklistDetailRow Worklist,
            HospitalDicomServerResponse targetDicomServer
    ) {
        String normalizedAeTitle = firstNonBlank(aeTitle, DEFAULT_DICOM_AE_TITLE).trim();
        if (("MODALITY" + "SIM").equalsIgnoreCase(normalizedAeTitle)) {
            return DEFAULT_DICOM_AE_TITLE;
        }
        return normalizedAeTitle.toUpperCase(Locale.ROOT);
    }

    private void validateWorklistModalityAgainstDicomServer(
            WorklistDetailRow Worklist,
            Long hospitalId,
            Long modalityId,
            HospitalDicomServerResponse server
    ) {
        if (server == null || modalityId == null || modalityId <= 0L) {
            return;
        }
        // After a Worklist is already sent to PACS, the existing DicomServer worklist is
        // already bound to a concrete server. Allow modality updates to keep using
        // that same target even if current routing rows were changed or removed.
        if (Worklist != null && hasText(Worklist.getDicomServerWorklistId())) {
            return;
        }
        List<HospitalModalityServerRouteResponse> routes =
                dicomServerMapper.listActiveRoutesByHospitalAndModality(hospitalId, modalityId);
        Long resolvedServerId = resolveDicomServerIdForWorklistAssign(server.getId(), routes);
        if (resolvedServerId == null) {
            throw new IllegalArgumentException("Selected modality is not routed to the current DICOM server.");
        }
    }

    private String resolveModalityName(Long modalityId, String fallbackName) {
        if (modalityId == null || modalityId <= 0L) {
            return firstNonBlank(fallbackName, "");
        }
        try {
            List<ModalityResponse> rows = modalityMapper.getModalityById(modalityId);
            if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
                return firstNonBlank(rows.get(0).getName(), fallbackName, "");
            }
        } catch (Exception ignored) {
            // Best-effort label resolution only.
        }
        return firstNonBlank(fallbackName, "");
    }

    private WorklistDetailRow syncWorklistDetailFromDicomServerIfNeeded(WorklistDetailRow Worklist, Long modifiedBy) {
        if (Worklist == null) {
            return null;
        }
        WorklistStatus currentStatus = safeWorklistStatus(Worklist.getStatus());
        if (currentStatus != WorklistStatus.IN_PROGRESS || !hasText(Worklist.getDicomServerWorklistId())) {
            return Worklist;
        }
        try {
            HospitalDicomServerResponse targetDicomServer = resolveTargetDicomServer(Worklist, modifiedBy);
            DicomServerWorklistResponse worklist = getDicomServerWorklist(Worklist.getDicomServerWorklistId(), targetDicomServer);
            syncWorklistFromWorklist(Worklist, worklist, modifiedBy);
            WorklistDetailRow refreshedWorklist = WorklistMapper.findWorklistById(Worklist.getHospitalId(), Worklist.getId());
            return refreshedWorklist == null ? Worklist : refreshedWorklist;
        } catch (RestClientException error) {
            LOGGER.warn(
                    "Worklist-find sync skipped for worklistId={} worklistId={} because DicomServer read failed: {}",
                    Worklist.getId(),
                    Worklist.getDicomServerWorklistId(),
                    error.getMessage()
            );
            return Worklist;
        } catch (Exception error) {
            LOGGER.warn(
                    "Worklist-find sync skipped for worklistId={} worklistId={} because of an unexpected error.",
                    Worklist.getId(),
                    Worklist.getDicomServerWorklistId(),
                    error
            );
            return Worklist;
        }
    }

    private void syncWorklistFromWorklist(WorklistDetailRow Worklist, DicomServerWorklistResponse worklist, Long modifiedBy) {
        if (Worklist == null || worklist == null) {
            return;
        }
        SyncedWorklistFields synced = DicomServerWorklistMapperHelper.toSyncedWorklistFields(worklist, Worklist, firstNonBlank(Worklist.getMachineAeTitle(), DEFAULT_DICOM_AE_TITLE));
        WorklistMapper.updateWorklistDicomWorklistFieldsById(
                Worklist.getHospitalId(),
                Worklist.getId(),
                Worklist.getModalityId(),
                firstNonBlank(synced.getAccessionNumber(), buildAccessionNumber(Worklist)),
                firstNonBlank(synced.getModalityCode(), Worklist.getModalityCode(), DicomServerWorklistMapperHelper.normalizeModality(Worklist.getModalityName())),
                firstNonBlank(synced.getScheduledStationAeTitle(), Worklist.getMachineAeTitle(), DEFAULT_DICOM_AE_TITLE),
                firstNonBlank(synced.getStudyDescription(), Worklist.getStudyDescription(), DicomServerWorklistMapperHelper.defaultStudyDescription(null, Worklist.getModalityName())),
                synced.getScheduledDate() != null ? synced.getScheduledDate() : resolveScheduledDate(Worklist.getScheduledDate(), null),
                synced.getScheduledTime() != null ? synced.getScheduledTime() : resolveScheduledTime(Worklist.getScheduledTime(), null),
                firstNonBlank(synced.getDicomServerWorklistId(), Worklist.getDicomServerWorklistId()),
                firstNonBlank(synced.getDicomServerWorklistPath(), Worklist.getDicomServerWorklistPath()),
                modifiedBy
        );
    }

    private WorklistDetailRow syncWorklistStudyResultIfAvailable(WorklistDetailRow Worklist, Long modifiedBy) {
        if (Worklist == null || !hasText(Worklist.getAccessionNumber())) {
            return Worklist;
        }

        WorklistStatus currentStatus = safeWorklistStatus(Worklist.getStatus());
        if (currentStatus != WorklistStatus.IN_PROGRESS) {
            return Worklist;
        }

        try {
            HospitalDicomServerResponse targetDicomServer = resolveTargetDicomServer(Worklist, modifiedBy);
            String studyId = findDicomServerStudyIdByAccessionNumber(Worklist.getAccessionNumber(), targetDicomServer);
            if (!hasText(studyId)) {
                return Worklist;
            }
            DicomServerStudyResponse studyResponse = getDicomServerStudy(studyId, targetDicomServer);
            if (studyResponse == null) {
                return Worklist;
            }

            String receivedAtIso = OffsetDateTime.now().toString();
            LinkedStudyContext linkedStudy = buildLinkedStudyContext(
                    Worklist,
                    studyId,
                    studyResponse,
                    targetDicomServer,
                    modifiedBy,
                    receivedAtIso
            );

            int updated = WorklistMapper.updateWorklistReceivedById(
                    Worklist.getHospitalId(),
                    Worklist.getId(),
                    linkedStudy.studyId(),
                    WorklistStatus.IN_PROGRESS.code(),
                    modifiedBy,
                    receivedAtIso
            );
            if (updated > 0) {
                persistWorklistStudyLink(Worklist, linkedStudy, modifiedBy);
                WorklistMapper.insertHistory(
                        Worklist.getHospitalId(),
                        Worklist.getId(),
                        Worklist.getPatientId(),
                        currentStatus.code(),
                        WorklistStatus.IN_PROGRESS.code(),
                        WorklistConstants.ACTION_SYNC_PACS_RESULT,
                        "Synced by accessionNumber=" + Worklist.getAccessionNumber(),
                        modifiedBy
                );
                WorklistDetailRow refreshedWorklist = WorklistMapper.findWorklistById(Worklist.getHospitalId(), Worklist.getId());
                return refreshedWorklist == null ? Worklist : refreshedWorklist;
            }
            return Worklist;
        } catch (RestClientException error) {
            LOGGER.warn(
                    "Worklist sync-result skipped for worklistId={} accessionNumber={} because DicomServer lookup failed: {}",
                    Worklist.getId(),
                    Worklist.getAccessionNumber(),
                    error.getMessage()
            );
            return Worklist;
        } catch (Exception error) {
            LOGGER.warn(
                    "Worklist sync-result skipped for worklistId={} accessionNumber={} because of an unexpected error.",
                    Worklist.getId(),
                    Worklist.getAccessionNumber(),
                    error
            );
            return Worklist;
        }
    }

    private LinkedStudyContext buildCallbackLinkedStudyContext(
            WorklistDetailRow Worklist,
            WorklistReceivedStudyRequest request,
            HospitalDicomServerResponse server,
            DicomServerStudyResponse verifiedStudy,
            String receivedAtIso
    ) {
        if (Worklist == null) {
            return new LinkedStudyContext(null, "", "", "", "", "");
        }
        Map<String, Object> studyTags = verifiedStudy != null && verifiedStudy.getMainDicomTags() != null
                ? verifiedStudy.getMainDicomTags()
                : Collections.emptyMap();
        Map<String, Object> patientTags = verifiedStudy != null && verifiedStudy.getPatientMainDicomTags() != null
                ? verifiedStudy.getPatientMainDicomTags()
                : Collections.emptyMap();

        String dicomServerStudyId = firstNonBlank(
                verifiedStudy == null ? null : verifiedStudy.getId(),
                normalizedOrEmpty(request == null ? null : request.getDicomServerStudyId()),
                Worklist.getDicomServerStudyId()
        );
        String studyInstanceUid = firstNonBlank(
                readDicomTag(studyTags, DicomTagConstants.STUDY_INSTANCE_UID),
                normalizedOrEmpty(request == null ? null : request.getStudyInstanceUid()),
                normalizedOrEmpty(request == null ? null : request.getStudyUuid()),
                Worklist.getStudyInstanceUid(),
                Worklist.getStudyUuid(),
                dicomServerStudyId
        );
        String dicomServerPatientId = firstNonBlank(
                verifiedStudy == null ? null : verifiedStudy.getParentPatient(),
                normalizedOrEmpty(request == null ? null : request.getDicomServerPatientId()),
                Worklist.getDicomServerPatientId()
        );
        String dicomServerSeriesId = firstNonBlank(
                verifiedStudy != null && verifiedStudy.getSeries() != null && !verifiedStudy.getSeries().isEmpty()
                        ? verifiedStudy.getSeries().get(0)
                        : null,
                firstDicomServerSeriesId(request == null ? null : request.getDicomServerSeriesIds()),
                Worklist.getDicomServerSeriesId()
        );
        String viewerUrl = hasText(dicomServerStudyId)
                ? buildViewerUrl(dicomServerStudyId, server)
                : firstNonBlank(Worklist.getViewerUrl(), "");
        String patientHn = firstNonBlank(
                readDicomTag(patientTags, DicomTagConstants.PATIENT_ID),
                normalizedOrEmpty(request == null ? null : request.getPatientId())
        );
        syncPatientHnFromDicom(Worklist, patientHn);
        String institutionName = firstNonBlank(
                readDicomTag(studyTags, DicomTagConstants.INSTITUTION_NAME),
                normalizedOrEmpty(request == null ? null : request.getInstitutionName()),
                Worklist.getInstitutionName()
        );

        Long studyId = null;
        if (hasText(studyInstanceUid)) {
            studyId = studyMapper.upsertFromWorklist(
                    Worklist.getHospitalId(),
                    Worklist.getPatientId(),
                    studyInstanceUid,
                    resolveStudyAccessionNumberForCallback(Worklist, request, studyTags),
                    Worklist.getModalityId(),
                    firstNonBlank(Worklist.getModalityCode(), Worklist.getModalityName()),
                    parseDicomStudyDate(firstNonBlank(readDicomTag(studyTags, DicomTagConstants.STUDY_DATE), normalizedOrEmpty(request == null ? null : request.getStudyDate()))),
                    firstNonBlank(readDicomTag(studyTags, DicomTagConstants.STUDY_DESCRIPTION), normalizedOrEmpty(request == null ? null : request.getStudyDescription()), Worklist.getStudyDescription()),
                    institutionName,
                    resolveStudyDicomServerId(Worklist, server),
                    StudyStatus.IMAGE_RECEIVED.code(),
                    dicomServerStudyId,
                    dicomServerPatientId,
                    dicomServerSeriesId,
                    resolveStudyInstanceCount(dicomServerStudyId, verifiedStudy, server),
                    receivedAtIso
            );
        }

        return new LinkedStudyContext(studyId, dicomServerStudyId, studyInstanceUid, dicomServerPatientId, dicomServerSeriesId, viewerUrl);
    }

    private DicomServerStudyResponse resolveVerifiedCallbackStudy(
            WorklistDetailRow Worklist,
            WorklistReceivedStudyRequest request,
            HospitalDicomServerResponse server
    ) {
        if (Worklist == null) {
            throw new IllegalArgumentException(WorklistConstants.MSG_WORKLIST_NOT_FOUND);
        }
        if (server == null) {
            throw new IllegalArgumentException("Active DICOM server routing is not configured for this Worklist.");
        }

        String requestedDicomServerStudyId = normalizedOrEmpty(request == null ? null : request.getDicomServerStudyId());
        String requestedStudyInstanceUid = firstNonBlank(
                normalizedOrEmpty(request == null ? null : request.getStudyInstanceUid()),
                normalizedOrEmpty(request == null ? null : request.getStudyUuid())
        );
        String requestedAccessionNumber = normalizedOrEmpty(request == null ? null : request.getAccessionNumber());
        if (hasText(requestedAccessionNumber) && !isCallbackAccessionAllowedForWorklist(Worklist, requestedAccessionNumber)) {
            throw new IllegalArgumentException("Callback accession does not match this Worklist.");
        }
        String expectedAccessionNumber = resolveStudyAccessionNumberForCallback(Worklist, request, null);
        Integer callbackInstanceCount = request == null ? null : request.getImageInstanceCount();
        if (callbackInstanceCount != null && callbackInstanceCount > 0 && hasText(requestedStudyInstanceUid)) {
            DicomServerStudyResponse callbackStudy = new DicomServerStudyResponse();
            callbackStudy.setId(requestedDicomServerStudyId);
            callbackStudy.setParentPatient(normalizedOrEmpty(request.getDicomServerPatientId()));
            callbackStudy.setSeries(request.getDicomServerSeriesIds() == null ? List.of() : request.getDicomServerSeriesIds());

            Map<String, Object> mainDicomTags = new HashMap<>();
            mainDicomTags.put(DicomTagConstants.ACCESSION_NUMBER, firstNonBlank(requestedAccessionNumber, expectedAccessionNumber));
            mainDicomTags.put(DicomTagConstants.STUDY_INSTANCE_UID, requestedStudyInstanceUid);
            mainDicomTags.put(DicomTagConstants.STUDY_DATE, normalizedOrEmpty(request.getStudyDate()));
            mainDicomTags.put("StudyTime", normalizedOrEmpty(request.getStudyTime()));
            mainDicomTags.put(DicomTagConstants.STUDY_DESCRIPTION, firstNonBlank(normalizedOrEmpty(request.getStudyDescription()), Worklist.getStudyDescription()));
            mainDicomTags.put(DicomTagConstants.INSTITUTION_NAME, firstNonBlank(normalizedOrEmpty(request.getInstitutionName()), Worklist.getInstitutionName()));
            callbackStudy.setMainDicomTags(mainDicomTags);

            Map<String, Object> patientTags = new HashMap<>();
            patientTags.put(DicomTagConstants.PATIENT_ID, firstNonBlank(normalizedOrEmpty(request.getPatientId()), Worklist.getPatientHn()));
            patientTags.put(DicomTagConstants.PATIENT_NAME, firstNonBlank(normalizedOrEmpty(request.getPatientName()), Worklist.getPatientName()));
            patientTags.put("PatientBirthDate", normalizedOrEmpty(request.getPatientBirthDate()));
            patientTags.put("PatientSex", normalizedOrEmpty(request.getPatientSex()));
            callbackStudy.setPatientMainDicomTags(patientTags);

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("CountInstances", callbackInstanceCount);
            callbackStudy.setStatistics(statistics);
            return callbackStudy;
        }
        if (callbackInstanceCount != null && callbackInstanceCount <= 0) {
            throw new IllegalArgumentException("DicomServer study has no image instances yet.");
        }
        String dicomServerStudyId = firstNonBlank(requestedDicomServerStudyId, Worklist.getDicomServerStudyId());
        if (!hasText(dicomServerStudyId) && hasText(requestedStudyInstanceUid)) {
            dicomServerStudyId = findDicomServerStudyIdByStudyInstanceUid(requestedStudyInstanceUid, server);
        }
        String dicomServerAccessionNumber = firstNonBlank(Worklist.getReferenceVisitCode(), Worklist.getAccessionNumber(), Worklist.getVisitCode());
        if (!hasText(dicomServerStudyId) && hasText(dicomServerAccessionNumber)) {
            dicomServerStudyId = findDicomServerStudyIdByAccessionNumber(dicomServerAccessionNumber, server);
        }
        if (!hasText(dicomServerStudyId)) {
            throw new IllegalArgumentException("DicomServer study is not available yet.");
        }

        DicomServerStudyResponse studyResponse = getDicomServerStudy(dicomServerStudyId, server);
        if (studyResponse == null) {
            throw new IllegalArgumentException("DicomServer study is not available yet.");
        }
        if (!hasText(studyResponse.getId())) {
            studyResponse.setId(dicomServerStudyId.trim());
        }

        String actualAccessionNumber = readDicomTag(studyResponse, DicomTagConstants.ACCESSION_NUMBER);
        if (!hasText(actualAccessionNumber)) {
            throw new IllegalArgumentException("DicomServer study is missing Accession Number.");
        }
        if (!isCallbackAccessionAllowedForWorklist(Worklist, actualAccessionNumber)) {
            throw new IllegalArgumentException("DicomServer study accession does not match this Worklist.");
        }

        String actualStudyInstanceUid = readDicomTag(studyResponse, DicomTagConstants.STUDY_INSTANCE_UID);
        if (!hasText(actualStudyInstanceUid)) {
            throw new IllegalArgumentException("DicomServer study is missing Study Instance UID.");
        }
        if (hasText(requestedStudyInstanceUid) && !actualStudyInstanceUid.trim().equalsIgnoreCase(requestedStudyInstanceUid.trim())) {
            throw new IllegalArgumentException("DicomServer study UID does not match the callback payload.");
        }
        if (!hasAvailableStudyInstances(studyResponse.getId(), studyResponse, server)) {
            throw new IllegalArgumentException("DicomServer study has no image instances yet.");
        }
        return studyResponse;
    }

    private boolean isAuthorizedDicomServerCallback(HttpServletRequest httpServletRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth
                && jwtAuth.isAuthenticated()
                && ApiConstants.Security.PRINCIPAL_TYPE_CLIENT.equals(jwtAuth.getToken().getClaimAsString("principalType"))) {
            return true;
        }
        LOGGER.warn("DICOM server callback rejected because machine-client Bearer token was not provided.");
        return false;
    }

    private Long resolveCallbackDicomServerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return null;
        }
        Object claim = jwtAuth.getToken().getClaim("dicomServerId");
        if (claim instanceof Number number) {
            long value = number.longValue();
            return value > 0L ? value : null;
        }
        if (claim instanceof String text) {
            try {
                long value = Long.parseLong(text.trim());
                return value > 0L ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private HospitalDicomServerResponse resolveCallbackDicomServer(Long callbackDicomServerId) {
        if (callbackDicomServerId == null || callbackDicomServerId <= 0L) {
            return null;
        }
        try {
            List<HospitalDicomServerResponse> rows = dicomServerMapper.getDicomServerById(callbackDicomServerId, null);
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (Exception error) {
            LOGGER.warn("Unable to resolve callback DICOM server {}: {}", callbackDicomServerId, error.getMessage());
            return null;
        }
    }

    private boolean isCallbackAccessionAllowedForWorklist(WorklistDetailRow worklist, String accessionNumber) {
        if (!hasText(accessionNumber)) {
            return true;
        }
        if (!hasAnyCallbackAccession(worklist)) {
            return true;
        }
        String normalized = accessionNumber.trim();
        return matchesCallbackAccession(normalized, worklist.getAccessionNumber())
                || matchesCallbackAccession(normalized, worklist.getVisitCode())
                || matchesCallbackAccession(normalized, worklist.getReferenceVisitCode());
    }

    private boolean hasAnyCallbackAccession(WorklistDetailRow worklist) {
        return worklist != null && (
                hasText(worklist.getAccessionNumber())
                        || hasText(worklist.getVisitCode())
                        || hasText(worklist.getReferenceVisitCode())
        );
    }

    private boolean matchesCallbackAccession(String requestedAccessionNumber, String expectedAccessionNumber) {
        return hasText(requestedAccessionNumber)
                && hasText(expectedAccessionNumber)
                && requestedAccessionNumber.trim().equalsIgnoreCase(expectedAccessionNumber.trim());
    }

    private String resolveStudyAccessionNumberForCallback(
            WorklistDetailRow worklist,
            WorklistReceivedStudyRequest request,
            Map<String, Object> studyTags
    ) {
        if (worklist == null) {
            return normalizedOrEmpty(request == null ? null : request.getAccessionNumber());
        }
        String worklistAccessionNumber = firstNonBlank(worklist.getAccessionNumber(), worklist.getVisitCode());
        String requestedAccessionNumber = normalizedOrEmpty(request == null ? null : request.getAccessionNumber());
        String dicomAccessionNumber = readDicomTag(studyTags, DicomTagConstants.ACCESSION_NUMBER);
        String referenceVisitCode = worklist.getReferenceVisitCode();
        if ((hasText(requestedAccessionNumber) && matchesCallbackAccession(requestedAccessionNumber, referenceVisitCode))
                || (hasText(dicomAccessionNumber) && matchesCallbackAccession(dicomAccessionNumber, referenceVisitCode))) {
            return firstNonBlank(worklistAccessionNumber, requestedAccessionNumber, dicomAccessionNumber);
        }
        return firstNonBlank(requestedAccessionNumber, dicomAccessionNumber, worklistAccessionNumber);
    }

    private WorklistDetailRow resolveCallbackWorklist(
            String accessionNumber,
            String visitCode,
            String studyInstanceUid,
            String dicomServerStudyId,
            HospitalDicomServerResponse callbackServer
    ) {
        if (hasText(accessionNumber)) {
            WorklistDetailRow Worklist = WorklistMapper.findWorklistByAccessionNumber(accessionNumber.trim());
            if (Worklist != null) {
                return Worklist;
            }
        }
        if (hasText(visitCode)) {
            WorklistDetailRow Worklist = WorklistMapper.findWorklistByVisitCodeAnyHospital(visitCode.trim());
            if (Worklist != null) {
                return Worklist;
            }
        }
        if (hasText(studyInstanceUid) || hasText(dicomServerStudyId)) {
            WorklistDetailRow Worklist = WorklistMapper.findWorklistByStudyIdentifiers(
                    normalizedOrEmpty(studyInstanceUid),
                    normalizedOrEmpty(dicomServerStudyId)
            );
            if (Worklist != null) {
                return Worklist;
            }
        }
        if (callbackServer == null) {
            return null;
        }
        DicomServerStudyResponse callbackStudy = resolveCallbackStudyFromDicomServer(studyInstanceUid, dicomServerStudyId, callbackServer);
        String resolvedAccession = readDicomTag(callbackStudy, DicomTagConstants.ACCESSION_NUMBER);
        if (hasText(resolvedAccession)) {
            return WorklistMapper.findWorklistByAccessionNumber(resolvedAccession.trim());
        }
        return null;
    }

    private DicomServerStudyResponse resolveCallbackStudyFromDicomServer(
            String studyInstanceUid,
            String dicomServerStudyId,
            HospitalDicomServerResponse callbackServer
    ) {
        try {
            String resolvedStudyId = normalizedOrEmpty(dicomServerStudyId);
            if (!hasText(resolvedStudyId) && hasText(studyInstanceUid)) {
                resolvedStudyId = findDicomServerStudyIdByStudyInstanceUid(studyInstanceUid, callbackServer);
            }
            if (!hasText(resolvedStudyId)) {
                return null;
            }
            DicomServerStudyResponse study = getDicomServerStudy(resolvedStudyId, callbackServer);
            if (study != null && !hasText(study.getId())) {
                study.setId(resolvedStudyId);
            }
            return study;
        } catch (Exception error) {
            LOGGER.warn("Unable to resolve callback study from DICOM server: {}", error.getMessage());
            return null;
        }
    }

    private boolean isCallbackAllowedForWorklist(Long callbackDicomServerId, HospitalDicomServerResponse callbackServer, WorklistDetailRow Worklist) {
        if (callbackDicomServerId == null || callbackDicomServerId <= 0L) {
            // Legacy machine clients remain controlled by ModulePermissionFilter allowlist.
            return true;
        }
        if (Worklist == null || Worklist.getHospitalId() == null || Worklist.getModalityId() == null) {
            return false;
        }
        Long routedDicomServerId = Worklist.getDicomServerId();
        if (routedDicomServerId != null && routedDicomServerId > 0L) {
            if (callbackDicomServerId.equals(routedDicomServerId)) {
                return true;
            }
            return isSameDicomServerEndpoint(callbackServer, findDicomServerById(routedDicomServerId, Worklist.getHospitalId()));
        }
        List<HospitalModalityServerRouteResponse> routes =
                dicomServerMapper.listActiveRoutesByHospitalAndModality(Worklist.getHospitalId(), Worklist.getModalityId());
        if (routes == null || routes.isEmpty()) {
            return false;
        }
        return routes.stream()
                .anyMatch(route -> route != null
                        && (callbackDicomServerId.equals(route.getDicomServerId())
                        || isSameDicomServerEndpoint(callbackServer, findDicomServerById(route.getDicomServerId(), Worklist.getHospitalId()))));
    }

    private HospitalDicomServerResponse findDicomServerById(Long dicomServerId, Long hospitalId) {
        if (dicomServerId == null || dicomServerId <= 0L) {
            return null;
        }
        try {
            List<HospitalDicomServerResponse> rows = dicomServerMapper.getDicomServerById(dicomServerId, hospitalId);
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (Exception error) {
            LOGGER.warn("Unable to resolve DICOM server {} for callback route check: {}", dicomServerId, error.getMessage());
            return null;
        }
    }

    private boolean isSameDicomServerEndpoint(HospitalDicomServerResponse callbackServer, HospitalDicomServerResponse routeServer) {
        String callbackBaseUrl = normalizeDicomServerEndpoint(callbackServer);
        String routeBaseUrl = normalizeDicomServerEndpoint(routeServer);
        return hasText(callbackBaseUrl) && callbackBaseUrl.equalsIgnoreCase(routeBaseUrl);
    }

    private String normalizeDicomServerEndpoint(HospitalDicomServerResponse server) {
        if (server == null) {
            return null;
        }
        try {
            return normalizePublicBaseUrl(resolveDicomServerBaseUrl(server));
        } catch (Exception ignored) {
            String protocol = Boolean.TRUE.equals(server.getSslEnabled()) ? "https" : "http";
            String host = firstNonBlank(server.getIpAddress(), "");
            Integer port = server.getPort();
            if (!hasText(host) || port == null || port <= 0) {
                return null;
            }
            return normalizePublicBaseUrl(protocol + "://" + host.replaceFirst("^https?://", "") + ":" + port);
        }
    }

    private void insertDicomServerCallbackLog(
            WorklistReceivedStudyRequest request,
            boolean success,
            String errorMessage,
            String warningMessage,
            String receivedAtIso
    ) {
        try {
            Map<String, Object> auditPayload = new HashMap<>();
            putIfHasText(auditPayload, "event", request == null ? null : request.getEvent());
            putIfHasText(auditPayload, "accessionNumber", request == null ? null : request.getAccessionNumber());
            putIfHasText(auditPayload, "dicomServerStudyId", request == null ? null : request.getDicomServerStudyId());
            putIfHasText(auditPayload, "dicomServerPatientId", request == null ? null : request.getDicomServerPatientId());
            putIfHasText(auditPayload, "studyInstanceUid", request == null ? null : request.getStudyInstanceUid());
            putIfHasText(auditPayload, "visitCode", request == null ? null : request.getVisitCode());
            if (request != null && request.getDicomServerSeriesIds() != null && !request.getDicomServerSeriesIds().isEmpty()) {
                auditPayload.put("dicomServerSeriesIds", request.getDicomServerSeriesIds());
            }
            if (request != null && request.getImageInstanceCount() != null) {
                auditPayload.put("imageInstanceCount", request.getImageInstanceCount());
            }
            String payloadJson = objectMapper.writeValueAsString(auditPayload);
            String dicomServerSeriesIdsJson = objectMapper.writeValueAsString(
                    request == null || request.getDicomServerSeriesIds() == null
                            ? List.of()
                            : request.getDicomServerSeriesIds()
            );
            dicomServerCallbackLogMapper.insertCallbackLog(
                    normalizedOrEmpty(request == null ? null : request.getEvent()),
                    normalizedOrEmpty(request == null ? null : request.getAccessionNumber()),
                    normalizedOrEmpty(request == null ? null : request.getDicomServerStudyId()),
                    normalizedOrEmpty(request == null ? null : request.getDicomServerPatientId()),
                    dicomServerSeriesIdsJson,
                    payloadJson,
                    success,
                    errorMessage,
                    warningMessage,
                    receivedAtIso
            );
        } catch (Exception error) {
            LOGGER.warn("Failed to persist DicomServer callback log: {}", error.getMessage());
        }
    }

    private LinkedStudyContext buildLinkedStudyContext(
            WorklistDetailRow Worklist,
            String dicomServerStudyId,
            DicomServerStudyResponse studyResponse,
            HospitalDicomServerResponse server,
            Long modifiedBy,
            String receivedAtIso
    ) {
        String resolvedStudyInstanceUid = firstNonBlank(
                readDicomTag(studyResponse, DicomTagConstants.STUDY_INSTANCE_UID),
                Worklist.getStudyInstanceUid(),
                Worklist.getStudyUuid(),
                dicomServerStudyId
        );
        String resolvedDicomServerStudyId = firstNonBlank(dicomServerStudyId, Worklist.getDicomServerStudyId());
        String dicomServerPatientId = firstNonBlank(studyResponse != null ? studyResponse.getParentPatient() : null, Worklist.getDicomServerPatientId());
        String dicomServerSeriesId = "";
        if (studyResponse != null && studyResponse.getSeries() != null && !studyResponse.getSeries().isEmpty()) {
            dicomServerSeriesId = firstNonBlank(studyResponse.getSeries().get(0), Worklist.getDicomServerSeriesId());
        } else {
            dicomServerSeriesId = firstNonBlank(Worklist.getDicomServerSeriesId(), "");
        }
        String viewerUrl = hasText(resolvedDicomServerStudyId)
                ? buildViewerUrl(resolvedDicomServerStudyId, server)
                : firstNonBlank(Worklist.getViewerUrl(), "");
        Map<String, Object> patientTags = studyResponse != null && studyResponse.getPatientMainDicomTags() != null
                ? studyResponse.getPatientMainDicomTags()
                : Collections.emptyMap();
        syncPatientHnFromDicom(Worklist, readDicomTag(patientTags, DicomTagConstants.PATIENT_ID));

        Long studyId = upsertStudyArchiveRecord(
                Worklist,
                studyResponse,
                resolvedStudyInstanceUid,
                resolvedDicomServerStudyId,
                dicomServerPatientId,
                dicomServerSeriesId,
                server,
                viewerUrl,
                receivedAtIso
        );
        return new LinkedStudyContext(studyId, resolvedDicomServerStudyId, resolvedStudyInstanceUid, dicomServerPatientId, dicomServerSeriesId, viewerUrl);
    }

    private void persistWorklistStudyLink(WorklistDetailRow Worklist, LinkedStudyContext linkedStudy, Long modifiedBy) {
        if (Worklist == null || linkedStudy == null || linkedStudy.studyId() == null || linkedStudy.studyId() <= 0L) {
            return;
        }
        WorklistMapper.upsertWorklistStudyLink(
                Worklist.getHospitalId(),
                Worklist.getId(),
                linkedStudy.studyId(),
                modifiedBy
        );
    }

    private Long upsertStudyArchiveRecord(
            WorklistDetailRow Worklist,
            DicomServerStudyResponse studyResponse,
            String studyInstanceUid,
            String dicomServerStudyId,
            String dicomServerPatientId,
            String dicomServerSeriesId,
            HospitalDicomServerResponse server,
            String viewerUrl,
            String receivedAtIso
    ) {
        if (Worklist == null || !hasText(studyInstanceUid)) {
            return Worklist != null ? Worklist.getStudyId() : null;
        }
        Map<String, Object> patientTags = studyResponse != null && studyResponse.getPatientMainDicomTags() != null
                ? studyResponse.getPatientMainDicomTags()
                : Collections.emptyMap();
        syncPatientHnFromDicom(Worklist, readDicomTag(patientTags, DicomTagConstants.PATIENT_ID));
        return studyMapper.upsertFromWorklist(
                Worklist.getHospitalId(),
                Worklist.getPatientId(),
                studyInstanceUid,
                firstNonBlank(readDicomTag(studyResponse, DicomTagConstants.ACCESSION_NUMBER), Worklist.getAccessionNumber()),
                Worklist.getModalityId(),
                firstNonBlank(Worklist.getModalityCode(), Worklist.getModalityName(), readDicomTag(studyResponse, "ModalitiesInStudy"), readDicomTag(studyResponse, "Modality")),
                resolveStudyDate(Worklist, studyResponse),
                firstNonBlank(readDicomTag(studyResponse, DicomTagConstants.STUDY_DESCRIPTION), Worklist.getStudyDescription()),
                firstNonBlank(readDicomTag(studyResponse, DicomTagConstants.INSTITUTION_NAME), Worklist.getInstitutionName()),
                resolveStudyDicomServerId(Worklist, server),
                StudyStatus.IMAGE_RECEIVED.code(),
                dicomServerStudyId,
                dicomServerPatientId,
                dicomServerSeriesId,
                resolveStudyInstanceCount(dicomServerStudyId, studyResponse, server),
                receivedAtIso
        );
    }

    private void syncPatientHnFromDicom(WorklistDetailRow Worklist, String patientHn) {
        if (Worklist == null || Worklist.getHospitalId() == null || Worklist.getPatientId() == null || !hasText(patientHn)) {
            return;
        }
        try {
            patientMapper.updatePatientHnIfBlank(Worklist.getHospitalId(), Worklist.getPatientId(), patientHn.trim());
            if (!hasText(Worklist.getPatientHn())) {
                Worklist.setPatientHn(patientHn.trim());
            }
        } catch (Exception error) {
            LOGGER.warn("Unable to sync DICOM PatientID as Patient HN for Worklist {}: {}", Worklist.getId(), error.getMessage());
        }
    }

    private void markLinkedStudyStatus(WorklistDetailRow Worklist, StudyStatus status) {
        if (Worklist == null || status == null) {
            return;
        }
        if (Worklist.getStudyId() != null && Worklist.getStudyId() > 0L) {
            studyMapper.updateStatusById(Worklist.getHospitalId(), Worklist.getStudyId(), status.code());
            return;
        }
        if (Worklist.getId() != null && Worklist.getId() > 0L) {
            studyMapper.updateStatusByWorklistId(Worklist.getHospitalId(), Worklist.getId(), status.code());
        }
    }

    private Long resolveStudyDicomServerId(WorklistDetailRow worklist, HospitalDicomServerResponse server) {
        if (server != null && server.getId() != null && server.getId() > 0L) {
            return server.getId();
        }
        if (worklist != null && worklist.getDicomServerId() != null && worklist.getDicomServerId() > 0L) {
            return worklist.getDicomServerId();
        }
        return null;
    }

    private StudyStatus resolveViewerStudyStatus(WorklistDetailRow Worklist) {
        if (Worklist == null) {
            return null;
        }
        if (Worklist.getStudyId() != null && Worklist.getStudyId() > 0L) {
            StudyResponse study = studyMapper.findById(Worklist.getHospitalId(), Worklist.getStudyId());
            StudyStatus status = safeStudyStatus(study == null ? null : study.getStatus());
            if (status != null) {
                return status;
            }
        }
        return hasText(resolveWorklistStudyInstanceUid(Worklist)) || hasText(resolveWorklistDicomServerStudyId(Worklist))
                ? StudyStatus.IMAGE_RECEIVED
                : null;
    }

    private static StudyStatus safeStudyStatus(String status) {
        try {
            return StudyStatus.fromValue(status);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveWorklistStudyInstanceUid(WorklistDetailRow Worklist) {
        if (Worklist == null) {
            return "";
        }
        String directStudyInstanceUid = firstNonBlank(Worklist.getStudyInstanceUid(), "");
        if (hasText(directStudyInstanceUid)) {
            return directStudyInstanceUid;
        }
        String studyUuid = firstNonBlank(Worklist.getStudyUuid(), "");
        if (hasText(studyUuid) && studyUuid.contains(".")) {
            return studyUuid;
        }
        return "";
    }

    private String resolveWorklistDicomServerStudyId(WorklistDetailRow Worklist) {
        if (Worklist == null) {
            return "";
        }
        String directDicomServerStudyId = firstNonBlank(Worklist.getDicomServerStudyId(), "");
        if (hasText(directDicomServerStudyId)) {
            return directDicomServerStudyId;
        }
        String studyUuid = firstNonBlank(Worklist.getStudyUuid(), "");
        if (hasText(studyUuid) && !studyUuid.contains(".")) {
            return studyUuid;
        }
        return "";
    }

    private ViewerInfoResponse buildViewerInfoResponse(
            WorklistDetailRow Worklist,
            StudyStatus studyStatus,
            HospitalDicomServerResponse server,
            String pacsApiBaseUrl,
            DicomServerStudyResponse studyResponse,
            String studyInstanceUid,
            String dicomServerStudyId,
            String mode,
            String requestedViewerAccess,
            Integer totalInstances,
            Integer seriesCount,
            Long viewerUserId,
            String viewerUsername
    ) {
        Map<String, Object> studyTags = studyResponse != null && studyResponse.getMainDicomTags() != null
                ? studyResponse.getMainDicomTags()
                : Collections.emptyMap();
        Map<String, Object> patientTags = studyResponse != null && studyResponse.getPatientMainDicomTags() != null
                ? studyResponse.getPatientMainDicomTags()
                : Collections.emptyMap();
        String viewerBaseUrl = resolvePublicViewerBaseUrl(server);
        String resolvedStudyInstanceUid = firstNonBlank(studyInstanceUid, resolveWorklistStudyInstanceUid(Worklist), readDicomTag(studyTags, DicomTagConstants.STUDY_INSTANCE_UID));
        String viewerToken = issueViewerDicomwebToken(Worklist.getHospitalId(), Worklist.getId(), resolvedStudyInstanceUid);
        String viewerAccess = ViewerAccessKeyService.normalizeAccessMode(
                hasText(requestedViewerAccess) ? requestedViewerAccess : ViewerAccessKeyService.ACCESS_EDIT
        );
        String viewerApiKey = issueViewerApiKey(
                Worklist.getHospitalId(),
                Worklist.getId(),
                Worklist.getStudyId(),
                Worklist.getModalityId(),
                resolvedStudyInstanceUid,
                viewerUserId,
                viewerUsername,
                viewerAccess
        );
        ViewerEditCapabilities editCapabilities =
                resolveViewerEditCapabilities(Worklist, viewerUserId, viewerAccess);
        String directDicomwebBaseUrl = resolvePublicDicomwebBaseUrl(server);
        String gatewayDicomwebBaseUrl = buildViewerDicomwebGatewayBaseUrl(
                pacsApiBaseUrl,
                Worklist.getHospitalId(),
                Worklist.getId(),
                resolvedStudyInstanceUid,
                viewerToken
        );
        boolean directDicomweb = hasText(directDicomwebBaseUrl);
        String dicomwebBaseUrl = directDicomweb ? directDicomwebBaseUrl : gatewayDicomwebBaseUrl;
        String resolvedDicomServerStudyId = firstNonBlank(dicomServerStudyId, resolveWorklistDicomServerStudyId(Worklist));
        String dicomServerSeriesId = firstNonBlank(Worklist.getDicomServerSeriesId(), "");
        if (!hasText(dicomServerSeriesId) && studyResponse != null && studyResponse.getSeries() != null && !studyResponse.getSeries().isEmpty()) {
            dicomServerSeriesId = firstNonBlank(studyResponse.getSeries().get(0), "");
        }

        ViewerInfoResponse response = new ViewerInfoResponse();
        response.setSuccess(Boolean.TRUE);
        response.setDirectDicomweb(directDicomweb);
        response.setWorklistId(Worklist.getId());
        response.setPublicKey(Worklist.getPublicKey());
        response.setHospitalId(Worklist.getHospitalId());
        response.setHospitalPublicKey(Worklist.getHospitalPublicKey());
        response.setStudyId(Worklist.getStudyId());
        response.setStudyPublicKey(Worklist.getStudyPublicKey());
        response.setModalityId(Worklist.getModalityId());
        response.setModalityPublicKey(Worklist.getModalityPublicKey());
        response.setPatientPublicKey(Worklist.getPatientPublicKey());
        response.setWorklistStatus(Worklist.getStatus());
        response.setStatus(studyStatus.name());
        response.setDicomServerStudyId(resolvedDicomServerStudyId);
        response.setDicomServerPatientId(firstNonBlank(Worklist.getDicomServerPatientId(), studyResponse == null ? null : studyResponse.getParentPatient(), ""));
        response.setDicomServerSeriesId(dicomServerSeriesId);
        response.setStudyInstanceUid(resolvedStudyInstanceUid);
        response.setAccessionNumber(firstNonBlank(readDicomTag(studyTags, DicomTagConstants.ACCESSION_NUMBER), Worklist.getAccessionNumber(), ""));
        response.setPatientUid(firstNonBlank(Worklist.getPatientUid(), ""));
        response.setPatientHn(firstNonBlank(readDicomTag(patientTags, DicomTagConstants.PATIENT_ID), Worklist.getPatientHn(), ""));
        response.setPatientName(firstNonBlank(readDicomTag(patientTags, DicomTagConstants.PATIENT_NAME), Worklist.getPatientName(), ""));
        response.setModalityName(firstNonBlank(Worklist.getModalityName(), Worklist.getModalityCode(), readDicomTag(studyTags, "ModalitiesInStudy"), readDicomTag(studyTags, "Modality"), ""));
        response.setStudyDescription(firstNonBlank(readDicomTag(studyTags, DicomTagConstants.STUDY_DESCRIPTION), Worklist.getStudyDescription(), ""));
        response.setInstitutionName(firstNonBlank(readDicomTag(studyTags, DicomTagConstants.INSTITUTION_NAME), Worklist.getInstitutionName(), ""));
        response.setImageReceivedAt(Worklist.getImageReceivedAt());
        response.setImageInstanceCount(totalInstances);
        response.setTotalInstances(totalInstances);
        response.setSeriesCount(seriesCount == null ? 0 : seriesCount);
        response.setViewerBaseUrl(viewerBaseUrl);
        response.setDicomwebBaseUrl(dicomwebBaseUrl);
        response.setDicomwebGatewayBaseUrl(gatewayDicomwebBaseUrl);
        response.setDicomwebAuthToken(directDicomweb ? viewerToken : null);
        response.setViewerApiKey(viewerApiKey);
        response.setViewerAccess(viewerAccess);
        response.setCanEditResult(editCapabilities.canEditResult());
        response.setCanEditViewerState(editCapabilities.canEditViewerState());
        response.setDicomServerUiBaseUrl(resolvePublicDicomServerUiBaseUrl(server));
        response.setViewerUrl(buildOhifViewerUrl(resolvedStudyInstanceUid, mode, viewerBaseUrl, dicomwebBaseUrl, response, directDicomweb ? viewerToken : null, viewerApiKey));
        response.setBasicViewerUrl(buildOhifViewerUrl(resolvedStudyInstanceUid, "basic", viewerBaseUrl, dicomwebBaseUrl, response, directDicomweb ? viewerToken : null, viewerApiKey));
        response.setSegmentationViewerUrl(buildOhifViewerUrl(resolvedStudyInstanceUid, "segmentation", viewerBaseUrl, dicomwebBaseUrl, response, directDicomweb ? viewerToken : null, viewerApiKey));
        response.setPublicViewerUrl(buildPublicViewerVerificationUrl(
                viewerBaseUrl,
                mode,
                Worklist.getHospitalPublicKey(),
                Worklist.getPublicKey(),
                null
        ));
        return response;
    }

    private ViewerInfoResponse buildPublicStudyViewerInfo(
            StudyResponse study,
            String mode,
            HttpServletRequest httpServletRequest
    ) {
        if (study == null
                || study.getHospitalId() == null
                || study.getId() == null
                || !hasText(study.getHospitalPublicKey())
                || !hasText(study.getPublicKey())
                || !hasText(study.getStudyInstanceUid())) {
            return null;
        }

        HospitalDicomServerResponse server = resolveStudyDicomServer(study, study.getHospitalId());
        String viewerBaseUrl = resolvePublicViewerBaseUrl(server);
        String dicomwebBaseUrl = resolvePublicDicomwebBaseUrl(server);
        if (!hasText(viewerBaseUrl) || !hasText(dicomwebBaseUrl)) {
            return null;
        }

        String viewerToken = issueViewerDicomwebToken(
                study.getHospitalId(),
                null,
                study.getId(),
                study.getStudyInstanceUid()
        );
        String viewerApiKey = issueViewerApiKey(
                study.getHospitalId(),
                null,
                study.getId(),
                study.getModalityId(),
                study.getStudyInstanceUid(),
                null,
                null,
                ViewerAccessKeyService.ACCESS_PUBLIC
        );
        if (!hasText(viewerToken) || !hasText(viewerApiKey)) {
            return null;
        }

        ViewerInfoResponse response = new ViewerInfoResponse();
        response.setSuccess(Boolean.TRUE);
        response.setDirectDicomweb(Boolean.FALSE);
        response.setPublicKey(study.getPublicKey());
        response.setHospitalPublicKey(study.getHospitalPublicKey());
        response.setStudyPublicKey(study.getPublicKey());
        response.setModalityPublicKey(study.getModalityPublicKey());
        response.setPatientPublicKey(study.getPatientPublicKey());
        response.setStatus(firstNonBlank(study.getStatus(), StudyStatus.IMAGE_RECEIVED.name()));
        response.setDicomServerStudyId(study.getDicomServerStudyId());
        response.setDicomServerPatientId(study.getDicomServerPatientId());
        response.setDicomServerSeriesId(study.getDicomServerSeriesId());
        response.setStudyInstanceUid(study.getStudyInstanceUid());
        response.setAccessionNumber(study.getAccessionNumber());
        response.setPatientUid(study.getMrn());
        response.setPatientHn(study.getPatientHn());
        response.setPatientName(study.getPatientName());
        response.setModalityName(firstNonBlank(study.getModalityName(), study.getModality()));
        response.setStudyDescription(study.getStudyDescription());
        response.setInstitutionName(study.getInstitutionName());
        response.setImageReceivedAt(study.getImageReceivedAt());
        response.setImageInstanceCount(study.getInstances());
        response.setTotalInstances(study.getInstances());
        response.setSeriesCount(hasText(study.getDicomServerSeriesId()) ? 1 : 0);
        response.setViewerBaseUrl(viewerBaseUrl);
        response.setDicomwebBaseUrl(dicomwebBaseUrl);
        response.setDicomwebGatewayBaseUrl(null);
        response.setDicomwebAuthToken(viewerToken);
        response.setViewerApiKey(viewerApiKey);
        response.setViewerAccess(ViewerAccessKeyService.ACCESS_PUBLIC);
        response.setCanEditResult(Boolean.FALSE);
        response.setCanEditViewerState(Boolean.FALSE);
        response.setDicomServerUiBaseUrl(resolvePublicDicomServerUiBaseUrl(server));
        response.setViewerUrl(buildOhifViewerUrl(study.getStudyInstanceUid(), mode, viewerBaseUrl, dicomwebBaseUrl, response, viewerToken, viewerApiKey));
        response.setBasicViewerUrl(buildOhifViewerUrl(study.getStudyInstanceUid(), "basic", viewerBaseUrl, dicomwebBaseUrl, response, viewerToken, viewerApiKey));
        response.setSegmentationViewerUrl(buildOhifViewerUrl(study.getStudyInstanceUid(), "segmentation", viewerBaseUrl, dicomwebBaseUrl, response, viewerToken, viewerApiKey));
        response.setPublicViewerUrl(buildPublicViewerVerificationUrl(
                viewerBaseUrl,
                mode,
                study.getHospitalPublicKey(),
                null,
                study.getPublicKey()
        ));
        return response;
    }

    private ViewerEditCapabilities resolveViewerEditCapabilities(
            WorklistDetailRow worklist,
            Long viewerUserId,
            String viewerAccess
    ) {
        if (worklist == null
                || viewerUserId == null
                || viewerUserId <= 0L
                || !ViewerAccessKeyService.ACCESS_EDIT.equals(viewerAccess)
                || pacsResultMapper == null) {
            return ViewerEditCapabilities.READ_ONLY;
        }
        try {
            PacsResultFindByWorklistRequest resultRequest = new PacsResultFindByWorklistRequest();
            resultRequest.setHospitalId(worklist.getHospitalId());
            resultRequest.setWorklistId(worklist.getId());
            PacsResultResponse existingResult = pacsResultMapper.findByWorklist(resultRequest);
            if (isCompletedResult(existingResult)) {
                return ViewerEditCapabilities.READ_ONLY;
            }

            PacsViewerStateRequest stateRequest = new PacsViewerStateRequest();
            stateRequest.setHospitalId(worklist.getHospitalId());
            stateRequest.setWorklistId(worklist.getId());
            stateRequest.setStateType(DEFAULT_VIEWER_STATE_TYPE);
            PacsViewerStateResponse existingState = pacsResultMapper.findViewerState(stateRequest);

            Long ownerId = viewerOwnerId(existingResult, existingState);
            boolean canEdit = ownerId == null || viewerUserId.equals(ownerId);
            return new ViewerEditCapabilities(canEdit, canEdit);
        } catch (Exception error) {
            LOGGER.warn(
                    "Unable to resolve viewer edit ownership for worklistId={}: {}",
                    worklist.getId(),
                    error.toString()
            );
            return ViewerEditCapabilities.READ_ONLY;
        }
    }

    private ViewerEditCapabilities resolveViewerEditCapabilities(ViewerAccessClaims accessClaims) {
        if (accessClaims == null
                || accessClaims.hospitalId() == null
                || accessClaims.hospitalId() <= 0L) {
            return ViewerEditCapabilities.READ_ONLY;
        }
        if (accessClaims.worklistId() != null && accessClaims.worklistId() > 0L) {
            WorklistDetailRow worklist = WorklistMapper.findWorklistById(
                    accessClaims.hospitalId(),
                    accessClaims.worklistId()
            );
            return resolveViewerEditCapabilities(
                    worklist,
                    accessClaims.userId(),
                    accessClaims.accessMode()
            );
        }
        StudyResponse study = resolveViewerAccessStudy(accessClaims);
        return resolveStudyViewerEditCapabilities(
                study,
                accessClaims.userId(),
                accessClaims.accessMode()
        );
    }

    private StudyResponse resolveViewerAccessStudy(ViewerAccessClaims accessClaims) {
        if (accessClaims == null || accessClaims.hospitalId() == null || accessClaims.hospitalId() <= 0L) {
            return null;
        }
        if (accessClaims.studyId() != null && accessClaims.studyId() > 0L && studyMapper != null) {
            StudyResponse study = studyMapper.findById(accessClaims.hospitalId(), accessClaims.studyId());
            if (study != null) {
                return study;
            }
        }
        if ((accessClaims.studyId() == null || accessClaims.studyId() <= 0L)
                && !hasText(accessClaims.studyInstanceUid())) {
            return null;
        }
        StudyResponse study = new StudyResponse();
        study.setHospitalId(accessClaims.hospitalId());
        study.setId(accessClaims.studyId());
        study.setModalityId(accessClaims.modalityId());
        study.setStudyInstanceUid(accessClaims.studyInstanceUid());
        return study;
    }

    private ViewerEditCapabilities resolveStudyViewerEditCapabilities(
            StudyResponse study,
            Long viewerUserId,
            String viewerAccess
    ) {
        if (study == null
                || viewerUserId == null
                || viewerUserId <= 0L
                || !ViewerAccessKeyService.ACCESS_EDIT.equals(viewerAccess)
                || pacsResultMapper == null) {
            return ViewerEditCapabilities.READ_ONLY;
        }
        try {
            PacsResultResponse existingResult = findExistingStudyResult(study);
            if (isCompletedResult(existingResult)) {
                return ViewerEditCapabilities.READ_ONLY;
            }

            PacsViewerStateResponse existingState = findExistingStudyViewerState(study);
            Long ownerId = viewerOwnerId(existingResult, existingState);
            boolean canEdit = ownerId == null || viewerUserId.equals(ownerId);
            return new ViewerEditCapabilities(canEdit, canEdit);
        } catch (Exception error) {
            LOGGER.warn(
                    "Unable to resolve viewer edit ownership for studyId={}, studyInstanceUid={}: {}",
                    study.getId(),
                    study.getStudyInstanceUid(),
                    error.toString()
            );
            return ViewerEditCapabilities.READ_ONLY;
        }
    }

    private PacsResultResponse findExistingStudyResult(StudyResponse study) {
        if (study == null
                || study.getHospitalId() == null
                || study.getHospitalId() <= 0L
                || study.getModalityId() == null
                || study.getModalityId() <= 0L) {
            return null;
        }
        if (study.getId() != null && study.getId() > 0L) {
            return pacsResultMapper.findByStudyId(
                    study.getHospitalId(),
                    study.getModalityId(),
                    study.getId()
            );
        }
        if (hasText(study.getStudyInstanceUid())) {
            return pacsResultMapper.findByStudyInstanceUid(
                    study.getHospitalId(),
                    study.getModalityId(),
                    study.getStudyInstanceUid()
            );
        }
        return null;
    }

    private PacsViewerStateResponse findExistingStudyViewerState(StudyResponse study) {
        if (study == null || study.getHospitalId() == null || study.getHospitalId() <= 0L) {
            return null;
        }
        PacsViewerStateRequest stateRequest = new PacsViewerStateRequest();
        stateRequest.setHospitalId(study.getHospitalId());
        stateRequest.setModalityId(study.getModalityId());
        stateRequest.setStudyId(study.getId());
        stateRequest.setStudyInstanceUid(study.getStudyInstanceUid());
        stateRequest.setAccessionNumber(study.getAccessionNumber());
        stateRequest.setStateType(DEFAULT_VIEWER_STATE_TYPE);
        return pacsResultMapper.findViewerState(stateRequest);
    }

    private static Long viewerOwnerId(PacsResultResponse result, PacsViewerStateResponse state) {
        Long resultOwner = positiveUserId(result == null ? null : result.getCreatedBy());
        Long stateOwner = positiveUserId(state == null ? null : state.getCreatedBy());
        return FunctionHelper.firstNonNull(resultOwner, stateOwner);
    }

    private static boolean isCompletedResult(PacsResultResponse result) {
        return result != null
                && (Boolean.TRUE.equals(result.getCompleted())
                || RESULT_STATUS_COMPLETED.equalsIgnoreCase(String.valueOf(result.getStatus())));
    }

    private static Long positiveUserId(Long userId) {
        return userId != null && userId > 0L ? userId : null;
    }

    private record ViewerEditCapabilities(boolean canEditResult, boolean canEditViewerState) {
        private static final ViewerEditCapabilities READ_ONLY = new ViewerEditCapabilities(false, false);
    }

    private String buildPublicViewerVerificationUrl(
            String viewerBaseUrl,
            String mode,
            String hospitalKey,
            String worklistKey,
            String studyKey
    ) {
        if (!hasText(viewerBaseUrl)
                || !hasText(hospitalKey)
                || (!hasText(worklistKey) && !hasText(studyKey))) {
            return null;
        }
        String viewerRouteUrl = appendPublicPath(viewerBaseUrl, normalizeViewerRoute(mode));
        if (!hasText(viewerRouteUrl)) {
            return null;
        }
        StringBuilder builder = new StringBuilder(viewerRouteUrl)
                .append("?publicViewer=1&hospitalKey=")
                .append(URLEncoder.encode(hospitalKey.trim(), StandardCharsets.UTF_8));
        if (hasText(worklistKey)) {
            builder.append("&worklistKey=")
                    .append(URLEncoder.encode(worklistKey.trim(), StandardCharsets.UTF_8));
        } else {
            builder.append("&studyKey=")
                    .append(URLEncoder.encode(studyKey.trim(), StandardCharsets.UTF_8));
        }
        builder.append("&mode=")
                .append(URLEncoder.encode(firstNonBlank(mode, "segmentation"), StandardCharsets.UTF_8));
        return builder.toString();
    }

    private String buildOhifViewerUrl(String studyInstanceUid, String mode, String viewerBaseUrl, String dicomwebBaseUrl) {
        return buildOhifViewerUrl(studyInstanceUid, mode, viewerBaseUrl, dicomwebBaseUrl, null);
    }

    private String buildOhifViewerUrl(String studyInstanceUid, String mode, String viewerBaseUrl, String dicomwebBaseUrl, ViewerInfoResponse context) {
        return buildOhifViewerUrl(studyInstanceUid, mode, viewerBaseUrl, dicomwebBaseUrl, context, null);
    }

    private String buildOhifViewerUrl(String studyInstanceUid, String mode, String viewerBaseUrl, String dicomwebBaseUrl, ViewerInfoResponse context, String dicomwebAuthToken) {
        return buildOhifViewerUrl(studyInstanceUid, mode, viewerBaseUrl, dicomwebBaseUrl, context, dicomwebAuthToken, null);
    }

    private String buildOhifViewerUrl(String studyInstanceUid, String mode, String viewerBaseUrl, String dicomwebBaseUrl, ViewerInfoResponse context, String dicomwebAuthToken, String viewerApiKey) {
        if (!hasText(studyInstanceUid) || !hasText(viewerBaseUrl)) {
            return null;
        }
        String route = normalizeViewerRoute(mode);
        String viewerRouteUrl = appendPublicPath(viewerBaseUrl, route);
        if (!hasText(viewerRouteUrl)) {
            return null;
        }
        StringBuilder builder = new StringBuilder(viewerRouteUrl);
        builder.append("?StudyInstanceUIDs=")
                .append(URLEncoder.encode(studyInstanceUid.trim(), StandardCharsets.UTF_8));
        if (hasText(dicomwebBaseUrl)) {
            builder.append("&dicomwebBaseUrl=")
                    .append(URLEncoder.encode(dicomwebBaseUrl.trim(), StandardCharsets.UTF_8));
        }
        if (hasText(dicomwebAuthToken) || hasText(viewerApiKey)) {
            StringBuilder fragment = new StringBuilder();
            appendViewerFragmentParam(fragment, PARAM_TOKEN, dicomwebAuthToken);
            appendViewerFragmentParam(fragment, "viewerAccessToken", viewerApiKey);
            appendViewerFragmentParam(fragment, "viewerAccess", context == null ? null : context.getViewerAccess());
            appendViewerFragmentParam(fragment, "canEditResult", Boolean.TRUE.equals(context == null ? null : context.getCanEditResult()) ? "1" : "0");
            appendViewerFragmentParam(fragment, "canEditViewerState", Boolean.TRUE.equals(context == null ? null : context.getCanEditViewerState()) ? "1" : "0");
            if (!fragment.isEmpty()) {
                builder.append("#").append(fragment);
            }
        }
        return builder.toString();
    }

    private void appendViewerFragmentParam(StringBuilder builder, String name, Object value) {
        if (builder == null || value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (!hasText(text)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("&");
        }
        builder.append(name)
                .append("=")
                .append(URLEncoder.encode(text.trim(), StandardCharsets.UTF_8));
    }

    private String normalizeViewerRoute(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "basic", "viewer" -> "viewer";
            case "usannotation", "us-annotation", "us_annotation" -> "usAnnotation";
            case "tmtv" -> "tmtv";
            case "microscopy" -> "microscopy";
            case "preclinical-4d", "preclinical_4d", "dynamic-volume", "dynamic_volume" -> "dynamic-volume";
            case "segmentation" -> "segmentation";
            default -> "segmentation";
        };
    }

    private LocalDate resolveStudyDate(WorklistDetailRow Worklist, DicomServerStudyResponse studyResponse) {
        LocalDate parsedStudyDate = parseDicomStudyDate(readDicomTag(studyResponse, DicomTagConstants.STUDY_DATE));
        if (parsedStudyDate != null) {
            return parsedStudyDate;
        }
        if (Worklist != null && Worklist.getScheduledDate() != null) {
            return Worklist.getScheduledDate();
        }
        return null;
    }

    private WorklistViewerStudyResponse buildWorklistViewerResponse(
            WorklistDetailRow Worklist,
            String dicomServerStudyId,
            DicomServerStudyResponse studyResponse,
            HospitalDicomServerResponse server,
            String pacsApiBaseUrl
    ) {
        Map<String, Object> studyTags = studyResponse != null && studyResponse.getMainDicomTags() != null
                ? studyResponse.getMainDicomTags()
                : Collections.emptyMap();
        Map<String, Object> patientTags = studyResponse != null && studyResponse.getPatientMainDicomTags() != null
                ? studyResponse.getPatientMainDicomTags()
                : Collections.emptyMap();
        WorklistViewerStudyResponse response = new WorklistViewerStudyResponse();
        response.setWorklistId(Worklist.getId());
        response.setPublicKey(Worklist.getPublicKey());
        response.setHospitalId(Worklist.getHospitalId());
        response.setHospitalPublicKey(Worklist.getHospitalPublicKey());
        response.setStudyId(Worklist.getStudyId());
        response.setStudyPublicKey(Worklist.getStudyPublicKey());
        response.setModalityId(Worklist.getModalityId());
        response.setModalityPublicKey(Worklist.getModalityPublicKey());
        response.setPatientPublicKey(Worklist.getPatientPublicKey());
        response.setVisitCode(Worklist.getVisitCode());
        response.setStatus(Worklist.getStatus());
        response.setPatientUid(firstNonBlank(Worklist.getPatientUid(), ""));
        response.setPatientHn(firstNonBlank(readDicomTag(patientTags, DicomTagConstants.PATIENT_ID), Worklist.getPatientHn(), ""));
        response.setPatientName(firstNonBlank(readDicomTag(patientTags, DicomTagConstants.PATIENT_NAME), Worklist.getPatientName(), ""));
        response.setAccessionNumber(firstNonBlank(readDicomTag(studyTags, DicomTagConstants.ACCESSION_NUMBER), Worklist.getAccessionNumber(), ""));
        response.setModalityName(firstNonBlank(Worklist.getModalityName(), Worklist.getModalityCode(), ""));
        response.setStudyDescription(firstNonBlank(readDicomTag(studyTags, DicomTagConstants.STUDY_DESCRIPTION), Worklist.getStudyDescription(), ""));
        response.setInstitutionName(firstNonBlank(readDicomTag(studyTags, DicomTagConstants.INSTITUTION_NAME), Worklist.getInstitutionName(), ""));
        response.setDicomServerStudyId(dicomServerStudyId);
        String resolvedStudyInstanceUid = firstNonBlank(Worklist.getStudyInstanceUid(), Worklist.getStudyUuid(), readDicomTag(studyTags, DicomTagConstants.STUDY_INSTANCE_UID));
        response.setStudyInstanceUid(resolvedStudyInstanceUid);
        response.setViewerUrl(firstNonBlank(Worklist.getViewerUrl(), buildViewerUrl(dicomServerStudyId, server)));
        String viewerBaseUrl = resolvePublicViewerBaseUrl(server);
        response.setViewerBaseUrl(viewerBaseUrl);
        String viewerToken = issueViewerDicomwebToken(Worklist.getHospitalId(), Worklist.getId(), resolvedStudyInstanceUid);
        String directDicomwebBaseUrl = resolvePublicDicomwebBaseUrl(server);
        String gatewayDicomwebBaseUrl = buildViewerDicomwebGatewayBaseUrl(
                pacsApiBaseUrl,
                Worklist.getHospitalId(),
                Worklist.getId(),
                resolvedStudyInstanceUid,
                viewerToken
        );
        response.setDicomwebBaseUrl(hasText(directDicomwebBaseUrl) ? directDicomwebBaseUrl : gatewayDicomwebBaseUrl);
        response.setDicomwebGatewayBaseUrl(gatewayDicomwebBaseUrl);
        response.setDicomwebAuthToken(hasText(directDicomwebBaseUrl) ? viewerToken : null);
        String viewerAccess = ViewerAccessKeyService.ACCESS_EDIT;
        Long viewerUserId = currentUserId();
        String viewerUsername = currentUsername();
        ViewerEditCapabilities editCapabilities =
                resolveViewerEditCapabilities(Worklist, viewerUserId, viewerAccess);
        response.setViewerAccess(viewerAccess);
        response.setViewerApiKey(issueViewerApiKey(
                Worklist.getHospitalId(),
                Worklist.getId(),
                Worklist.getStudyId(),
                Worklist.getModalityId(),
                resolvedStudyInstanceUid,
                viewerUserId,
                viewerUsername,
                viewerAccess
        ));
        response.setCanEditResult(editCapabilities.canEditResult());
        response.setCanEditViewerState(editCapabilities.canEditViewerState());
        response.setDicomServerUiBaseUrl(resolvePublicDicomServerUiBaseUrl(server));

        List<String> instanceIds = resolveViewerInstanceIds(dicomServerStudyId, studyResponse, server);
        response.setTotalInstances(instanceIds.size());
        response.setPreviewLimited(instanceIds.size() > MAX_VIEWER_PREVIEW_INSTANCES);

        List<WorklistViewerInstanceResponse> instances = new ArrayList<>();
        int limit = Math.min(instanceIds.size(), MAX_VIEWER_PREVIEW_INSTANCES);
        for (int index = 0; index < limit; index++) {
            String instanceId = firstNonBlank(instanceIds.get(index), "");
            if (!hasText(instanceId)) {
                continue;
            }
            WorklistViewerInstanceResponse item = new WorklistViewerInstanceResponse();
            item.setInstanceId(instanceId);
            item.setLabel("Image " + (index + 1));
            item.setPreviewPath(ApiConstants.Worklist.BASE_PATH + "/worklist-view-study-preview/" + Worklist.getId() + "/" + instanceId);
            instances.add(item);
        }
        response.setInstances(instances);
        return response;
    }

    private List<String> resolveViewerInstanceIds(
            String dicomServerStudyId,
            DicomServerStudyResponse studyResponse,
            HospitalDicomServerResponse server
    ) {
        if (studyResponse != null && studyResponse.getInstances() != null && !studyResponse.getInstances().isEmpty()) {
            return studyResponse.getInstances();
        }
        if (!hasText(dicomServerStudyId)) {
            return Collections.emptyList();
        }

        try {
            List<DicomServerSeriesResponse> seriesList;
            if (server == null) {
                return Collections.emptyList();
            }
            seriesList = dicomServerClientService.getSeriesByStudyId(
                        resolveDicomServerBaseUrl(server),
                        server.getUsername(),
                        server.getPassword(),
                        dicomServerStudyId.trim()
                );

            List<String> instanceIds = new ArrayList<>();
            if (seriesList != null) {
                for (DicomServerSeriesResponse series : seriesList) {
                    if (series == null || series.getInstances() == null || series.getInstances().isEmpty()) {
                        continue;
                    }
                    for (String instanceId : series.getInstances()) {
                        String resolved = firstNonBlank(instanceId, "");
                        if (hasText(resolved)) {
                            instanceIds.add(resolved);
                        }
                    }
                }
            }
            return instanceIds;
        } catch (Exception error) {
            LOGGER.warn(
                    "Worklist-view-study instance resolution skipped for dicomServerStudyId={} because series lookup failed: {}",
                    dicomServerStudyId,
                    error.getMessage()
            );
            return Collections.emptyList();
        }
    }

    private boolean hasAvailableStudyInstances(
            String dicomServerStudyId,
            DicomServerStudyResponse studyResponse,
            HospitalDicomServerResponse server
    ) {
        Integer count = resolveStudyInstanceCount(dicomServerStudyId, studyResponse, server);
        return count != null && count > 0;
    }

    private Integer resolveStudyInstanceCount(
            String dicomServerStudyId,
            DicomServerStudyResponse studyResponse,
            HospitalDicomServerResponse server
    ) {
        if (studyResponse == null) {
            return 0;
        }
        if (studyResponse.getInstances() != null && !studyResponse.getInstances().isEmpty()) {
            return studyResponse.getInstances().size();
        }
        Integer statisticsCount = readDicomServerInstanceCount(studyResponse.getStatistics());
        if (statisticsCount != null && statisticsCount > 0) {
            return statisticsCount;
        }
        return resolveViewerInstanceIds(dicomServerStudyId, studyResponse, server).size();
    }

    private String resolveDicomServerStudyIdForViewer(WorklistDetailRow Worklist, HospitalDicomServerResponse server) {
        if (Worklist == null) {
            return "";
        }
        if (hasText(Worklist.getDicomServerStudyId())) {
            return Worklist.getDicomServerStudyId().trim();
        }
        String storedStudyUuid = firstNonBlank(Worklist.getStudyUuid(), "");
        if (hasText(storedStudyUuid) && !storedStudyUuid.contains(".")) {
            return storedStudyUuid;
        }
        String studyInstanceUid = firstNonBlank(Worklist.getStudyInstanceUid(), storedStudyUuid);
        if (hasText(studyInstanceUid)) {
            String studyId = findDicomServerStudyIdByStudyInstanceUid(studyInstanceUid, server);
            if (hasText(studyId)) {
                return studyId;
            }
        }
        if (hasText(Worklist.getAccessionNumber())) {
            return firstNonBlank(findDicomServerStudyIdByAccessionNumber(Worklist.getAccessionNumber(), server), "");
        }
        return "";
    }

    private DicomServerStudyResponse getDicomServerStudy(String dicomServerStudyId, HospitalDicomServerResponse server) {
        if (!hasText(dicomServerStudyId)) {
            return null;
        }
        if (server == null) {
            return null;
        }
        return dicomServerClientService.getStudyById(resolveDicomServerBaseUrl(server), server.getUsername(), server.getPassword(), dicomServerStudyId.trim());
    }

    private ResponseEntity<byte[]> getDicomServerInstancePreview(String instanceId, HospitalDicomServerResponse server) {
        if (server == null) {
            throw new IllegalArgumentException("Active DICOM server routing is not configured for this Worklist.");
        }
        return dicomServerClientService.getInstancePreview(resolveDicomServerBaseUrl(server), server.getUsername(), server.getPassword(), instanceId);
    }

    private void markWorklistFailed(Long worklistId, String errorMessage) {
        markWorklistFailed(worklistId, WorklistConstants.ACTION_UPDATE_STATUS, errorMessage);
    }

    private void markWorklistFailed(Long worklistId, String action, String errorMessage) {
        if (worklistId == null || worklistId <= 0L) {
            return;
        }
        try {
            Long hospitalId = currentHospitalId();
            WorklistDetailRow Worklist = WorklistMapper.findWorklistById(hospitalId, worklistId);
            markWorklistFailed(Worklist, currentUserId(), action, errorMessage);
        } catch (Exception error) {
            LOGGER.warn("Unable to mark worklistId={} as FAILED: {}", worklistId, error.getMessage());
        }
    }

    private void markWorklistFailedAfterSendAttempt(WorklistDetailRow Worklist, Long actorId, String errorMessage) {
        markWorklistFailed(Worklist, actorId, WorklistConstants.ACTION_SEND_WORKLIST, errorMessage);
    }

    private void markWorklistFailed(WorklistDetailRow Worklist, Long actorId, String errorMessage) {
        markWorklistFailed(Worklist, actorId, WorklistConstants.ACTION_UPDATE_STATUS, errorMessage);
    }

    private void markWorklistFailed(WorklistDetailRow Worklist, Long actorId, String action, String errorMessage) {
        if (Worklist == null || Worklist.getId() == null || Worklist.getId() <= 0L) {
            return;
        }
        try {
            persistWorklistFailedInNewTransaction(Worklist, actorId, action, errorMessage);
        } catch (Exception error) {
            LOGGER.warn("Unable to mark worklistId={} as FAILED: {}", Worklist.getId(), error.getMessage());
        }
    }

    private void persistWorklistFailedInNewTransaction(WorklistDetailRow Worklist, Long actorId, String action, String errorMessage) {
        runInRequiresNewTransaction(() -> {
            Long hospitalId = (Worklist.getHospitalId() != null && Worklist.getHospitalId() > 0L)
                    ? Worklist.getHospitalId()
                    : currentHospitalId();
            WorklistDetailRow persistedWorklist = WorklistMapper.findWorklistById(hospitalId, Worklist.getId());
            if (persistedWorklist == null) {
                LOGGER.warn("Worklist failure tracking skipped because worklistId={} no longer exists.", Worklist.getId());
                return;
            }

            WorklistStatus currentStatus = safeWorklistStatus(persistedWorklist.getStatus());
            if (currentStatus == WorklistStatus.CANCELLED) {
                return;
            }

            Long modifiedBy = actorId != null ? actorId : currentUserId();
            String resolvedMessage = firstNonBlank(errorMessage, "Worklist workflow failed.");
            int updated = WorklistMapper.updateWorklistWorkflowStatusById(
                    hospitalId,
                    persistedWorklist.getId(),
                    WorklistStatus.FAILED.code(),
                    resolvedMessage,
                    modifiedBy
            );
            if (updated <= 0) {
                LOGGER.warn("Worklist failure tracking updated 0 rows for worklistId={} hospitalId={}", persistedWorklist.getId(), hospitalId);
                return;
            }

            if (currentStatus != WorklistStatus.FAILED) {
                WorklistMapper.insertHistory(
                        hospitalId,
                        persistedWorklist.getId(),
                        persistedWorklist.getPatientId(),
                        currentStatus.code(),
                        WorklistStatus.FAILED.code(),
                        firstNonBlank(action, WorklistConstants.ACTION_UPDATE_STATUS),
                        resolvedMessage,
                        modifiedBy
                );
            }

            Worklist.setStatus(WorklistStatus.FAILED.name());
            Worklist.setErrorMessage(resolvedMessage);
        });
    }

    private void runInRequiresNewTransaction(Runnable action) {
        if (action == null) {
            return;
        }
        if (transactionManager == null) {
            action.run();
            return;
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    private ResponseMessage<BaseResult> WorklistDicomServerClientError(
            LocalTime startDuration,
            HttpServletRequest httpServletRequest,
            String endpointPath,
            String action,
            String message,
            Exception error
    ) throws UnknownHostException {
        LocalTime endDuration = LocalTime.now();
        Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
        activityLogService.insert(ApiConstants.Worklist.BASE_PATH + endpointPath, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (" + action + ")", action, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
        return ResponseMessageUtils.makeResponse(false, messageService.message(message, false));
    }

    private ResponseMessage<BaseResult> WorklistDicomServerUnexpectedError(
            LocalTime startDuration,
            HttpServletRequest httpServletRequest,
            String endpointPath,
            String action,
            String message,
            Exception error
    ) throws UnknownHostException {
        LOGGER.error("Worklist-worklist action {} failed: {}", action, error.toString(), error);
        LocalTime endDuration = LocalTime.now();
        Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
        activityLogService.insert(ApiConstants.Worklist.BASE_PATH + endpointPath, errorLine, error.toString(), WorklistConstants.MODULE_CODE, "Worklist (" + action + ")", action, WorklistConstants.LOG_STATUS_ERROR, WorklistConstants.RESULT_ERROR, startDuration, endDuration, httpServletRequest);
        return ResponseMessageUtils.makeResponse(false, messageService.message(message, false));
    }

    private String findDicomServerStudyIdByStudyInstanceUid(String studyInstanceUid, HospitalDicomServerResponse server) {
        DicomServerFindRequest findRequest = new DicomServerFindRequest();
        findRequest.setLevel("Study");
        Map<String, String> query = new HashMap<>();
        query.put(DicomTagConstants.STUDY_INSTANCE_UID, studyInstanceUid.trim());
        findRequest.setQuery(query);
        List<String> studyIds = findDicomServerStudyIds(findRequest, server);
        if (studyIds == null || studyIds.isEmpty()) {
            return "";
        }
        return firstNonBlank(studyIds.get(0), "");
    }

    private String findDicomServerStudyIdByAccessionNumber(String accessionNumber, HospitalDicomServerResponse server) {
        DicomServerFindRequest findRequest = new DicomServerFindRequest();
        findRequest.setLevel("Study");
        Map<String, String> query = new HashMap<>();
        query.put(DicomTagConstants.ACCESSION_NUMBER, accessionNumber.trim());
        findRequest.setQuery(query);
        List<String> studyIds = findDicomServerStudyIds(findRequest, server);
        if (studyIds == null || studyIds.isEmpty()) {
            return "";
        }
        return firstNonBlank(studyIds.get(0), "");
    }

    private List<String> findDicomServerStudyIds(DicomServerFindRequest findRequest, HospitalDicomServerResponse server) {
        if (server == null) {
            return Collections.emptyList();
        }
        return dicomServerClientService.findStudyIdsByAccessionNumber(
                resolveDicomServerBaseUrl(server),
                server.getUsername(),
                server.getPassword(),
                findRequest
        );
    }

    private String buildViewerUrl(String studyId, HospitalDicomServerResponse server) {
        if (!hasText(studyId)) {
            return null;
        }
        String publicUiBaseUrl = resolvePublicDicomServerUiBaseUrl(server);
        if (hasText(publicUiBaseUrl)) {
            return publicUiBaseUrl + "/app/explorer.html#study?uuid=" + studyId.trim();
        }
        return null;
    }

    private Long resolveWorklistId(WorklistSendToPacsRequest request) {
        if (request == null) {
            return null;
        }
        String key = firstNonBlank(request.getWorklistKey(), request.getPublicKey());
        Long resolvedId = publicEntityKeyResolver.resolve(Entity.WORKLIST, key, null);
        if (resolvedId != null && resolvedId > 0L) {
            return resolvedId;
        }
        return request.getWorklistId();
    }

    private Long resolveWorklistId(WorklistViewStudyRequest request) {
        if (request == null) {
            return null;
        }
        String key = firstNonBlank(request.getWorklistKey(), request.getPublicKey());
        return publicEntityKeyResolver.resolve(Entity.WORKLIST, key, request.getWorklistId());
    }

    private WorklistDetailRow findWorklistByIdentifier(Long hospitalId, Long worklistId, String visitCode) {
        if (worklistId != null && worklistId > 0) {
            if (hospitalId == null && isAdminUser()) {
                return WorklistMapper.findWorklistByIdAnyHospital(worklistId);
            }
            return WorklistMapper.findWorklistById(hospitalId, worklistId);
        }
        if (visitCode != null && !visitCode.trim().isEmpty()) {
            if (hospitalId == null && isAdminUser()) {
                return WorklistMapper.findWorklistByVisitCodeAnyHospital(visitCode.trim());
            }
            return WorklistMapper.findWorklistByVisitCode(hospitalId, visitCode.trim());
        }
        return null;
    }

    private static Long currentHospitalId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal != null && principal.hospitalId() != null) {
            return principal.hospitalId();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long fromDetails = extractLongFromAuthDetails(authentication, "hospitalId");
        if (fromDetails != null) {
            return fromDetails;
        }
        throw new IllegalStateException("Hospital context not found in authentication.");
    }

    private static Long currentUserId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal != null) {
            return principal.userId();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long fromDetails = extractLongFromAuthDetails(authentication, "userId");
        if (fromDetails != null) {
            return fromDetails;
        }
        return null;
    }

    private static String currentUsername() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal != null && principal.username() != null && !principal.username().isBlank()) {
            return principal.username();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
    }

    private static Long extractLongFromAuthDetails(Authentication authentication, String key) {
        if (authentication == null || authentication.getDetails() == null || key == null || key.isBlank()) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof CurrentUserPrincipal currentUserPrincipal) {
            return switch (key) {
                case "hospitalId" -> currentUserPrincipal.hospitalId();
                case "userId" -> currentUserPrincipal.userId();
                default -> null;
            };
        }
        if (details instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null) {
                try {
                    return Long.parseLong(String.valueOf(value).trim());
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
        try {
            Method method = details.getClass().getMethod(key);
            Object value = method.invoke(details);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null) {
                return Long.parseLong(String.valueOf(value).trim());
            }
        } catch (Exception ignored) {
            // Best-effort extraction only.
        }
        return null;
    }

    private static String normalizeStatus(String rawStatus) {
        return rawStatus;
    }

    private static WorklistStatus safeWorklistStatus(String status) {
        try {
            return WorklistStatus.fromValue(normalizeStatus(status));
        } catch (Exception ignored) {
            return WorklistStatus.WAITING;
        }
    }

    private record LinkedStudyContext(
            Long studyId,
            String dicomServerStudyId,
            String studyInstanceUid,
            String dicomServerPatientId,
            String dicomServerSeriesId,
            String viewerUrl
    ) {
    }

    private static String toWorklistEndpoint(WorklistStatus status) {
        if (status == WorklistStatus.CANCELLED) return ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.CANCEL_PATH;
        return ApiConstants.Worklist.BASE_PATH + "/worklist-update-status";
    }

    private static String toWorklistAction(WorklistStatus status) {
        if (status == WorklistStatus.CANCELLED) return WorklistConstants.ACTION_CANCEL;
        return WorklistConstants.ACTION_UPDATE_STATUS;
    }

    private static Long resolveHospitalId(Long requestedHospitalId) {
        if (requestedHospitalId != null && requestedHospitalId > 0 && isAdminUser()) {
            return requestedHospitalId;
        }
        return currentHospitalId();
    }

    private static Long resolveOptionalHospitalId(Long requestedHospitalId) {
        if (isAdminUser()) {
            return requestedHospitalId != null && requestedHospitalId > 0 ? requestedHospitalId : null;
        }
        return currentHospitalId();
    }

    private static WorklistActionResponse buildWorklistActionResponse(WorklistDetailRow Worklist, String studyUuid, String status, String message) {
        WorklistActionResponse response = new WorklistActionResponse();
        response.setWorklistId(Worklist.getId());
        response.setPublicKey(Worklist.getPublicKey());
        response.setVisitCode(Worklist.getVisitCode());
        response.setStudyUuid(studyUuid);
        response.setStatus(status);
        response.setMessage(message);
        return response;
    }
}
