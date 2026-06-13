package com.ut.emrPacs.model.dto.request.authentication.userGroup;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class UserGroupModuleTypeSelectionRequest {
    @JsonIgnore
    private Long id;
    @JsonAlias({"key", "uuid", "publicKey", "moduleTypeKey", "moduleTypePublicKey"})
    private String publicKey;
    private String name;
    private List<UserGroupModuleSelectionRequest> moduleList;
}
