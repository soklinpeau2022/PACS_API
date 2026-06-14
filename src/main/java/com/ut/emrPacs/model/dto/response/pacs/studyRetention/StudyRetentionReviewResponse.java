package com.ut.emrPacs.model.dto.response.pacs.studyRetention;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class StudyRetentionReviewResponse {
    @JsonIgnore
    private Long studyId;
    private String studyPublicKey;

    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalName;

    @JsonIgnore
    private Long policyId;
    private String policyPublicKey;

    @JsonIgnore
    private Long dicomServerId;
    private String dicomServerPublicKey;
    private String dicomServerName;

    @JsonIgnore
    private Long modalityId;
    private String modalityPublicKey;
    private String modalityCode;
    private String modalityName;

    private String patientName;
    private String patientMrn;
    private String accessionNumber;
    private String referenceVisitCode;
    private String studyInstanceUid;
    private String dicomServerStudyId;
    private String studyDate;
    private String imageReceivedAt;
    private Integer retentionDays;
    private Integer notifyBeforeDays;
    private String retentionBaseAt;
    private String nearExpiryAt;
    private String expiresAt;
    private Long daysUntilExpiry;
    private String status;
    private String latestDecisionStatus;
    private String decisionNote;
    private String errorMessage;
    private String requestedAt;
    private String approvedAt;
    private String rejectedAt;
    private String deletedAt;
}
