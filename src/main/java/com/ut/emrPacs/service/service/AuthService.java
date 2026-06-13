package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.authentication.login.LoginRequest;
import com.ut.emrPacs.model.dto.request.authentication.token.ClientCredentialsRequest;
import com.ut.emrPacs.model.dto.request.authentication.token.RefreshTokenRequest;
import com.ut.emrPacs.model.users.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    ResponseMessage<BaseResult> handleLogin(LoginRequest loginUser, HttpServletRequest httpServletRequest, HttpServletResponse response);

    ResponseMessage<BaseResult> handleLogout(HttpServletRequest request, HttpServletResponse response);

    ResponseMessage<BaseResult> refreshAccessToken(RefreshTokenRequest refreshTokenRequest, HttpServletRequest request, HttpServletResponse response);

    ResponseMessage<BaseResult> handleClientCredentials(ClientCredentialsRequest request, HttpServletRequest httpServletRequest);

    User findUserByUsername(String username);

    User findUserByEmail(String email);
}
