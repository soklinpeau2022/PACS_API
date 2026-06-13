package com.ut.emrPacs.model.users;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class UserRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(hidden = true)
    @JsonIgnore
    private Long id;

    @NotBlank
    @Size(max = 255)
    private String username;

    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(max = 255)
    private String password;

    @JsonProperty("first_name")
    @JsonAlias("firstName")
    @Schema(description = "User first name.", example = "John")
    @Size(max = 255)
    private String firstName;

    @JsonProperty("last_name")
    @JsonAlias("lastName")
    @Schema(description = "User last name.", example = "Doe")
    @Size(max = 255)
    private String lastName;

    @JsonProperty("hospital_id")
    @JsonAlias("hospitalId")
    @Schema(description = "Single hospital ID for user mapping.", example = "1")
    @Positive
    @JsonIgnore
    private Long hospitalId;

    @JsonProperty("hospital_ids")
    @JsonAlias("hospitalIds")
    @Schema(description = "Hospital IDs assigned to this user. Preferred format for multi-hospital assignment.", example = "[1,2]")
    @JsonIgnore
    private List<@Positive Long> hospitalIds;
    @JsonAlias({"hospitalPublicKeys", "hospitalUuids"})
    private List<String> hospitalKeys;

    @JsonProperty("user_group_id")
    @JsonAlias("userGroupId")
    @Schema(description = "Legacy single user group ID. Prefer userGroupIds for multiple groups.", example = "1")
    @Positive
    @JsonIgnore
    private Long userGroupId;

    @JsonProperty("user_group_ids")
    @JsonAlias("userGroupIds")
    @Schema(description = "User group IDs assigned to this user. Preferred format for multiple groups.", example = "[1,2]")
    @JsonIgnore
    private List<@Positive Long> userGroupIds;
    @JsonAlias({"userGroupPublicKeys", "roleKeys", "rolePublicKeys", "roleUuids"})
    private List<String> userGroupKeys;
}
