package com.ut.emrPacs.model.dto.response.authentication.role;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.model.dto.response.authentication.module.ModuleType;
import com.ut.emrPacs.model.role.RoleUser;
import lombok.Data;

import java.util.List;

@Data
public class RoleResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;

    @JsonIgnore
    private Long hospitalId;

    private String hospitalName;

    private Boolean isSystemRole;

    private String name;

    private String createdBy;

    private String createdDate;

    private String modifiedBy;

    private String modifiedDate;

    private List<RoleUser> userList;

    private List<ModuleType> moduleTypeList;

}
