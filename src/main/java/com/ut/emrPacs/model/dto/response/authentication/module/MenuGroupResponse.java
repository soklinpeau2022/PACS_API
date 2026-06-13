package com.ut.emrPacs.model.dto.response.authentication.module;

import lombok.Data;

@Data
public class MenuGroupResponse {
    private String groupCode;
    private String groupName;
    private Integer orderNo;
    private Long checkedCount;
    private Long totalCount;
}

