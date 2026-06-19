package com.ut.emrPacs.model.base.filter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserLogFilter extends Filter{
    @Schema(description = "Cursor for large-data paging: fetch user-log rows with id below this value.")
    private Long lastUserLogId;

    private String dateFrom;
    private String dateTo;
    private Long userNameId;

    public void setLastUserLogId(Long lastUserLogId) {
        this.lastUserLogId = lastUserLogId != null && lastUserLogId > 0L ? lastUserLogId : null;
    }
}
