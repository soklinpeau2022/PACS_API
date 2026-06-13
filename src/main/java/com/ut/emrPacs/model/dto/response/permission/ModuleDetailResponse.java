package com.ut.emrPacs.model.dto.response.permission;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class ModuleDetailResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long moduleId;
    private String modulePublicKey;
    private String code;
    private String name;
    private String nameOther;
    private String type;
    private String actionKey;
    private Integer displayOrder;
    private Boolean checked;
}
