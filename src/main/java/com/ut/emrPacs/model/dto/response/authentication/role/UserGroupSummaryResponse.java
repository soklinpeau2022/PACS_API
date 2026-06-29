package com.ut.emrPacs.model.dto.response.authentication.role;

import lombok.Data;

@Data
public class UserGroupSummaryResponse {
    private Long totalGroups;
    private Long totalMembers;
}
