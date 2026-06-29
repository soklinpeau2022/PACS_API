package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.dto.request.authentication.role.RoleCreateRequest;
import com.ut.emrPacs.model.dto.request.authentication.role.RoleDataUpdate;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.RoleListFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface RoleService {
    ResponseMessage<BaseResult> listRole(RoleListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> listRoleUserGroupl(RoleListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> summarizeRoleUserGroups(RoleListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> getRoleById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> menu(HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> createRole(RoleCreateRequest roleData, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> updateRole(RoleDataUpdate roleDataUpdate, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deleteRole(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

}
