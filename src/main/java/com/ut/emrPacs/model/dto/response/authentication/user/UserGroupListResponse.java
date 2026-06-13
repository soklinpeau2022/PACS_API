package com.ut.emrPacs.model.dto.response.authentication.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class UserGroupListResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    private String name;
    private String createdBy;
    private String createdDate;
    private String modifiedBy;
    private String modifiedDate;
    private List<UserGroupUserResponse> userList;
}
