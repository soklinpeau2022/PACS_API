package com.ut.emrPacs.model.dto.request.authentication.permission;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class SaveRolePermissionsRequest {

    @JsonIgnore
    private Long roleId;
    @JsonAlias({"rolePublicKey", "roleUuid", "publicKey", "key"})
    private String roleKey;

    @Schema(description = "Module detail ids to assign to the role. Empty array clears all permissions.")
    @JsonIgnore
    private List<@Positive Long> moduleDetailIds;
    @JsonAlias({"moduleDetailPublicKeys", "permissionKeys", "permissionPublicKeys"})
    private List<String> moduleDetailKeys;
}
