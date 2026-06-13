package com.ut.emrPacs.model.dto.request.authentication.userGroup;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserGroupUpdateRequest {

    @JsonIgnore
    private Long id;
    @JsonAlias({"key", "uuid", "publicKey"})
    private String publicKey;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Positive
    @JsonIgnore
    private Long hospitalId;

    @JsonIgnore
    private List<@Positive Long> userIds;
    @JsonAlias({"userPublicKeys", "memberKeys", "memberPublicKeys"})
    private List<String> userKeys;

    @JsonIgnore
    private List<@Positive Long> moduleDetailIds;
    @JsonAlias({"moduleDetailPublicKeys", "permissionKeys", "permissionPublicKeys"})
    private List<String> moduleDetailKeys;

    private List<UserGroupModuleTypeSelectionRequest> moduleTypeList;
}
