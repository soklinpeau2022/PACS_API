package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.mapper.pacs.StudyRetentionMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.StudyRetentionPolicyFilter;
import com.ut.emrPacs.model.base.filter.StudyRetentionReviewFilter;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionDecisionRequest;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionPolicySaveRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionPolicyResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionReviewResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionSummaryResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import com.ut.emrPacs.service.service.StudyRetentionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.net.UnknownHostException;
import java.time.LocalTime;
import java.util.List;

import static com.ut.emrPacs.authentication.util.AuthorityUtils.isAdminUser;

@Service
public class StudyRetentionServiceImpl implements StudyRetentionService {

    private static final int DEFAULT_NOTIFY_BEFORE_DAYS = 14;

    @Autowired
    private StudyRetentionMapper studyRetentionMapper;
    @Autowired
    private DicomServerMapper dicomServerMapper;
    @Autowired
    private DicomServerClientService dicomServerClientService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    public ResponseMessage<BaseResult> listPolicies(StudyRetentionPolicyFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            StudyRetentionPolicyFilter safeFilter = filter == null ? new StudyRetentionPolicyFilter() : filter;
            Long hospitalId = resolveScopedHospitalId(safeFilter.getHospitalKey(), safeFilter.getHospitalId(), true);
            safeFilter.setHospitalId(hospitalId);
            safeFilter.setDicomServerId(publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, safeFilter.getDicomServerKey(), safeFilter.getDicomServerId()));
            safeFilter.setModalityId(publicEntityKeyResolver.resolve(Entity.MODALITY, safeFilter.getModalityKey(), safeFilter.getModalityId()));

            Pagination pagination = PaginationHelper.buildAndApplyOffset(safeFilter, studyRetentionMapper.countPolicies(hospitalId, safeFilter));
            List<StudyRetentionPolicyResponse> rows = studyRetentionMapper.listPolicies(hospitalId, safeFilter);

            logSuccess(ApiConstants.StudyRetention.POLICY_LIST_PATH, "Study Retention Policy (List)", "View", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", rows, pagination, true));
        } catch (Exception error) {
            logError(ApiConstants.StudyRetention.POLICY_LIST_PATH, "Study Retention Policy (List)", "View", startDuration, error, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> savePolicy(StudyRetentionPolicySaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (!isAdminUser()) {
                return ResponseMessageUtils.makeResponse(false, 403, "Forbidden", "Only Admin users can update retention policy.");
            }
            if (request == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Invalid request.", false));
            }
            normalizePolicyRequest(request);

            Long policyId = publicEntityKeyResolver.resolve(Entity.STUDY_RETENTION_POLICY, request.getPublicKey(), request.getId());
            request.setId(policyId);
            Long hospitalId = resolveScopedHospitalId(request.getHospitalKey(), request.getHospitalId(), true);
            request.setHospitalId(hospitalId);
            request.setDicomServerId(publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, request.getDicomServerKey(), request.getDicomServerId()));
            request.setModalityId(publicEntityKeyResolver.resolve(Entity.MODALITY, request.getModalityKey(), request.getModalityId()));
            validatePolicyRequest(request);

            if (request.getDicomServerId() != null && request.getHospitalId() != null
                    && studyRetentionMapper.countDicomServerInHospital(request.getHospitalId(), request.getDicomServerId()) <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("DICOM server does not belong to the selected hospital.", false));
            }

            if (studyRetentionMapper.countDuplicatePolicyScope(request.getHospitalId(), request.getDicomServerId(), request.getModalityId(), request.getId()) > 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("A retention policy already exists for this hospital, DICOM server, and modality scope.", false));
            }

