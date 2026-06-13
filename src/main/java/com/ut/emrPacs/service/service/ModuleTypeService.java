package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.ModuleTypeFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface ModuleTypeService {

    ResponseMessage<BaseResult> listModuleType(ModuleTypeFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getModuleTypeById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

}
