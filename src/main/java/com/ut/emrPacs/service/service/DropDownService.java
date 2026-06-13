package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.DropDownFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.UnknownHostException;

public interface DropDownService {

    ResponseMessage<BaseResult> getListCountries(DropDownFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;
    ResponseMessage<BaseResult> getListHospitalsByUser(DropDownFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getListModalities(DropDownFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getListModalityCatalog(DropDownFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getListDicomServers(DropDownFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getListUserGroupMembers(DropDownFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getListUsers(DropDownFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getListPatients(DropDownFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getListUserGroups(DropDownFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
