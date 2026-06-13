package com.ut.emrPacs.model.dto.request.dashboard;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class DashboardOverviewRequest {
    @JsonIgnore
    private Long hospitalId;
    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;
    private Integer snapshotLimit;
    private Integer waitingThresholdMinutes;
    /**
     * Performance guard:
     * - false/null: skip "today" aggregate queries.
     * - true: include todayAssignedWorklists/todayCancelledWorklists.
     */
    private Boolean includeTodayMetrics;
}
