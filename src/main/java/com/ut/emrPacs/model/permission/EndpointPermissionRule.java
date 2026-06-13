package com.ut.emrPacs.model.permission;

import lombok.Data;

@Data
public class EndpointPermissionRule {
    private String httpMethod;
    private String endpointPattern;
    private String permissionCode;
    private String requiredScope;
}
