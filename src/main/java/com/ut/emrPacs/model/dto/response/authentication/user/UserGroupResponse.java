package com.ut.emrPacs.model.dto.response.authentication.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.model.dto.response.authentication.module.ModuleType;
import com.ut.emrPacs.model.enums.UserGroupType;
import lombok.Data;

import java.util.List;

@Data
public class UserGroupResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalName;
    private String name;
    private String createdBy;
    private String createdDate;
    private String modifiedBy;
    private String modifiedDate;
    private UserGroupType groupType;
    private List<UserGroupUserResponse> userList;
    @JsonIgnore
    private List<Long> userIds;
    private List<ModuleType> moduleTypeList;
    @JsonIgnore
    private List<Long> moduleDetailIds;
}
