package com.ut.emrPacs.mapper.pacs;

import com.ut.emrPacs.model.base.filter.StudyRetentionPolicyFilter;
import com.ut.emrPacs.model.base.filter.StudyRetentionReviewFilter;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionPolicySaveRequest;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionPolicyResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionReviewResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StudyRetentionMapper {
    Long countPolicies(@Param("hospitalId") Long hospitalId, @Param("filter") StudyRetentionPolicyFilter filter);

    List<StudyRetentionPolicyResponse> listPolicies(@Param("hospitalId") Long hospitalId, @Param("filter") StudyRetentionPolicyFilter filter);

    StudyRetentionPolicyResponse findPolicyById(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    Long countDuplicatePolicyScope(
            @Param("hospitalId") Long hospitalId,
            @Param("dicomServerId") Long dicomServerId,
            @Param("modalityId") Long modalityId,
            @Param("excludeId") Long excludeId
    );

    Integer countDicomServerInHospital(@Param("hospitalId") Long hospitalId, @Param("dicomServerId") Long dicomServerId);

    Integer countModalityInHospital(@Param("hospitalId") Long hospitalId, @Param("modalityId") Long modalityId);

    Integer insertPolicy(@Param("request") StudyRetentionPolicySaveRequest request, @Param("userId") Long userId);

    Integer updatePolicy(@Param("request") StudyRetentionPolicySaveRequest request, @Param("userId") Long userId);

    Integer deletePolicy(@Param("id") Long id, @Param("hospitalId") Long hospitalId, @Param("userId") Long userId);

    Long countReview(@Param("hospitalId") Long hospitalId, @Param("filter") StudyRetentionReviewFilter filter);

    List<StudyRetentionReviewResponse> listReview(@Param("hospitalId") Long hospitalId, @Param("filter") StudyRetentionReviewFilter filter);

    StudyRetentionReviewResponse findReviewCandidateByStudyId(@Param("hospitalId") Long hospitalId, @Param("studyId") Long studyId);

    List<StudyRetentionReviewResponse> listReviewCandidatesByStudyPublicKeys(
            @Param("hospitalId") Long hospitalId,
            @Param("studyPublicKeys") List<String> studyPublicKeys
    );

    List<StudyRetentionReviewResponse> listAutoDeleteCandidates(@Param("hospitalId") Long hospitalId, @Param("limit") Integer limit);

    List<StudyRetentionReviewResponse> listDashboardRetentionAlerts(@Param("hospitalId") Long hospitalId, @Param("limit") Integer limit);

    StudyRetentionSummaryResponse summary(@Param("hospitalId") Long hospitalId);

    Long createDeleteRequest(@Param("candidate") StudyRetentionReviewResponse candidate, @Param("userId") Long userId);

    Integer markRequestApproved(@Param("requestId") Long requestId, @Param("userId") Long userId, @Param("note") String note);

    Integer markRequestRejected(
            @Param("requestId") Long requestId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("note") String note
    );

    Integer markRequestDeleted(@Param("requestId") Long requestId, @Param("userId") Long userId);

    Integer markRequestDeleteFailed(@Param("requestId") Long requestId, @Param("errorMessage") String errorMessage);

    Integer hardDeleteStudyData(@Param("hospitalId") Long hospitalId, @Param("studyId") Long studyId);
}
