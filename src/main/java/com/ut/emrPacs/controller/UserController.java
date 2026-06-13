package com.ut.emrPacs.controller;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.UserListFilter;
import com.ut.emrPacs.model.dto.request.authentication.changePassword.ChangePasswordRequest;
import com.ut.emrPacs.model.dto.request.authentication.user.UserUpdateRequest;
import com.ut.emrPacs.model.dto.response.authentication.user.UserResponse;
import com.ut.emrPacs.model.users.UserRequest;
import com.ut.emrPacs.service.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.net.UnknownHostException;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
@RestController
@Validated
@RequestMapping(ApiConstants.User.BASE_PATH)
@Tag(name = "16. User Controller", description = "Endpoints for user management.")

public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;


    @PostMapping(ApiConstants.User.LIST_PATH)
    @Operation(
        summary = "List users",
        description = "List users. Module -> User. Endpoint -> POST /user/user-list. Frontend integration: Request details -> request body filter. Filter object: send only used fields; supports filtering and pagination fields (for example page and rowsPerPage) when available. Response format -> ResponseMessage<BaseResult> with success, message, data, and pagination metadata when applicable. Security scope: userId=1 (super admin) or users with role code USER_HOSPITAL_SCOPE_ALL can view cross-hospital users and filter by hospitalId; other users are restricted to their login hospital."
    )
    public ResponseMessage<BaseResult> listUser(@Valid @RequestBody UserListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return userService.listUser(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.User.GROUP_LIST_PATH)
    @Operation(
        summary = "List user groups",
        description = "List user groups. Module -> User. Endpoint -> POST /user/user-group-list. Frontend integration: follows role-list flow with pagination/search/hospitalId filter. Response fields: id, name, createdBy, createdDate, modifiedBy, modifiedDate, userList[id,username]. Security scope: userId=1 (super admin) or users with role code USER_HOSPITAL_SCOPE_ALL can view cross-hospital groups; others are restricted to their own login hospital."
    )
    public ResponseMessage<BaseResult> listUserGroup(@Valid @RequestBody UserListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return userService.listUserGroup(filter, httpServletRequest);
    }


    @PostMapping(value = ApiConstants.User.CREATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Create user",
        description = "Create a user. Module -> User. Endpoint -> POST /user/user-create. Frontend integration: Request details -> request body UserRequest. Response format -> ResponseMessage<BaseResult> with success, message, and data. Security -> use a Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> add(@Valid @RequestBody UserRequest userRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return userService.createUser(userRequest, httpServletRequest);
    }

    @PostMapping(ApiConstants.User.CHANGE_PASSWORD_PATH)
    @Operation(
        summary = "Change password",
        description = "Change password. Module -> User. Endpoint -> POST /user/user-change-password. Frontend integration: Request details -> request body ChangePasswordRequest. Response format -> ResponseMessage<BaseResult> with success, message, and data. Security -> use a Bearer token on protected endpoints."
    )
    public ResponseMessage<String> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return userService.changePassword(changePasswordRequest, httpServletRequest);
    }


    @PostMapping(ApiConstants.User.ME_PATH)
    @Operation(
        summary = "Get current user",
        description = "Get the current user. Module -> User. Endpoint -> POST /user/user-me. Frontend integration: no request payload is required. Response format -> ResponseMessage<BaseResult> with success, message, and data. Security -> use a Bearer token on protected endpoints."
    )
    public ResponseMessage<UserResponse> me(HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return userService.me(httpServletRequest);
    }


    @PostMapping(ApiConstants.User.FIND_PATH)
    @Operation(
        summary = "Get user by ID",
        description = "Retrieve a single user profile by user ID. Module -> User. Endpoint -> POST /user/user-find/{id}. Frontend integration: Request details -> Path variable 'id' (Long). Response format -> ResponseMessage<BaseResult> with success, message, and data; list endpoints also include pagination metadata when applicable. Security -> use Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> getUserById(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return userService.getUserById(publicEntityKeyResolver.resolveFromPath(Entity.USER, id, "User"), httpServletRequest);
    }


    @PostMapping(value = ApiConstants.User.UPDATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Update user",
        description = "Update an existing user account with editable profile, active status, password, hospital assignments, and user group assignments. Module -> User. Endpoint -> POST /user/user-update. Frontend integration: send UserUpdateRequest as JSON, including hospitalIds for multiple hospitals and userGroupIds for multiple user groups, for example {\"id\":1,\"username\":\"admin\",\"hospitalIds\":[1,2],\"userGroupIds\":[1,3],\"isActive\":1}. Response format -> ResponseMessage<BaseResult> with success, message, and data. Security -> use Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> updateUser(@Valid @RequestBody UserUpdateRequest updateRequest, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return userService.updateUser(updateRequest, httpServletRequest);
    }

    @PostMapping(ApiConstants.User.DELETE_PATH)
    @Operation(
        summary = "Delete user",
        description = "Delete a user. Module -> User. Endpoint -> POST /user/user-delete/{id}. Frontend integration: Request details -> path variable 'id' (Long). Response format -> ResponseMessage<BaseResult> with success, message, and data. Security -> use a Bearer token on protected endpoints."
    )
    public ResponseMessage<String> deleteUser(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {

        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return userService.deleteUser(publicEntityKeyResolver.resolveFromPath(Entity.USER, id, "User"), httpServletRequest);
    }
}


