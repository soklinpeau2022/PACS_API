package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.PatientListFilter;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientCreateRequest;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.net.UnknownHostException;

public interface PatientService {
    ResponseMessage<BaseResult> list(PatientListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;
    ResponseMessage<BaseResult> findById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;
    ResponseMessage<BaseResult> create(PatientCreateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;
    ResponseMessage<BaseResult> update(PatientUpdateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
