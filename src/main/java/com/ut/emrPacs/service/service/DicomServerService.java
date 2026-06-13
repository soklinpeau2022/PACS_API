package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.DicomServerFilter;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomMachineListRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomMachineRequestUpdate;
import com.ut.emrPacs.model.dto.request.pacs.dicom.DicomServerHealthSettingsRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomServerRequestUpdate;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalModalityServerRouteListRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalModalityServerRouteRequestUpdate;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalModalityServerRouteSaveRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface DicomServerService {
    ResponseMessage<BaseResult> listDicomServers(DicomServerFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> listDicomServerHealth(DicomServerFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getDicomServerHealthSummary(DicomServerFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getDicomServerHealthSettings(HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateDicomServerHealthSettings(DicomServerHealthSettingsRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getDicomServerById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> createDicomServer(HospitalDicomServerRequestUpdate requestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateDicomServer(HospitalDicomServerRequestUpdate requestUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteDicomServer(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> listDicomMachines(HospitalDicomMachineListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getDicomMachineById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> createDicomMachine(HospitalDicomMachineRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateDicomMachine(HospitalDicomMachineRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteDicomMachine(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> listRouting(HospitalModalityServerRouteListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getRoutingById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> createRouting(HospitalModalityServerRouteSaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateRouting(HospitalModalityServerRouteRequestUpdate request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteRouting(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> buildRoutingDicomServerConfig(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
