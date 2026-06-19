package com.ut.emrPacs.model.base.filter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ActivityFilter extends Filter {
    @Schema(description = "Cursor for large-data paging: fetch activity rows with id below this value.")
    private Long lastActivityId;

    private String startDate;

    private String endDate;

    private Long moduleId;

    private Long userId;

    private Long status;

    private String action;

    private String module;

    private String endpoint;

    public void setLastActivityId(Long lastActivityId) {
        this.lastActivityId = lastActivityId != null && lastActivityId > 0L ? lastActivityId : null;
    }
}
