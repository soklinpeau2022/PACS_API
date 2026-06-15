package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.StudyRetentionPolicyFilter;
import com.ut.emrPacs.model.base.filter.StudyRetentionReviewFilter;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionAutoDeleteRequest;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionBulkDeleteRequest;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionDecisionRequest;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionPolicySaveRequest;
import com.ut.emrPacs.service.service.StudyRetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;

@RestController
@RequestMapping(ApiConstants.StudyRetention.BASE_PATH)
@Tag(name = "16. Study Retention Controller", description = "Study retention policy and Super Admin deletion approval workflow.")
public class StudyRetentionController {

    @Autowired
    private StudyRetentionService studyRetentionService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.StudyRetention.POLICY_LIST_PATH)
    @Operation(summary = "List study retention policies", description = "Module -> Study Retention. Endpoint -> POST /study-retention/policy-list")
    public ResponseMessage<BaseResult> listPolicies(@RequestBody(required = false) StudyRetentionPolicyFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return studyRetentionService.listPolicies(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.StudyRetention.POLICY_FIND_PATH)
    @Operation(summary = "Find a study retention policy", description = "Module -> Study Retention. Endpoint -> POST /study-retention/policy-find/{publicKey}. Use study_retention_policies.public_id only.")
    public ResponseMessage<BaseResult> findPolicy(@PathVariable String publicKey, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        Long policyId = publicEntityKeyResolver.resolveFromPath(Entity.STUDY_RETENTION_POLICY, publicKey, "Study retention policy");
        return studyRetentionService.findPolicy(policyId, httpServletRequest);
    }

    @PostMapping(ApiConstants.StudyRetention.POLICY_SAVE_PATH)
    @Operation(summary = "Create or update a study retention policy", description = "Module -> Study Retention. Endpoint -> POST /study-retention/policy-save")
    public ResponseMessage<BaseResult> savePolicy(@Valid @RequestBody StudyRetentionPolicySaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return studyRetentionService.savePolicy(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.StudyRetention.POLICY_DELETE_PATH)
    @Operation(summary = "Delete a study retention policy", description = "Module -> Study Retention. Endpoint -> POST /study-retention/policy-delete/{id}")
    public ResponseMessage<BaseResult> deletePolicy(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        Long policyId = publicEntityKeyResolver.resolveFromPath(Entity.STUDY_RETENTION_POLICY, id, "Study retention policy");
        return studyRetentionService.deletePolicy(policyId, httpServletRequest);
    }

    @PostMapping(ApiConstants.StudyRetention.REVIEW_LIST_PATH)
    @Operation(summary = "List studies nearing or past retention expiry", description = "Module -> Study Retention. Endpoint -> POST /study-retention/review-list")
    public ResponseMessage<BaseResult> listReview(@RequestBody(required = false) StudyRetentionReviewFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return studyRetentionService.listReview(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.StudyRetention.SUMMARY_PATH)
    @Operation(summary = "Study retention summary", description = "Module -> Study Retention. Endpoint -> POST /study-retention/summary")
    public ResponseMessage<BaseResult> summary(@RequestBody(required = false) StudyRetentionReviewFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return studyRetentionService.summary(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.StudyRetention.APPROVE_DELETE_PATH)
    @Operation(summary = "Approve and hard-delete an expired study", description = "Module -> Study Retention. Endpoint -> POST /study-retention/approve-delete/{studyId}")
    public ResponseMessage<BaseResult> approveDelete(@PathVariable String studyId, @RequestBody(required = false) StudyRetentionDecisionRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        Long resolvedStudyId = publicEntityKeyResolver.resolveFromPath(Entity.STUDY, studyId, "Study");
        return studyRetentionService.approveDelete(resolvedStudyId, request, httpServletRequest);
    }

    @PostMapping(ApiConstants.StudyRetention.BULK_DELETE_PATH)
    @Operation(summary = "Bulk approve hard-delete expired studies", description = "Module -> Study Retention. Endpoint -> POST /study-retention/bulk-delete")
    public ResponseMessage<BaseResult> bulkDelete(@Valid @RequestBody(required = false) StudyRetentionBulkDeleteRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return studyRetentionService.bulkDelete(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.StudyRetention.AUTO_DELETE_RUN_PATH)
    @Operation(summary = "Run chunked auto-delete for expired studies", description = "Module -> Study Retention. Endpoint -> POST /study-retention/auto-delete-run")
    public ResponseMessage<BaseResult> runAutoDelete(@Valid @RequestBody(required = false) StudyRetentionAutoDeleteRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return studyRetentionService.runAutoDelete(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.StudyRetention.REJECT_DELETE_PATH)
    @Operation(summary = "Reject or keep an expired study", description = "Module -> Study Retention. Endpoint -> POST /study-retention/reject-delete/{studyId}")
    public ResponseMessage<BaseResult> rejectDelete(@PathVariable String studyId, @RequestBody(required = false) StudyRetentionDecisionRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        Long resolvedStudyId = publicEntityKeyResolver.resolveFromPath(Entity.STUDY, studyId, "Study");
        return studyRetentionService.rejectDelete(resolvedStudyId, request, httpServletRequest);
    }
}
