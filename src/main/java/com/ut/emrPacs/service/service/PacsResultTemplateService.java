package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.PacsResultTemplateFilter;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultTemplateSaveRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface PacsResultTemplateService {
    ResponseMessage<BaseResult> listTemplates(PacsResultTemplateFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> findTemplate(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> createTemplate(PacsResultTemplateSaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateTemplate(PacsResultTemplateSaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteTemplate(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
