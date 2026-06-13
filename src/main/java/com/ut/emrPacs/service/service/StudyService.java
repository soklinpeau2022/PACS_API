package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.StudyListFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.UnknownHostException;

public interface StudyService {
    ResponseMessage<BaseResult> list(StudyListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;
    ResponseMessage<BaseResult> findById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;
    ResponseMessage<BaseResult> getViewerInfo(Long id, Long hospitalId, String mode, String viewerAccess, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
