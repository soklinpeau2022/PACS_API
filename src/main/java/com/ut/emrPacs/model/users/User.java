package com.ut.emrPacs.model.users;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class User implements Serializable {
    @Schema(hidden = true)
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String telephone;
    private Long userType;
    private String sex;
    private String address;
    private Long isActive;
    private String token;
    private String password;
    private String clientId;
    private String clientName;
    private Long modifiedBy;
    private String expired;
    private Long permissionVersion;
    private Long hospitalId;
    private String hospitalCode;

    private List<UserGroupList> userRoleList;
}

