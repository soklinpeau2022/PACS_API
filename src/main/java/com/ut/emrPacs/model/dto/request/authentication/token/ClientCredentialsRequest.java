package com.ut.emrPacs.model.dto.request.authentication.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for the OAuth2 client_credentials grant.
 *
 * Used by service clients (for example dicomserver-adapter) to obtain a JWT access token
 * without user involvement.
 */
@Data
public class ClientCredentialsRequest {

    @NotBlank
    @Size(max = 128)
    private String clientId;

    @NotBlank
    @Size(max = 256)
    private String clientSecret;

    /** Space-separated list of requested scopes. Optional - defaults to client's allowed scopes. */
    @Size(max = 512)
    private String scope;
}
