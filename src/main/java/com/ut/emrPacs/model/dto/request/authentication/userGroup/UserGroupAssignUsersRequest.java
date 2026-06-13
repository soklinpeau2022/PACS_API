package com.ut.emrPacs.model.dto.request.authentication.userGroup;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class UserGroupAssignUsersRequest {

    @NotNull
    @Positive
    @JsonIgnore
    private Long id;
    @JsonAlias({"key", "uuid", "publicKey", "roleKey", "rolePublicKey"})
    private String publicKey;

    @JsonIgnore
    private List<@Positive Long> userIds;
    @JsonAlias({"userKeys", "userPublicKeys", "memberKeys", "memberPublicKeys"})
    private List<String> userKeys;
}
