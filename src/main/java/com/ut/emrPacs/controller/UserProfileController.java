package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.authentication.user.UserProfileUpdateRequest;
import com.ut.emrPacs.service.service.UserProfileService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
@RestController
@Validated
@RequestMapping(ApiConstants.UserProfile.BASE_PATH)
@Tag(name = "19. User Profile Controller", description = "Endpoints for current user profile management.")
@Timed
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

    @PostMapping(ApiConstants.UserProfile.GET_PATH)
    @Operation(
        summary = "Get my profile",
        description = "Get current authenticated user profile. Module -> UserProfile. Endpoint -> POST /user-profile/get. Frontend integration: No explicit frontend payload parameters. Response format -> ResponseMessage<BaseResult> with success, message, and data; list endpoints also include pagination metadata when applicable. Security -> use Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> getProfile(HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return userProfileService.getProfile(httpServletRequest);
    }

    @PostMapping(value = ApiConstants.UserProfile.UPDATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Update my profile",
        description = "Update current authenticated user profile (requires old password validation). Module -> UserProfile. Endpoint -> POST /user-profile/update. Frontend integration: Request details -> Request body UserProfileUpdateRequest. Response format -> ResponseMessage<BaseResult> with success, message, and data; list endpoints also include pagination metadata when applicable. Security -> use Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> updateProfile(@Valid @RequestBody UserProfileUpdateRequest userProfileUpdateRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return userProfileService.updateProfile(userProfileUpdateRequest, httpServletRequest);
    }
}



