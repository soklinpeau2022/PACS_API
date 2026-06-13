package com.ut.emrPacs.model.dto.request.authentication.changePassword;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class ChangePasswordRequest {

    @JsonIgnore
    private Long userId;
    @JsonAlias({"userPublicKey", "userUuid", "publicKey", "key"})
    @NotBlank
    private String userKey;
    @NotBlank
    private String newPassword;
    @NotBlank
    private String confirmPassword;
}
