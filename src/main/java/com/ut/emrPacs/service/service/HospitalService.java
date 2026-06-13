package com.ut.emrPacs.service.service;

import java.net.UnknownHostException;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.HospitalListFilter;
import com.ut.emrPacs.model.dto.request.systemSettings.hospital.HospitalRequestUpdate;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

public interface HospitalService {

    ResponseMessage<BaseResult> listHospital(HospitalListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getHospitalById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> createHospital(HospitalRequestUpdate hospitalRequestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateHospital(HospitalRequestUpdate hospitalRequestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> createHospital(HospitalRequestUpdate hospitalRequestUpdate, MultipartFile logo, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateHospital(HospitalRequestUpdate hospitalRequestUpdate, MultipartFile logo, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
