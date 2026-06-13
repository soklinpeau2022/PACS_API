package com.ut.emrPacs.model.base.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserLogFilter extends Filter{

    private String dateFrom;
    private String dateTo;
    private Long userNameId;
}