            if (request.getId() == null) {
                studyRetentionMapper.insertPolicy(request, currentUserId());
            } else {
                Long editableHospitalId = isSuperAdmin() ? null : currentHospitalId();
                StudyRetentionPolicyResponse existing = studyRetentionMapper.findPolicyById(request.getId(), editableHospitalId);
                if (existing == null) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("Study retention policy not found.", false));
                }
                studyRetentionMapper.updatePolicy(request, currentUserId());
            }

            StudyRetentionPolicyResponse saved = studyRetentionMapper.findPolicyById(request.getId(), null);
            logSuccess(ApiConstants.StudyRetention.POLICY_SAVE_PATH, "Study Retention Policy (Save)", "Edit", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", saved == null ? List.of() : List.of(saved), true));
        } catch (IllegalArgumentException | IllegalStateException validationError) {
            logError(ApiConstants.StudyRetention.POLICY_SAVE_PATH, "Study Retention Policy (Save)", "Edit", startDuration, validationError, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            logError(ApiConstants.StudyRetention.POLICY_SAVE_PATH, "Study Retention Policy (Save)", "Edit", startDuration, error, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> deletePolicy(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (!isAdminUser()) {
                return ResponseMessageUtils.makeResponse(false, 403, "Forbidden", "Only Admin users can delete retention policy.");
            }
            Long hospitalId = isSuperAdmin() ? null : currentHospitalId();
            StudyRetentionPolicyResponse existing = studyRetentionMapper.findPolicyById(id, hospitalId);
            if (existing == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study retention policy not found.", false));
            }
            studyRetentionMapper.deletePolicy(id, hospitalId, currentUserId());

            logSuccess(ApiConstants.StudyRetention.POLICY_DELETE_PATH, "Study Retention Policy (Delete)", "Delete", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
        } catch (IllegalArgumentException | IllegalStateException validationError) {
            logError(ApiConstants.StudyRetention.POLICY_DELETE_PATH, "Study Retention Policy (Delete)", "Delete", startDuration, validationError, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            logError(ApiConstants.StudyRetention.POLICY_DELETE_PATH, "Study Retention Policy (Delete)", "Delete", startDuration, error, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> listReview(StudyRetentionReviewFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            StudyRetentionReviewFilter safeFilter = filter == null ? new StudyRetentionReviewFilter() : filter;
            Long hospitalId = resolveScopedHospitalId(safeFilter.getHospitalKey(), safeFilter.getHospitalId(), true);
            safeFilter.setHospitalId(hospitalId);
            safeFilter.setDicomServerId(publicEntityKeyResolver.resolve(Entity.DICOM_SERVER, safeFilter.getDicomServerKey(), safeFilter.getDicomServerId()));
            safeFilter.setModalityId(publicEntityKeyResolver.resolve(Entity.MODALITY, safeFilter.getModalityKey(), safeFilter.getModalityId()));

            Pagination pagination = PaginationHelper.buildAndApplyOffset(safeFilter, studyRetentionMapper.countReview(hospitalId, safeFilter));
            List<StudyRetentionReviewResponse> rows = studyRetentionMapper.listReview(hospitalId, safeFilter);

            logSuccess(ApiConstants.StudyRetention.REVIEW_LIST_PATH, "Study Retention Review (List)", "View", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", rows, pagination, true));
        } catch (Exception error) {
            logError(ApiConstants.StudyRetention.REVIEW_LIST_PATH, "Study Retention Review (List)", "View", startDuration, error, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> summary(StudyRetentionReviewFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            StudyRetentionReviewFilter safeFilter = filter == null ? new StudyRetentionReviewFilter() : filter;
            Long hospitalId = resolveScopedHospitalId(safeFilter.getHospitalKey(), safeFilter.getHospitalId(), true);
            StudyRetentionSummaryResponse summary = studyRetentionMapper.summary(hospitalId);

            logSuccess(ApiConstants.StudyRetention.SUMMARY_PATH, "Study Retention Summary", "View", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(summary), true));
        } catch (Exception error) {
            logError(ApiConstants.StudyRetention.SUMMARY_PATH, "Study Retention Summary", "View", startDuration, error, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> approveDelete(Long studyId, StudyRetentionDecisionRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            assertSuperAdmin("Only Super Admin can approve expired study deletion.");
            StudyRetentionReviewResponse candidate = studyRetentionMapper.findReviewCandidateByStudyId(null, studyId);
            if (candidate == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Expired study not found for retention review.", false));
            }
            if ("KEEP_PERMANENT".equals(candidate.getStatus())) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("This study is marked to keep permanently.", false));
            }
            if (!isDeletionReadyStatus(candidate.getStatus())) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study is not expired yet.", false));
            }
            if (!hasText(candidate.getDicomServerStudyId())) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("DICOM server study ID is missing. The study was not deleted.", false));
            }
            HospitalDicomServerResponse server = resolveDicomServer(candidate);
            Long requestId = studyRetentionMapper.createDeleteRequest(candidate, currentUserId());
            studyRetentionMapper.markRequestApproved(requestId, currentUserId(), decisionNote(request));

            try {
                dicomServerClientService.deleteStudyById(server.getBaseUrl(), server.getUsername(), server.getPassword(), candidate.getDicomServerStudyId());
            } catch (HttpClientErrorException.NotFound notFound) {
                // Already absent on the archive is acceptable for a hard-delete approval.
            } catch (Exception dicomError) {
                studyRetentionMapper.markRequestDeleteFailed(requestId, safeErrorMessage(dicomError));
                logError(ApiConstants.StudyRetention.APPROVE_DELETE_PATH, "Study Retention Approval (Approve Delete)", "Approve", startDuration, dicomError, httpServletRequest);
                return ResponseMessageUtils.makeResponse(false, messageService.message("DICOM server delete failed. Database data was kept for review.", false));
            }

            try {
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                transactionTemplate.executeWithoutResult(status -> {
                    Integer deleted = studyRetentionMapper.hardDeleteStudyData(candidate.getHospitalId(), candidate.getStudyId());
                    if (deleted == null || deleted <= 0) {
                        throw new IllegalStateException("Study database delete did not remove any row.");
                    }
                    studyRetentionMapper.markRequestDeleted(requestId, currentUserId());
                });
            } catch (Exception databaseError) {
                studyRetentionMapper.markRequestDeleteFailed(requestId, safeErrorMessage(databaseError));
                logError(ApiConstants.StudyRetention.APPROVE_DELETE_PATH, "Study Retention Approval (Approve Delete)", "Approve", startDuration, databaseError, httpServletRequest);
                return ResponseMessageUtils.makeResponse(false, messageService.message("DICOM server data was deleted, but database cleanup failed. Please review the failed request.", false));
            }

            logSuccess(ApiConstants.StudyRetention.APPROVE_DELETE_PATH, "Study Retention Approval (Approve Delete)", "Approve", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Study deleted from DICOM server and database.", true));
        } catch (IllegalArgumentException | IllegalStateException validationError) {
            logError(ApiConstants.StudyRetention.APPROVE_DELETE_PATH, "Study Retention Approval (Approve Delete)", "Approve", startDuration, validationError, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            logError(ApiConstants.StudyRetention.APPROVE_DELETE_PATH, "Study Retention Approval (Approve Delete)", "Approve", startDuration, error, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> rejectDelete(Long studyId, StudyRetentionDecisionRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            assertSuperAdmin("Only Super Admin can reject expired study deletion.");
            StudyRetentionReviewResponse candidate = studyRetentionMapper.findReviewCandidateByStudyId(null, studyId);
            if (candidate == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Expired study not found for retention review.", false));
            }
            if ("ACTIVE".equals(candidate.getStatus())) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study is not in retention review yet.", false));
            }
            Long requestId = studyRetentionMapper.createDeleteRequest(candidate, currentUserId());
            String status = request != null && Boolean.TRUE.equals(request.getKeepPermanent()) ? "KEEP_PERMANENT" : "REJECTED";
            studyRetentionMapper.markRequestRejected(requestId, currentUserId(), status, decisionNote(request));

            logSuccess(ApiConstants.StudyRetention.REJECT_DELETE_PATH, "Study Retention Approval (Reject Delete)", "Approve", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Study deletion was rejected.", true));
        } catch (IllegalArgumentException | IllegalStateException validationError) {
            logError(ApiConstants.StudyRetention.REJECT_DELETE_PATH, "Study Retention Approval (Reject Delete)", "Approve", startDuration, validationError, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            logError(ApiConstants.StudyRetention.REJECT_DELETE_PATH, "Study Retention Approval (Reject Delete)", "Approve", startDuration, error, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private void normalizePolicyRequest(StudyRetentionPolicySaveRequest request) {
        if (request.getRetentionDays() == null) {
            request.setRetentionDays(365);
        }
        if (request.getNotifyBeforeDays() == null) {
            request.setNotifyBeforeDays(DEFAULT_NOTIFY_BEFORE_DAYS);
        }
        if (request.getRequireApproval() == null) {
            request.setRequireApproval(Boolean.TRUE);
        }
        if (request.getEnabled() == null) {
            request.setEnabled(Boolean.TRUE);
        }
    }

    private void validatePolicyRequest(StudyRetentionPolicySaveRequest request) {
        if (request.getRetentionDays() == null || request.getRetentionDays() < 1 || request.getRetentionDays() > 3650) {
            throw new IllegalArgumentException("Retention days must be between 1 and 3650.");
        }
        if (request.getNotifyBeforeDays() == null || request.getNotifyBeforeDays() < 0 || request.getNotifyBeforeDays() > 365) {
            throw new IllegalArgumentException("Notify-before days must be between 0 and 365.");
        }
        if (request.getNotifyBeforeDays() > request.getRetentionDays()) {
            throw new IllegalArgumentException("Notify-before days cannot be greater than retention days.");
        }
    }

    private HospitalDicomServerResponse resolveDicomServer(StudyRetentionReviewResponse candidate) {
        if (candidate.getDicomServerId() == null || candidate.getDicomServerId() <= 0L) {
            throw new IllegalStateException("DICOM server is not linked to this study.");
        }
        List<HospitalDicomServerResponse> rows = dicomServerMapper.getDicomServerById(candidate.getDicomServerId(), candidate.getHospitalId());
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("DICOM server is not available for this study.");
        }
        HospitalDicomServerResponse server = rows.get(0);
        if (!hasText(server.getBaseUrl())) {
            throw new IllegalStateException("DICOM server base URL is not configured.");
        }
        return server;
    }

    private static boolean isDeletionReadyStatus(String status) {
        return "EXPIRED_WAITING_APPROVAL".equals(status)
                || "PENDING_APPROVAL".equals(status)
                || "DELETE_FAILED".equals(status)
                || "REJECTED".equals(status);
    }

    private Long resolveScopedHospitalId(String requestedHospitalKey, Long requestedHospitalId, boolean allowAllHospitalsForSuperAdmin) {
        Long resolvedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, requestedHospitalKey, requestedHospitalId);
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null) {
            throw new IllegalStateException("User context not found in OAuth2 token claims.");
        }
        if (isSuperAdmin()) {
            if (resolvedHospitalId != null && resolvedHospitalId > 0) {
                return resolvedHospitalId;
            }
            if (allowAllHospitalsForSuperAdmin) {
                return null;
            }
        }
        if (principal.hospitalId() != null && principal.hospitalId() > 0) {
            return principal.hospitalId();
        }
        throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
    }

    private static Long currentHospitalId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null || principal.hospitalId() <= 0) {
            throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
        }
        return principal.hospitalId();
    }

    private static Long currentUserId() {
        var principal = UserAuthSession.getCurrentUser();
        return principal == null ? null : principal.userId();
    }

    private static boolean isSuperAdmin() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal != null && principal.userId() != null && principal.userId() == 1L) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if ("ROLE_SUPER_ADMIN".equals(value) || "ROLE_SYSTEM_ADMIN".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static void assertSuperAdmin(String message) {
        if (!isSuperAdmin()) {
            throw new IllegalStateException(message);
        }
    }

    private static String decisionNote(StudyRetentionDecisionRequest request) {
        return request == null ? null : request.getNote();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safeErrorMessage(Exception error) {
        if (error == null) {
            return null;
        }
        String message = error.getMessage();
        if (!hasText(message)) {
            message = error.getClass().getSimpleName();
        }
        String sanitized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private void logSuccess(String path, String title, String action, LocalTime startDuration, HttpServletRequest request) throws UnknownHostException {
        activityLogService.insert(
                ApiConstants.StudyRetention.BASE_PATH + path,
                null,
                null,
                "StudyRetention",
                title,
                action,
                1,
                "Success",
                startDuration,
                LocalTime.now(),
                request
        );
    }

    private void logError(String path, String title, String action, LocalTime startDuration, Exception error, HttpServletRequest request) throws UnknownHostException {
        Long errorLine = error.getStackTrace() != null && error.getStackTrace().length > 0
                ? (long) error.getStackTrace()[0].getLineNumber()
                : null;
        activityLogService.insert(
                ApiConstants.StudyRetention.BASE_PATH + path,
                errorLine,
                error.toString(),
                "StudyRetention",
                title,
                action,
                2,
                "Error",
                startDuration,
                LocalTime.now(),
                request
        );
    }
}
