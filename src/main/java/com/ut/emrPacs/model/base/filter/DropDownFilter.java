package com.ut.emrPacs.model.base.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DropDownFilter extends Filter {
    private Boolean unusedRoutingOnly;
    private Long includeDicomServerId;
    private String includeDicomServerKey;
}
