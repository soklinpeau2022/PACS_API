package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import jakarta.servlet.http.HttpServletRequest;
import java.net.UnknownHostException;

public interface PermissionService {
    ResponseMessage<BaseResult> permissionTree(Long userId, Long hospitalId, Long permissionVersion, HttpServletRequest httpServletRequest) throws UnknownHostException;
    ResponseMessage<BaseResult> saveRolePermissions(Long roleId, Long[] moduleDetailIds, Long actorUserId, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
