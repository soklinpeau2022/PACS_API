package com.ut.emrPacs.model.dto.response.authentication.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class UserGroupUserResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    private String username;
}
