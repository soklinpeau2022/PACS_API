package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.UserListFilter;
import com.ut.emrPacs.model.dto.request.authentication.changePassword.ChangePasswordRequest;
import com.ut.emrPacs.model.dto.request.authentication.user.EditProfileRequest;
import com.ut.emrPacs.model.dto.request.authentication.user.UserUpdateRequest;
import com.ut.emrPacs.model.dto.response.authentication.user.UserResponse;
import com.ut.emrPacs.model.users.User;
import com.ut.emrPacs.model.users.UserRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface UserService {

    ResponseMessage<BaseResult> listUser(UserListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> listUserGroup(UserListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> createUser(UserRequest userRequest, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getUserById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<String> changePassword(ChangePasswordRequest changePasswordRequest, HttpServletRequest httpServletRequest) throws UnknownHostException;

    User getUserAuth();

    ResponseMessage<UserResponse> me(HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> editProfile(EditProfileRequest editProfileRequest, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateUser(UserUpdateRequest updateRequest, HttpServletRequest httpServletRequest) throws UnknownHostException;
    
    ResponseMessage<String> deleteUser(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

}
