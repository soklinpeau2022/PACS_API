package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.UserLogFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface UserLogService {

    ResponseMessage<BaseResult> listUserLog(UserLogFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getUserLogById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

}
