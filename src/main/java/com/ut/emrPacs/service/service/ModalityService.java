package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.ModalityFilter;
import com.ut.emrPacs.model.dto.request.systemSettings.hospital.HospitalModalityRequest;
import com.ut.emrPacs.model.dto.request.systemSettings.modality.ModalityRequestUpdate;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface ModalityService {
    ResponseMessage<BaseResult> listModality(ModalityFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getModalityById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> createModality(ModalityRequestUpdate modalityRequestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateModality(ModalityRequestUpdate modalityRequestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteModality(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> listHospitalModalityByUser(HospitalModalityRequest hospitalModalityRequest, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
