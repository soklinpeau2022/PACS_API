package com.ut.emrPacs.model.dto.request.authentication.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Size;
@Data
public class EditProfileRequest {

    @Schema(hidden = true)
    @JsonIgnore
    private Long id;

    private Long userType;

    @Size(max = 255)
    private String firstName;

    @Size(max = 255)
    private String lastName;

    private String address;

    private String telephone;

    private String sex;
}
