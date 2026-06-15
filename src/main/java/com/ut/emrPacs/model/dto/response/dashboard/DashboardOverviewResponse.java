package com.ut.emrPacs.model.dto.response.dashboard;

import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerHealthResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionReviewResponse;
import lombok.Data;

import java.util.List;

@Data
public class DashboardOverviewResponse {
    private Long recentPatients;
    private Long WorklistItems;
    private Long studies;
    private String systemPulse;

    private Long totalDicomServers;
    private Long activeDicomServers;
    private Long inactiveDicomServers;
    private Long onlineDicomServers;
    private Long offlineDicomServers;
    private List<DicomServerHealthResponse> dicomServerHealth;

    private Long mappedModalities;
    private Long unmappedModalities;

    private Long WorklistWaiting;
    private Long WorklistInProgress;
    private Long WorklistCancelled;
    private Long WorklistFailed;
    private Long longWaitingWorklists;

    private Long todayAssignedWorklists;
    private Long todayCancelledWorklists;

    private Long retentionNearExpiry;
    private Long retentionExpiredWaitingApproval;
    private Long retentionPendingApproval;
    private Long retentionAutoDeleteReady;
    private Long retentionDeleteFailed;
    private List<StudyRetentionReviewResponse> retentionAlerts;

    private List<DashboardWorklistSnapshotItemResponse> WorklistSnapshot;
    private List<DashboardActionAlertResponse> actionAlerts;
}
