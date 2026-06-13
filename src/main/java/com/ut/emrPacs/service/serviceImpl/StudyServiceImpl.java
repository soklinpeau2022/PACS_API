package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.authentication.util.JwtTokenService;
import com.ut.emrPacs.authentication.util.ViewerAccessKeyService;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.StudyListFilter;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.ViewerInfoResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.StudyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static com.ut.emrPacs.authentication.util.AuthorityUtils.isAdminUser;

import java.net.UnknownHostException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

@Service
public class StudyServiceImpl implements StudyService {

    @Autowired
    private StudyMapper studyMapper;
    @Autowired
    private DicomServerMapper dicomServerMapper;
    @Autowired(required = false)
    private ViewerAccessKeyService viewerAccessKeyService;
    @Autowired(required = false)
    private JwtTokenService jwtTokenService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @Value("${pacs.viewer.dicomweb.token-ms:86400000}")
    private long viewerDicomwebTokenMs;

    @Override
    public ResponseMessage<BaseResult> list(StudyListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            StudyListFilter safeFilter = filter != null ? filter : new StudyListFilter();
            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, safeFilter.getHospitalKey(), null);
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            Long modalityId = publicEntityKeyResolver.resolve(Entity.MODALITY, safeFilter.getModalityKey(), null);
            safeFilter.setHospitalId(hospitalId);
            safeFilter.setModalityId(modalityId);
            Pagination pagination = PaginationHelper.buildAndApplyOffset(safeFilter, studyMapper.count(hospitalId, safeFilter));

