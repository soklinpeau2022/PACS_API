package com.ut.emrPacs.model.dto.request.authentication.role;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.Size;
@Data
public class RoleDataUpdate implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private Long id;
    @JsonAlias({"key", "uuid", "publicKey"})
    private String publicKey;

    @NotBlank
    @Size(max = 255)
    private String name;

    /**
     * Preferred request payload field for role-user mapping.
     * Example: [1,2,3]
     */
    @JsonIgnore
    private List<@Positive Long> userIds;
    @JsonAlias({"userPublicKeys", "memberKeys", "memberPublicKeys"})
    private List<String> userKeys;

    /**
     * Preferred request payload field for role permissions.
     * Example: [10,11,12] where values are module_detail ids.
     */
    @JsonIgnore
    private List<@Positive Long> moduleDetailIds;
    @JsonAlias({"moduleDetailPublicKeys", "permissionKeys", "permissionPublicKeys"})
    private List<String> moduleDetailKeys;
}
