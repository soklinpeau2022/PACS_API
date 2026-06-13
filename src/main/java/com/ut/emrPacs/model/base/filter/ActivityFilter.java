package com.ut.emrPacs.model.base.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ActivityFilter extends Filter {

    private String startDate;

    private String endDate;

    private Long moduleId;

    private Long userId;

    private Long status;

    private String action;

    private String module;

    private String endpoint;
}
