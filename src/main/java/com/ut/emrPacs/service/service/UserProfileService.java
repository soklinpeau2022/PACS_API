package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.authentication.user.UserProfileUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface UserProfileService {

    ResponseMessage<BaseResult> getProfile(HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateProfile(UserProfileUpdateRequest userProfileUpdateRequest,
                                              HttpServletRequest httpServletRequest) throws UnknownHostException;
}

