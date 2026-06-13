package com.ut.emrPacs.model.dto.response.authentication.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.model.dto.response.authentication.module.ModuleType;
import com.ut.emrPacs.model.users.UserGroupList;
import com.ut.emrPacs.model.users.UserHospital;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class UserResponse implements Serializable {

    @JsonIgnore
    private Long id;
    private String publicKey;

    private String signaturePhoto;

    private Long userType;

    private String firstName;

    private String lastName;

    private String username;

    @JsonIgnore
    private String password;

    private String email;

    private String telephone;

    private String expireDate;

    @JsonIgnore
    private Long hospitalId;

    private String hospitalPublicKey;

    private String hospitalCode;

    private String hospitalName;

    @JsonIgnore
    private Long createdById;

    private String createdBy;

    private String created;

    @JsonIgnore
    private Long modifiedById;

    private String modifiedBy;

    private String modified;


    private List<ModuleType> moduleTypeList;

    private List<UserHospital> hospitalList;

    private List<UserGroupList> userRoleList;

}
