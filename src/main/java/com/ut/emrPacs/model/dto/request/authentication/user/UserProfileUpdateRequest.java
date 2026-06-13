package com.ut.emrPacs.model.dto.request.authentication.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Size;
@Data
public class UserProfileUpdateRequest {

    @Schema(hidden = true)
    @JsonIgnore
    private Long id;

    @Schema(hidden = true)
    @JsonIgnore
    private Long modifiedBy;

    /**
     * Encoded (bcrypt) password to persist. Populated in service after validation.
     */
    @Schema(hidden = true)
    @JsonIgnore
    private String password;

    // Profile
    @Size(max = 255)
    private String firstName;
    @Size(max = 255)
    private String lastName;
    private String gender;       // Male/Female
    private String dateOfBirth;  // yyyy-MM-dd
    private String address;
    private String telephone;
    private String email;
    @JsonIgnore
    private Long nationalityId;
    @Size(max = 255)
    private String username;

    // Password change
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
