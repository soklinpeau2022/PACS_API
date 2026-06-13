package com.ut.emrPacs.model.dto.request.authentication.role;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class RoleCreateRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 255)
    private String name;

    @JsonIgnore
    private List<@Positive Long> userIds;
    @JsonAlias({"userPublicKeys", "memberKeys", "memberPublicKeys"})
    private List<String> userKeys;

    @JsonIgnore
    private List<@Positive Long> moduleDetailIds;
    @JsonAlias({"moduleDetailPublicKeys", "permissionKeys", "permissionPublicKeys"})
    private List<String> moduleDetailKeys;
}
