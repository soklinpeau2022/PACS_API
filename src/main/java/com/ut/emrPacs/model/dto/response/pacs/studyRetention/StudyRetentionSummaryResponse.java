package com.ut.emrPacs.model.dto.response.pacs.studyRetention;

import lombok.Data;

@Data
public class StudyRetentionSummaryResponse {
    private Long nearExpiry;
    private Long expiredWaitingApproval;
    private Long pendingApproval;
    private Long deleteFailed;
    private Long rejected;
    private Long keepPermanent;
    private Long totalOpen;
}
