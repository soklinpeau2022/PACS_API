package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.pacs.DashboardMapper;
import com.ut.emrPacs.mapper.pacs.StudyRetentionMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.StudyRetentionReviewFilter;
import com.ut.emrPacs.model.components.pacs.dashboard.WorklistStatusCountRow;
import com.ut.emrPacs.model.dto.request.dashboard.DashboardOverviewRequest;
import com.ut.emrPacs.model.dto.response.dashboard.DashboardActionAlertResponse;
import com.ut.emrPacs.model.dto.response.dashboard.DashboardOverviewResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerHealthResponse;
import com.ut.emrPacs.model.dto.response.pacs.studyRetention.StudyRetentionSummaryResponse;
import com.ut.emrPacs.model.enums.WorklistStatus;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.DashboardService;
import com.ut.emrPacs.service.service.DicomServerHealthService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardServiceImpl.class);

    @Autowired
    private DashboardMapper dashboardMapper;
    @Autowired
    private StudyRetentionMapper studyRetentionMapper;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @Autowired
    private DicomServerHealthService dicomServerHealthService;

    /**
     * "live"   -> COUNT(*) on pacs_studies (exact active count; fine at small scale).
     * "summary" -> cumulative received-study count from pacs_daily_stats (avoids
     * scanning a 100M-row table). Switch to "summary" once the daily-stats
     * scheduler has been running and seeded.
     */
    @org.springframework.beans.factory.annotation.Value("${dashboard.studies-count-source:summary}")
    private String studiesCountSource;

    @Override
    public ResponseMessage<BaseResult> getOverview(DashboardOverviewRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            Long requestedHospitalId = request == null
                    ? null
                    : publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), request.getHospitalId());
            Long hospitalId = resolveScopedHospitalId(requestedHospitalId);
            int snapshotLimit = sanitizeLimit(request == null ? null : request.getSnapshotLimit(), 8, 1, 20);
            int waitingThresholdMinutes = sanitizeLimit(request == null ? null : request.getWaitingThresholdMinutes(), 30, 5, 240);
            boolean includeTodayMetrics = request != null && Boolean.TRUE.equals(request.getIncludeTodayMetrics());
            LocalDate dateFrom = request == null ? null : request.getDateFrom();
            LocalDate dateTo = request == null ? null : request.getDateTo();
            if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
                LocalDate swap = dateFrom;
                dateFrom = dateTo;
                dateTo = swap;
            }

            DashboardOverviewResponse response = new DashboardOverviewResponse();
            response.setRecentPatients(nullSafeLong(dashboardMapper.countRecentPatients(hospitalId, dateFrom, dateTo)));
            response.setWorklistItems(nullSafeLong(dashboardMapper.countWorklistItems(hospitalId, dateFrom, dateTo)));
            boolean hasStudyDateFilter = dateFrom != null || dateTo != null;
            response.setStudies(!hasStudyDateFilter && "summary".equalsIgnoreCase(studiesCountSource)
                    ? nullSafeLong(dashboardMapper.sumReceivedStudies(hospitalId, null, null))
                    : nullSafeLong(dashboardMapper.countStudies(hospitalId, dateFrom, dateTo)));

            response.setTotalDicomServers(nullSafeLong(dashboardMapper.countTotalDicomServers(hospitalId)));
            response.setActiveDicomServers(nullSafeLong(dashboardMapper.countActiveDicomServers(hospitalId)));
            response.setInactiveDicomServers(nullSafeLong(dashboardMapper.countInactiveDicomServers(hospitalId)));
            List<DicomServerHealthResponse> dicomServerHealth = dicomServerHealthService.listHealth(hospitalId);
            long onlineDicomServers = dicomServerHealth.stream().filter(item -> Boolean.TRUE.equals(item.getOnline())).count();
            long offlineDicomServers = dicomServerHealth.stream().filter(item -> "OFFLINE".equalsIgnoreCase(item.getStatus())).count();
            response.setOnlineDicomServers(onlineDicomServers);
            response.setOfflineDicomServers(offlineDicomServers);
            response.setDicomServerHealth(dicomServerHealth);
            response.setSystemPulse(resolveSystemPulse(
                    response.getActiveDicomServers(),
                    onlineDicomServers,
                    offlineDicomServers
            ));

            response.setMappedModalities(nullSafeLong(dashboardMapper.countMappedModalities(hospitalId)));
            response.setUnmappedModalities(nullSafeLong(dashboardMapper.countUnmappedModalities(hospitalId)));

            EnumMap<WorklistStatus, Long> worklistStatusMap = buildworklistStatusMap(
                    dashboardMapper.listWorklistStatusCounts(hospitalId, dateFrom, dateTo)
            );
            response.setWorklistWaiting(worklistStatusMap.getOrDefault(WorklistStatus.WAITING, 0L));
            response.setWorklistInProgress(worklistStatusMap.getOrDefault(WorklistStatus.IN_PROGRESS, 0L));
            response.setWorklistCancelled(worklistStatusMap.getOrDefault(WorklistStatus.CANCELLED, 0L));
            response.setWorklistFailed(worklistStatusMap.getOrDefault(WorklistStatus.FAILED, 0L));

            response.setLongWaitingWorklists(nullSafeLong(dashboardMapper.countLongWaitingWorklists(hospitalId, waitingThresholdMinutes, dateFrom, dateTo)));
            if (includeTodayMetrics) {
                response.setTodayAssignedWorklists(nullSafeLong(dashboardMapper.countTodayAssignedWorklists(hospitalId)));
                response.setTodayCancelledWorklists(nullSafeLong(dashboardMapper.countTodayCancelledWorklists(hospitalId)));
            } else {
                response.setTodayAssignedWorklists(0L);
                response.setTodayCancelledWorklists(0L);
            }

            StudyRetentionReviewFilter retentionFilter = buildRetentionDateFilter(dateFrom, dateTo);
            StudyRetentionSummaryResponse retentionSummary = studyRetentionMapper.summary(hospitalId, retentionFilter);
            response.setRetentionNearExpiry(nullSafeLong(retentionSummary == null ? null : retentionSummary.getNearExpiry()));
            response.setRetentionExpiredWaitingApproval(nullSafeLong(retentionSummary == null ? null : retentionSummary.getExpiredWaitingApproval()));
            response.setRetentionPendingApproval(nullSafeLong(retentionSummary == null ? null : retentionSummary.getPendingApproval()));
            response.setRetentionAutoDeleteReady(nullSafeLong(retentionSummary == null ? null : retentionSummary.getAutoDeleteReady()));
            response.setRetentionDeleteFailed(nullSafeLong(retentionSummary == null ? null : retentionSummary.getDeleteFailed()));
            response.setRetentionAlerts(studyRetentionMapper.listDashboardRetentionAlerts(hospitalId, 8, retentionFilter));

            response.setWorklistSnapshot(dashboardMapper.listWorklistSnapshot(hospitalId, snapshotLimit, dateFrom, dateTo));
            response.setActionAlerts(buildActionAlerts(response, waitingThresholdMinutes, includeTodayMetrics));

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    ApiConstants.Dashboard.BASE_PATH + ApiConstants.Dashboard.OVERVIEW_PATH,
                    null,
                    null,
                    "Dashboard",
                    "Dashboard (View)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(response), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0)
                    ? (long) error.getStackTrace()[0].getLineNumber()
                    : null;
            activityLogService.insert(
                    ApiConstants.Dashboard.BASE_PATH + ApiConstants.Dashboard.OVERVIEW_PATH,
                    errorLine,
                    error.toString(),
                    "Dashboard",
                    "Dashboard (View)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private Long resolveScopedHospitalId(Long requestedHospitalId) {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null) {
            throw new IllegalStateException("User context not found in OAuth2 token claims.");
        }
        boolean isSuperAdmin = principal.userId() != null && principal.userId() == 1L;
        if (requestedHospitalId != null && requestedHospitalId > 0 && isSuperAdmin) {
            return requestedHospitalId;
        }
        if (principal.hospitalId() != null && principal.hospitalId() > 0) {
            return principal.hospitalId();
        }
        // Super admin can read cross-hospital overview when hospital scope is not pinned in token.
        if (isSuperAdmin) {
            return requestedHospitalId;
        }
        throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
    }

    private static int sanitizeLimit(Integer value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static long nullSafeLong(Long value) {
        return value == null ? 0L : value;
    }

    private static StudyRetentionReviewFilter buildRetentionDateFilter(LocalDate dateFrom, LocalDate dateTo) {
        StudyRetentionReviewFilter filter = new StudyRetentionReviewFilter();
        if (dateFrom != null) {
            filter.setStartDate(dateFrom.toString());
        }
        if (dateTo != null) {
            filter.setEndDate(dateTo.toString());
        }
        return filter;
    }

    private static String resolveSystemPulse(long activeServers, long onlineServers, long offlineServers) {
        if (activeServers == 0L) {
            return "No servers";
        }
        if (offlineServers > 0L) {
            return "Degraded";
        }
        if (onlineServers >= activeServers) {
            return "Live";
        }
        return "Checking";
    }

    private static EnumMap<WorklistStatus, Long> buildworklistStatusMap(List<WorklistStatusCountRow> rows) {
        EnumMap<WorklistStatus, Long> counts = new EnumMap<>(WorklistStatus.class);
        if (rows == null || rows.isEmpty()) {
            return counts;
        }
        for (WorklistStatusCountRow row : rows) {
            if (row == null || row.getStatusCode() == null) {
                continue;
            }
            try {
                WorklistStatus status = WorklistStatus.fromCode(row.getStatusCode());
                counts.put(status, nullSafeLong(row.getTotal()));
            } catch (Exception ex) {
                LOGGER.debug("Skipping unknown WorklistStatus code '{}': {}", row.getStatusCode(), ex.getMessage());
            }
        }
        return counts;
    }

    private static List<DashboardActionAlertResponse> buildActionAlerts(
            DashboardOverviewResponse overview,
            int waitingThresholdMinutes,
            boolean includeTodayMetrics
    ) {
        List<DashboardActionAlertResponse> alerts = new ArrayList<>();

        if (overview.getActiveDicomServers() == 0L) {
            alerts.add(buildAlert(
                    "NO_ACTIVE_DICOM_SERVER",
                    "danger",
                    "No active DICOM server",
                    "No active DICOM server is configured for this hospital.",
                    overview.getActiveDicomServers(),
                    "/dicom-servers"
            ));
        }
        if (overview.getOfflineDicomServers() != null && overview.getOfflineDicomServers() > 0L) {
            alerts.add(buildAlert(
                    "DICOM_SERVER_OFFLINE",
                    "danger",
                    "DICOM server offline",
                    "One or more active DICOM servers are not responding.",
                    overview.getOfflineDicomServers(),
                    "/dicom-servers"
            ));
        }
        if (overview.getUnmappedModalities() > 0L) {
            alerts.add(buildAlert(
                    "UNMAPPED_MODALITY",
                    "warning",
                    "Modality routing missing",
                    "Some active modalities do not have DICOM routing yet.",
                    overview.getUnmappedModalities(),
                    "/dicom-routing"
            ));
        }
        if (overview.getLongWaitingWorklists() > 0L) {
            alerts.add(buildAlert(
                    "Worklist_WAITING_TOO_LONG",
                    "warning",
                    "Worklists waiting too long",
                    "Waiting Worklists exceeded " + waitingThresholdMinutes + " minutes.",
                    overview.getLongWaitingWorklists(),
                    "/worklist"
            ));
        }
        if (includeTodayMetrics && overview.getTodayCancelledWorklists() > 0L) {
            alerts.add(buildAlert(
                    "TODAY_CANCELLED_Worklist",
                    "warning",
                    "Cancelled Worklists today",
                    "There are cancelled Worklists today. Please review operational flow.",
                    overview.getTodayCancelledWorklists(),
                    "/worklist"
            ));
        }
        if (overview.getRetentionDeleteFailed() != null && overview.getRetentionDeleteFailed() > 0L) {
            alerts.add(buildAlert(
                    "STUDY_RETENTION_DELETE_FAILED",
                    "danger",
                    "Study deletion failed",
                    "A retention deletion needs review before retry.",
                    overview.getRetentionDeleteFailed(),
                    "/study-retention?status=DELETE_FAILED"
            ));
        }
        if (overview.getRetentionExpiredWaitingApproval() != null && overview.getRetentionExpiredWaitingApproval() > 0L) {
            alerts.add(buildAlert(
                    "STUDY_RETENTION_EXPIRED",
                    "danger",
                    "Expired studies waiting approval",
                    "Studies reached retention expiry and need Super Admin approval.",
                    overview.getRetentionExpiredWaitingApproval(),
                    "/study-retention?status=EXPIRED_WAITING_APPROVAL"
            ));
        }
        if (overview.getRetentionAutoDeleteReady() != null && overview.getRetentionAutoDeleteReady() > 0L) {
            alerts.add(buildAlert(
                    "STUDY_RETENTION_AUTO_DELETE_READY",
                    "warning",
                    "Auto-delete studies ready",
                    "Expired studies match auto-delete policies and will be cleaned in chunks.",
                    overview.getRetentionAutoDeleteReady(),
                    "/study-retention?status=AUTO_DELETE_READY"
            ));
        }
        if (overview.getRetentionNearExpiry() != null && overview.getRetentionNearExpiry() > 0L) {
            alerts.add(buildAlert(
                    "STUDY_RETENTION_NEAR_EXPIRY",
                    "warning",
                    "Studies nearing expiry",
                    "Studies are inside the retention warning window.",
                    overview.getRetentionNearExpiry(),
                    "/study-retention?status=NEAR_EXPIRY"
            ));
        }

        return alerts;
    }

    private static DashboardActionAlertResponse buildAlert(
            String code,
            String tone,
            String title,
            String description,
            Long value,
            String path
    ) {
        DashboardActionAlertResponse alert = new DashboardActionAlertResponse();
        alert.setCode(code);
        alert.setTone(tone);
        alert.setTitle(title);
        alert.setDescription(description);
        alert.setValue(value);
        alert.setPath(path);
        return alert;
    }
}
