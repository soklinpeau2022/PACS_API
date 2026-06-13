package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.ActivityFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;
import java.time.LocalTime;

public interface ActivityLogService {

    ResponseMessage<BaseResult> listActivityLog(ActivityFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getActivityLogById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    void insert(String endpoint, Long line, String bug, String moduleName, String moduleId, String act, int status, String description, LocalTime startDuration, LocalTime endDuration, HttpServletRequest httpServletRequest) throws UnknownHostException;

}