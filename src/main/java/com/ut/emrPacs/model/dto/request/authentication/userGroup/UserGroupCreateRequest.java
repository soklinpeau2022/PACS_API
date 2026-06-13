package com.ut.emrPacs.model.dto.request.authentication.userGroup;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserGroupCreateRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Schema(description = "Target hospital id. If not provided, backend uses current login hospital.")
    @Positive
    @JsonIgnore
    private Long hospitalId;

    @Schema(description = "Optional users to assign during create.")
    @JsonIgnore
    private List<@Positive Long> userIds;
    @JsonAlias({"userPublicKeys", "memberKeys", "memberPublicKeys"})
    private List<String> userKeys;

    @Schema(description = "Optional module_detail ids to assign permissions during create.")
    @JsonIgnore
    private List<@Positive Long> moduleDetailIds;
    @JsonAlias({"moduleDetailPublicKeys", "permissionKeys", "permissionPublicKeys"})
    private List<String> moduleDetailKeys;

    @Schema(description = "Optional full permission matrix. When provided, checked=true ids are applied.")
    private List<UserGroupModuleTypeSelectionRequest> moduleTypeList;
}
