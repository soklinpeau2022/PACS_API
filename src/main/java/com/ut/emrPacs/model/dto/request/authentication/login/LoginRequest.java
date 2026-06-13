package com.ut.emrPacs.model.dto.request.authentication.login;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.ut.emrPacs.validation.ValidLoginRequest;

@Data
@ValidLoginRequest
public class LoginRequest {

    @Schema(hidden = true)
    @JsonIgnore
    private Long id;

    @Schema(
            description = "OAuth2 public client ID. Use an active login client such as pacs-web or pacs-mobile.",
            example = "pacs-web"
    )
    @NotBlank
    @Size(max = 128)
    private String clientId;

    @Schema(
            description = "Client display name (optional).",
            example = "PACS Web Client"
    )
    @Size(max = 128)
    private String clientName;

    @Schema(description = "Application username.", example = "admin")
    @Size(max = 64)
    private String username;

    @Schema(description = "Application password.", example = "your_password")
    @NotBlank
    @Size(max = 128)
    private String password;

}
