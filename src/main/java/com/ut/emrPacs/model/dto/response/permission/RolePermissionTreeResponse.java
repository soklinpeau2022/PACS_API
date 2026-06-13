package com.ut.emrPacs.model.dto.response.permission;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class RolePermissionTreeResponse {
    @JsonIgnore
    private Long moduleTypeId;
    private String moduleTypePublicKey;
    private String moduleTypeCode;
    private String moduleTypeName;
    private String moduleTypeNameOther;
    private Integer moduleTypeOrder;
    @JsonIgnore
    private Long moduleId;
    private String modulePublicKey;
    private String moduleCode;
    private String moduleName;
    private Integer moduleOrder;
    @JsonIgnore
    private Long moduleDetailId;
    private String moduleDetailPublicKey;
    private String code;
    private String actionKey;
    private Integer orderNo;
    private String moduleDetailName;
    private String moduleDetailNameOther;
    private String moduleDetailType;
    private Boolean checked;
}
