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
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionAutoDeleteRequest;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionBulkDeleteRequest;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionDecisionRequest;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionPolicySaveRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionBulkDeleteResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionDeleteItemResult;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionPolicyResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionReviewResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionSummaryResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DicomServerClientService;
import com.ut.emrPacs.service.service.StudyRetentionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.net.UnknownHostException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StudyRetentionServiceImpl implements StudyRetentionService {

    private static final int DEFAULT_NOTIFY_BEFORE_DAYS = 14;
    private static final int DEFAULT_RETENTION_VALUE = 1;
    private static final String DEFAULT_RETENTION_UNIT = "YEAR";
    private static final int MAX_RETENTION_DAYS = 3650;
    private static final int DEFAULT_DELETE_CHUNK_SIZE = 25;
    private static final int MAX_DELETE_CHUNK_SIZE = 100;
    private static final int MAX_BULK_DELETE_ITEMS = 500;
    private static final int DEFAULT_AUTO_DELETE_MAX_ITEMS = 100;

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

    @Value("${study.retention.auto-delete.enabled:true}")
    private boolean autoDeleteSchedulerEnabled;
    @Value("${study.retention.auto-delete.chunk-size:25}")
    private int scheduledAutoDeleteChunkSize;
    @Value("${study.retention.auto-delete.max-items:100}")
    private int scheduledAutoDeleteMaxItems;

    @Scheduled(
            initialDelayString = "${study.retention.auto-delete.initial-delay-ms:60000}",
            fixedDelayString = "${study.retention.auto-delete.fixed-delay-ms:300000}"
    )
    public void runScheduledAutoDelete() {
        if (!autoDeleteSchedulerEnabled) {
            return;
        }
        try {
            executeAutoDelete(null, sanitizeAutoDeleteLimit(scheduledAutoDeleteMaxItems), sanitizeChunkSize(scheduledAutoDeleteChunkSize), "SCHEDULED");
        } catch (Exception error) {
            // Scheduler must not stop future cleanup runs because one execution failed.
        }
    }

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
    public ResponseMessage<BaseResult> findPolicy(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long hospitalId = isSuperAdmin() ? null : currentHospitalId();
            StudyRetentionPolicyResponse existing = studyRetentionMapper.findPolicyById(id, hospitalId);
            if (existing == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study retention policy not found.", false));
            }

            logSuccess(ApiConstants.StudyRetention.POLICY_FIND_PATH, "Study Retention Policy (Find)", "View", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(existing), true));
        } catch (IllegalArgumentException | IllegalStateException validationError) {
            logError(ApiConstants.StudyRetention.POLICY_FIND_PATH, "Study Retention Policy (Find)", "View", startDuration, validationError, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            logError(ApiConstants.StudyRetention.POLICY_FIND_PATH, "Study Retention Policy (Find)", "View", startDuration, error, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> savePolicy(StudyRetentionPolicySaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
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
            if (request.getModalityId() != null && request.getHospitalId() != null
                    && studyRetentionMapper.countModalityInHospital(request.getHospitalId(), request.getModalityId()) <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Modality does not belong to the selected hospital.", false));
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

            Pagination pagination = PaginationHelper.buildAndApplyOffsetOrDefault(safeFilter);
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
            StudyRetentionDeleteItemResult result = deleteCandidate(candidate, decisionNote(request), "MANUAL");
            if (!"DELETED".equals(result.getResult())) {
                return ResponseMessageUtils.makeResponse(false, messageService.message(result.getMessage(), false));
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
    public ResponseMessage<BaseResult> bulkDelete(StudyRetentionBulkDeleteRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            assertSuperAdmin("Only Super Admin can bulk-delete expired studies.");
            List<String> keys = normalizeStudyPublicKeys(request == null ? null : request.getStudyPublicKeys());
            if (keys.isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Select at least one study to delete.", false));
            }
            int chunkSize = sanitizeChunkSize(request == null ? null : request.getChunkSize());
            StudyRetentionBulkDeleteResponse response = deletePublicKeysInChunks(keys, chunkSize, request == null ? null : request.getNote());

            logSuccess(ApiConstants.StudyRetention.BULK_DELETE_PATH, "Study Retention Approval (Bulk Delete)", "Approve", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Bulk delete completed.", List.of(response), true));
        } catch (IllegalArgumentException | IllegalStateException validationError) {
            logError(ApiConstants.StudyRetention.BULK_DELETE_PATH, "Study Retention Approval (Bulk Delete)", "Approve", startDuration, validationError, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            logError(ApiConstants.StudyRetention.BULK_DELETE_PATH, "Study Retention Approval (Bulk Delete)", "Approve", startDuration, error, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> runAutoDelete(StudyRetentionAutoDeleteRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            assertSuperAdmin("Only Super Admin can run auto-delete cleanup.");
            StudyRetentionAutoDeleteRequest safeRequest = request == null ? new StudyRetentionAutoDeleteRequest() : request;
            Long hospitalId = resolveScopedHospitalId(safeRequest.getHospitalKey(), safeRequest.getHospitalId(), true);
            int maxItems = sanitizeAutoDeleteLimit(safeRequest.getMaxItems());
            int chunkSize = sanitizeChunkSize(safeRequest.getChunkSize());
            StudyRetentionBulkDeleteResponse response = executeAutoDelete(hospitalId, maxItems, chunkSize, "MANUAL_AUTO_RUN");

            logSuccess(ApiConstants.StudyRetention.AUTO_DELETE_RUN_PATH, "Study Retention Auto Delete", "Approve", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Auto-delete run completed.", List.of(response), true));
        } catch (IllegalArgumentException | IllegalStateException validationError) {
            logError(ApiConstants.StudyRetention.AUTO_DELETE_RUN_PATH, "Study Retention Auto Delete", "Approve", startDuration, validationError, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message(validationError.getMessage(), false));
        } catch (Exception error) {
            logError(ApiConstants.StudyRetention.AUTO_DELETE_RUN_PATH, "Study Retention Auto Delete", "Approve", startDuration, error, httpServletRequest);
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

    private StudyRetentionBulkDeleteResponse deletePublicKeysInChunks(List<String> keys, int chunkSize, String note) {
        StudyRetentionBulkDeleteResponse response = newBulkResponse("BULK_DELETE", keys.size(), chunkSize);
        for (List<String> chunk : partition(keys, chunkSize)) {
            List<StudyRetentionReviewResponse> candidates = studyRetentionMapper.listReviewCandidatesByStudyPublicKeys(null, chunk);
            Map<String, StudyRetentionReviewResponse> candidateByKey = new LinkedHashMap<>();
            if (candidates != null) {
                for (StudyRetentionReviewResponse candidate : candidates) {
                    if (hasText(candidate.getStudyPublicKey())) {
                        candidateByKey.put(candidate.getStudyPublicKey().trim().toLowerCase(Locale.ROOT), candidate);
                    }
                }
            }
            for (String key : chunk) {
                StudyRetentionReviewResponse candidate = candidateByKey.get(key.toLowerCase(Locale.ROOT));
                if (candidate == null) {
                    response.addResult(StudyRetentionDeleteItemResult.missing(key, "Study is not available in the retention review queue."));
                    continue;
                }
                response.addResult(deleteCandidate(candidate, note, "BULK"));
            }
        }
        return response;
    }

    private StudyRetentionBulkDeleteResponse executeAutoDelete(Long hospitalId, int maxItems, int chunkSize, String mode) {
        List<StudyRetentionReviewResponse> candidates = studyRetentionMapper.listAutoDeleteCandidates(hospitalId, maxItems);
        List<StudyRetentionReviewResponse> safeCandidates = candidates == null ? List.of() : candidates;
        StudyRetentionBulkDeleteResponse response = newBulkResponse(mode == null ? "AUTO_DELETE" : mode, safeCandidates.size(), chunkSize);
        for (List<StudyRetentionReviewResponse> chunk : partition(safeCandidates, chunkSize)) {
            for (StudyRetentionReviewResponse candidate : chunk) {
                response.addResult(deleteCandidate(candidate, "Auto-delete expired study by retention policy.", "AUTO"));
            }
        }
        return response;
    }

    private StudyRetentionDeleteItemResult deleteCandidate(StudyRetentionReviewResponse candidate, String note, String mode) {
        if (candidate == null) {
            return StudyRetentionDeleteItemResult.missing(null, "Study is not available in the retention review queue.");
        }
        if ("KEEP_PERMANENT".equals(candidate.getStatus())) {
            return StudyRetentionDeleteItemResult.of(candidate, "SKIPPED", "This study is marked to keep permanently.");
        }
        if (!isDeletionReadyStatus(candidate.getStatus())) {
            return StudyRetentionDeleteItemResult.of(candidate, "SKIPPED", "Study is not expired yet.");
        }
        if ("AUTO".equals(mode) && !"AUTO_DELETE_READY".equals(candidate.getStatus())) {
            return StudyRetentionDeleteItemResult.of(candidate, "SKIPPED", "Study is not configured for auto-delete.");
        }

        Long requestId = null;
        try {
            requestId = studyRetentionMapper.createDeleteRequest(candidate, currentUserId());
            studyRetentionMapper.markRequestApproved(requestId, currentUserId(), firstNonBlank(note, deleteNoteForMode(mode)));

            if (!hasText(candidate.getDicomServerStudyId())) {
                return markDeleteFailed(candidate, requestId, "DICOM server study ID is missing. The study was not deleted.");
            }

            HospitalDicomServerResponse server;
            try {
                server = resolveDicomServer(candidate);
            } catch (Exception serverError) {
                return markDeleteFailed(candidate, requestId, safeErrorMessage(serverError));
            }

            try {
                dicomServerClientService.deleteStudyById(server.getBaseUrl(), server.getUsername(), server.getPassword(), candidate.getDicomServerStudyId());
            } catch (HttpClientErrorException.NotFound notFound) {
                // Already absent on the archive is acceptable for hard-delete cleanup.
            } catch (Exception dicomError) {
                return markDeleteFailed(candidate, requestId, "DICOM server delete failed. Database data was kept for review.");
            }

            try {
                Long finalRequestId = requestId;
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                transactionTemplate.executeWithoutResult(status -> {
                    Integer deleted = studyRetentionMapper.hardDeleteStudyData(candidate.getHospitalId(), candidate.getStudyId());
                    if (deleted == null || deleted <= 0) {
                        throw new IllegalStateException("Study database delete did not remove any row.");
                    }
                    studyRetentionMapper.markRequestDeleted(finalRequestId, currentUserId());
                });
            } catch (Exception databaseError) {
                return markDeleteFailed(candidate, requestId, "DICOM server data was deleted, but database cleanup failed. Please review the failed request.");
            }

            return StudyRetentionDeleteItemResult.of(candidate, "DELETED", "Study deleted from DICOM server and database.");
        } catch (Exception error) {
            if (requestId != null) {
                studyRetentionMapper.markRequestDeleteFailed(requestId, safeErrorMessage(error));
            }
            return StudyRetentionDeleteItemResult.of(candidate, "FAILED", "Study deletion failed. Please review the failed request.");
        }
    }

    private StudyRetentionDeleteItemResult markDeleteFailed(StudyRetentionReviewResponse candidate, Long requestId, String message) {
        studyRetentionMapper.markRequestDeleteFailed(requestId, safeErrorMessage(new IllegalStateException(message)));
        return StudyRetentionDeleteItemResult.of(candidate, "FAILED", message);
    }

    private StudyRetentionBulkDeleteResponse newBulkResponse(String mode, int requested, int chunkSize) {
        StudyRetentionBulkDeleteResponse response = new StudyRetentionBulkDeleteResponse();
        response.setMode(mode);
        response.setRequested(requested);
        response.setProcessed(0);
        response.setDeleted(0);
        response.setFailed(0);
        response.setSkipped(0);
        response.setChunkSize(chunkSize);
        response.setChunks(requested == 0 ? 0 : (int) Math.ceil((double) requested / (double) chunkSize));
        return response;
    }

    private static List<String> normalizeStudyPublicKeys(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String key : input) {
            if (!hasText(key)) {
                continue;
            }
            unique.add(key.trim().toLowerCase(Locale.ROOT));
        }
        if (unique.size() > MAX_BULK_DELETE_ITEMS) {
            throw new IllegalArgumentException("Cannot delete more than " + MAX_BULK_DELETE_ITEMS + " studies in one request.");
        }
        return new ArrayList<>(unique);
    }

    private static int sanitizeChunkSize(Integer chunkSize) {
        if (chunkSize == null || chunkSize < 1) {
            return DEFAULT_DELETE_CHUNK_SIZE;
        }
        return Math.min(chunkSize, MAX_DELETE_CHUNK_SIZE);
    }

    private static int sanitizeAutoDeleteLimit(Integer maxItems) {
        if (maxItems == null || maxItems < 1) {
            return DEFAULT_AUTO_DELETE_MAX_ITEMS;
        }
        return Math.min(maxItems, MAX_BULK_DELETE_ITEMS);
    }

    private static <T> List<List<T>> partition(List<T> values, int chunkSize) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += chunkSize) {
            chunks.add(values.subList(start, Math.min(start + chunkSize, values.size())));
        }
        return chunks;
    }

    private void normalizePolicyRequest(StudyRetentionPolicySaveRequest request) {
        if (request.getRetentionValue() == null) {
            request.setRetentionValue(request.getRetentionDays() == null ? DEFAULT_RETENTION_VALUE : request.getRetentionDays());
        }
        request.setRetentionUnit(normalizeRetentionUnit(request.getRetentionUnit()));
        if (request.getRetentionDays() == null) {
            request.setRetentionDays(toApproximateRetentionDays(request.getRetentionValue(), request.getRetentionUnit()));
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
        if (request.getAutoDelete() == null) {
            request.setAutoDelete(Boolean.FALSE);
        }
        if (Boolean.TRUE.equals(request.getAutoDelete())) {
            request.setRequireApproval(Boolean.FALSE);
        }
    }

    private void validatePolicyRequest(StudyRetentionPolicySaveRequest request) {
        if (request.getRetentionValue() == null || request.getRetentionValue() < 1) {
            throw new IllegalArgumentException("Retention value must be at least 1.");
        }
        if (!isSupportedRetentionUnit(request.getRetentionUnit())) {
            throw new IllegalArgumentException("Retention unit must be DAY, MONTH, or YEAR.");
        }
        int retentionDays = toApproximateRetentionDays(request.getRetentionValue(), request.getRetentionUnit());
        if (retentionDays > MAX_RETENTION_DAYS) {
            throw new IllegalArgumentException(
                    "Retention period cannot exceed 3650 days (117 months or 10 years)."
            );
        }
        request.setRetentionDays(retentionDays);
        if (request.getNotifyBeforeDays() == null || request.getNotifyBeforeDays() < 0 || request.getNotifyBeforeDays() > 365) {
            throw new IllegalArgumentException("Notify-before days must be between 0 and 365.");
        }
        if (request.getNotifyBeforeDays() > request.getRetentionDays()) {
            throw new IllegalArgumentException("Notify-before days cannot be greater than the retention period.");
        }
    }

    private static String normalizeRetentionUnit(String value) {
        if (!hasText(value)) {
            return DEFAULT_RETENTION_UNIT;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.endsWith("S")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isSupportedRetentionUnit(String value) {
        return "DAY".equals(value) || "MONTH".equals(value) || "YEAR".equals(value);
    }

    private static int toApproximateRetentionDays(Integer value, String unit) {
        int amount = value == null || value < 1 ? DEFAULT_RETENTION_VALUE : value;
        return switch (normalizeRetentionUnit(unit)) {
            case "MONTH" -> amount * 31;
            case "YEAR" -> amount * 365;
            default -> amount;
        };
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
                || "AUTO_DELETE_READY".equals(status)
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

    private static String deleteNoteForMode(String mode) {
        if ("AUTO".equals(mode) || "SCHEDULED".equals(mode) || "MANUAL_AUTO_RUN".equals(mode)) {
            return "Auto-delete expired study by retention policy.";
        }
        if ("BULK".equals(mode)) {
            return "Bulk hard-delete approved from retention queue.";
        }
        return "Hard-delete approved from retention queue.";
    }

    private static String firstNonBlank(String first, String fallback) {
        return hasText(first) ? first : fallback;
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
