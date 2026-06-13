package com.ut.emrPacs.model.dto.request.authentication.userGroup;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class UserGroupModuleSelectionRequest {
    @JsonIgnore
    private Long id;
    @JsonAlias({"key", "uuid", "publicKey", "moduleDetailKey", "moduleDetailPublicKey"})
    private String publicKey;
    private String name;
    private Boolean checked;
}
