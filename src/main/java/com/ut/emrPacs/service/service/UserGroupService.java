package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.authentication.userGroup.UserGroupCreateRequest;
import com.ut.emrPacs.model.dto.request.authentication.userGroup.UserGroupListRequest;
import com.ut.emrPacs.model.dto.request.authentication.userGroup.UserGroupUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface UserGroupService {
    ResponseMessage<BaseResult> list(UserGroupListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> add(UserGroupCreateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> update(UserGroupUpdateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> delete(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
