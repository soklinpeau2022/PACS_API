package com.ut.emrPacs.model.dto.request.authentication.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class UserUpdateRequest {

    @JsonIgnore
    private Long id;
    @JsonAlias({"key", "uuid"})
    private String publicKey;

    @Size(max = 255)
    private String username;

    @JsonProperty("first_name")
    @JsonAlias("firstName")
    @Size(max = 255)
    private String firstName;

    @JsonProperty("last_name")
    @JsonAlias("lastName")
    @Size(max = 255)
    private String lastName;

    @Size(max = 30)
    private String telephone;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String signature;

    @Size(max = 255)
    private String password;

    @JsonProperty("is_active")
    @JsonAlias("isActive")
    private Long isActive;

    @JsonProperty("hospital_ids")
    @JsonAlias("hospitalIds")
    @JsonIgnore
    private List<@Positive Long> hospitalIds;
    @JsonAlias({"hospitalPublicKeys", "hospitalUuids"})
    private List<String> hospitalKeys;

    @JsonProperty("user_group_ids")
    @JsonAlias("userGroupIds")
    @JsonIgnore
    private List<@Positive Long> userGroupIds;
    @JsonAlias({"userGroupPublicKeys", "roleKeys", "rolePublicKeys", "roleUuids"})
    private List<String> userGroupKeys;

    @JsonIgnore
    private Long modifiedBy;
}
