package com.ut.emrPacs.controller;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.authentication.login.LoginRequest;
import com.ut.emrPacs.model.dto.request.authentication.token.ClientCredentialsRequest;
import com.ut.emrPacs.model.dto.request.authentication.token.RefreshTokenRequest;
import com.ut.emrPacs.service.service.AuthService;
import com.ut.emrPacs.config.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.Auth.BASE_PATH)
@Tag(name = "1. Authentication Controller", description = "Endpoints for authentication and token management.")
@Validated
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping(ApiConstants.Auth.LOGIN_PATH)
    @Operation(summary = "Log in", description = "Module -> Authentication. Endpoint -> POST /auth/auth-login")
    public ResponseMessage<BaseResult> login(@Valid @RequestBody LoginRequest loginRequest,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        return authService.handleLogin(loginRequest, request, response);
    }

    @PostMapping(ApiConstants.Auth.LOGOUT_PATH)
    @Operation(summary = "Log out", description = "Module -> Authentication. Endpoint -> POST /auth/auth-logout")
    public ResponseMessage<BaseResult> logout(
            @Valid @RequestBody(required = false) RefreshTokenRequest refreshTokenRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return authService.handleLogout(refreshTokenRequest, request, response);
    }

    @PostMapping(ApiConstants.Auth.REFRESH_PATH)
    @Operation(summary = "Refresh access token", description = "Module -> Authentication. Endpoint -> POST /auth/auth-refresh")
    public ResponseMessage<BaseResult> refreshToken(
            @Valid @RequestBody(required = false) RefreshTokenRequest refreshTokenRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return authService.refreshAccessToken(refreshTokenRequest, request, response);
    }

    @PostMapping(ApiConstants.Auth.CLIENT_CREDENTIALS_PATH)
    @Operation(summary = "Client credentials token", description = "Module -> Authentication. Endpoint -> POST /auth/auth-client-credentials")
    public ResponseMessage<BaseResult> clientCredentials(
            @Valid @RequestBody ClientCredentialsRequest clientCredentialsRequest,
            HttpServletRequest request
    ) {
        return authService.handleClientCredentials(clientCredentialsRequest, request);
    }
}
