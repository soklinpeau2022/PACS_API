package com.ut.emrPacs.model.dto.response.authentication.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class UserProfileResponse {
    private String firstName;
    private String lastName;
    private String gender;
    private String dateOfBirth;
    private String address;
    private String telephone;
    private String email;
    @JsonIgnore
    private Long nationalityId;
    private String nationalityName;
    private String username;
}