            List<StudyResponse> studies = studyMapper.list(hospitalId, safeFilter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Study.BASE_PATH + ApiConstants.Study.LIST_PATH, null, null, "Study", "Study (List)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(
                    "Success",
                    studies,
                    pagination,
                    true
            ));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Study.BASE_PATH + ApiConstants.Study.LIST_PATH, errorLine, error.toString(), "Study", "Study (List)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> findById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var study = studyMapper.findById(currentHospitalId(), id);
            if (study == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study not found.", false));
            }
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Study.BASE_PATH + ApiConstants.Study.FIND_PATH, null, null, "Study", "Study (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(study), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Study.BASE_PATH + ApiConstants.Study.FIND_PATH, errorLine, error.toString(), "Study", "Study (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getViewerInfo(Long id, Long requestedHospitalId, String mode, String requestedViewerAccess, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            StudyResponse study = studyMapper.findById(hospitalId, id);
            if (study == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study not found.", false));
            }
            if (!hasText(study.getStudyInstanceUid())) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study Instance UID is not available for this study.", false));
            }

            HospitalDicomServerResponse server = resolveStudyDicomServer(study, hospitalId);
            String viewerBaseUrl = resolvePublicViewerBaseUrl(server);
            if (!hasText(viewerBaseUrl)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Viewer base URL is not configured for this DICOM server.", false));
            }
            String directDicomwebBaseUrl = resolvePublicDicomwebBaseUrl(server);
            String viewerDicomwebBaseUrl = appendPublicPath(viewerBaseUrl, "/pacs-dicomweb");
            String dicomwebBaseUrl = hasText(viewerDicomwebBaseUrl) ? viewerDicomwebBaseUrl : directDicomwebBaseUrl;
            if (!hasText(dicomwebBaseUrl)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("DICOMweb base URL is not configured for this DICOM server.", false));
            }

            String viewerAccess = ViewerAccessKeyService.normalizeAccessMode(
                    hasText(requestedViewerAccess) ? requestedViewerAccess : ViewerAccessKeyService.ACCESS_READ
            );
            if (ViewerAccessKeyService.ACCESS_EDIT.equals(viewerAccess)) {
                viewerAccess = ViewerAccessKeyService.ACCESS_READ;
            }
            String viewerApiKey = issueViewerApiKey(study, viewerAccess);
            String dicomwebAuthToken = issueViewerDicomwebToken(study);

            ViewerInfoResponse response = new ViewerInfoResponse();
            response.setSuccess(Boolean.TRUE);
            response.setDirectDicomweb(Boolean.FALSE);
            response.setPublicKey(study.getPublicKey());
            response.setHospitalPublicKey(study.getHospitalPublicKey());
            response.setStudyPublicKey(study.getPublicKey());
            response.setStatus(study.getStatus());
            response.setDicomServerStudyId(study.getDicomServerStudyId());
            response.setDicomServerPatientId(study.getDicomServerPatientId());
            response.setDicomServerSeriesId(study.getDicomServerSeriesId());
            response.setStudyInstanceUid(study.getStudyInstanceUid());
            response.setAccessionNumber(study.getAccessionNumber());
            response.setPatientUid(study.getMrn());
            response.setPatientName(study.getPatientName());
            response.setModalityName(study.getModality());
            response.setStudyDescription(study.getStudyDescription());
            response.setImageReceivedAt(study.getImageReceivedAt());
            response.setImageInstanceCount(study.getInstances());
            response.setTotalInstances(study.getInstances());
            response.setSeriesCount(hasText(study.getDicomServerSeriesId()) ? 1 : 0);
            response.setViewerBaseUrl(viewerBaseUrl);
            response.setDicomwebBaseUrl(dicomwebBaseUrl);
            response.setDicomwebGatewayBaseUrl(null);
            response.setDicomwebAuthToken(dicomwebAuthToken);
            response.setViewerApiKey(viewerApiKey);
            response.setViewerAccess(viewerAccess);
            response.setCanEditResult(Boolean.FALSE);
            response.setCanEditViewerState(Boolean.FALSE);
            response.setDicomServerUiBaseUrl(resolvePublicDicomServerUiBaseUrl(server));
            response.setViewerUrl(buildOhifViewerUrl(study.getStudyInstanceUid(), mode, viewerBaseUrl, dicomwebBaseUrl, response, dicomwebAuthToken, viewerApiKey));
            response.setBasicViewerUrl(buildOhifViewerUrl(study.getStudyInstanceUid(), "basic", viewerBaseUrl, dicomwebBaseUrl, response, dicomwebAuthToken, viewerApiKey));
            response.setSegmentationViewerUrl(buildOhifViewerUrl(study.getStudyInstanceUid(), "segmentation", viewerBaseUrl, dicomwebBaseUrl, response, dicomwebAuthToken, viewerApiKey));

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Study.BASE_PATH + ApiConstants.Study.VIEWER_INFO_PATH, null, null, "Study", "Study (Viewer Info)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(response), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Study.BASE_PATH + ApiConstants.Study.VIEWER_INFO_PATH, errorLine, error.toString(), "Study", "Study (Viewer Info)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private static Long currentHospitalId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null) {
            throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
        }
        return principal.hospitalId();
    }

    private static Long resolveHospitalId(Long requestedHospitalId) {
        if (requestedHospitalId != null && requestedHospitalId > 0) {
            return isAdminUser() ? requestedHospitalId : currentHospitalId();
        }
        return currentHospitalId();
    }

    private HospitalDicomServerResponse resolveStudyDicomServer(StudyResponse study, Long hospitalId) {
        if (study != null && study.getDicomServerId() != null && study.getDicomServerId() > 0L) {
            List<HospitalDicomServerResponse> servers = dicomServerMapper.getDicomServerById(study.getDicomServerId(), hospitalId);
            if (servers != null && !servers.isEmpty()) {
                return servers.get(0);
            }
        }
        return dicomServerMapper.findPrimaryActiveDicomServerByHospital(hospitalId);
    }

    private String issueViewerApiKey(StudyResponse study, String viewerAccess) {
        if (viewerAccessKeyService == null || study == null || !hasText(study.getStudyInstanceUid())) {
            return null;
        }
        return viewerAccessKeyService.issue(
                study.getHospitalId(),
                null,
                study.getId(),
                null,
                study.getStudyInstanceUid(),
                currentUserId(),
                currentUsername(),
                viewerAccess
        );
    }

    private String issueViewerDicomwebToken(StudyResponse study) {
        if (jwtTokenService == null || study == null || !hasText(study.getStudyInstanceUid())) {
            return null;
        }
        var tokenResponse = jwtTokenService.issueViewerDicomwebToken(
                study.getHospitalId(),
                null,
                study.getId(),
                study.getStudyInstanceUid().trim(),
                viewerDicomwebTokenMs
        );
        return tokenResponse == null ? null : tokenResponse.getAccessToken();
    }

    private static Long currentUserId() {
        var principal = UserAuthSession.getCurrentUser();
        return principal == null ? null : principal.userId();
    }

    private static String currentUsername() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal != null && principal.username() != null && !principal.username().isBlank()) {
            return principal.username();
        }
        return null;
    }

    private String resolvePublicViewerBaseUrl(HospitalDicomServerResponse server) {
        return normalizePublicBaseUrl(server == null ? null : server.getViewerBaseUrl());
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

    private String resolvePublicDicomwebBaseUrl(HospitalDicomServerResponse server) {
        String configuredDicomwebBaseUrl = normalizePublicBaseUrl(server == null ? null : server.getDicomwebBaseUrl());
        if (hasText(configuredDicomwebBaseUrl)) {
            return configuredDicomwebBaseUrl;
        }
        String uiBaseUrl = resolvePublicDicomServerUiBaseUrl(server);
        return hasText(uiBaseUrl) ? appendPublicPath(uiBaseUrl, "/dicom-web") : null;
    }

    private String resolveDicomServerBaseUrl(HospitalDicomServerResponse server) {
        String configuredBaseUrl = normalizePublicBaseUrl(server == null ? null : server.getBaseUrl());
        if (hasText(configuredBaseUrl)) {
            return configuredBaseUrl;
        }
        String ipAddress = firstNonBlank(server == null ? null : server.getIpAddress(), "");
        Integer port = server == null ? null : server.getPort();
        if (!hasText(ipAddress) || port == null || port <= 0) {
            return null;
        }
        String protocol = ipAddress.startsWith("https://") || ipAddress.startsWith("http://") ? "" : "http://";
        return normalizePublicBaseUrl(protocol + ipAddress.replaceAll("/+$", "") + ":" + port);
    }

    private String buildOhifViewerUrl(String studyInstanceUid, String mode, String viewerBaseUrl, String dicomwebBaseUrl, ViewerInfoResponse context, String dicomwebAuthToken, String viewerApiKey) {
        if (!hasText(studyInstanceUid) || !hasText(viewerBaseUrl)) {
            return null;
        }
        String viewerRouteUrl = appendPublicPath(viewerBaseUrl, normalizeViewerRoute(mode));
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

        StringBuilder fragment = new StringBuilder();
        appendViewerFragmentParam(fragment, "token", dicomwebAuthToken);
        appendViewerFragmentParam(fragment, "viewerAccessToken", viewerApiKey);
        appendViewerFragmentParam(fragment, "viewerAccess", context == null ? ViewerAccessKeyService.ACCESS_READ : context.getViewerAccess());
        appendViewerFragmentParam(fragment, "canEditResult", "0");
        appendViewerFragmentParam(fragment, "canEditViewerState", "0");
        if (!fragment.isEmpty()) {
            builder.append("#").append(fragment);
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
            default -> "viewer";
        };
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

    private String normalizePublicBaseUrl(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("/+$", "");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
