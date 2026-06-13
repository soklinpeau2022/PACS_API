package com.ut.emrPacs.model.dto.request.authentication.token;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @Schema(description = "OAuth2 clientId associated with the refresh token.")
    @Size(max = 128)
    private String clientId;

    @Schema(description = "Refresh token used to obtain a new access token.")
    @Size(max = 2048)
    private String refreshToken;
}
