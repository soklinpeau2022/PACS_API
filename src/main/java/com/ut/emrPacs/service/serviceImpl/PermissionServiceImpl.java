package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.cache.config.CacheConfig;
import com.ut.emrPacs.cache.permission.PermissionCacheService;
import com.ut.emrPacs.mapper.permission.PermissionMapper;
import com.ut.emrPacs.mapper.permission.RolePermissionMapper;
import com.ut.emrPacs.mapper.user.RoleMapper;
import com.ut.emrPacs.mapper.user.ModuleTypeMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.net.UnknownHostException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class PermissionServiceImpl implements PermissionService {

    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private RolePermissionMapper rolePermissionMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private ModuleTypeMapper moduleTypeMapper;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private PermissionCacheService permissionCacheService;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Override
    public ResponseMessage<BaseResult> permissionTree(Long userId, Long hospitalId, Long permissionVersion, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var tree = moduleTypeMapper.getAllActiveModuleTreeFlat(userId, hospitalId);
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert("/permission/permission-tree", null, null, "Permission", "Permission (Tree)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", tree, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/permission/permission-tree", errorLine, error.toString(), "Permission", "Permission (Tree)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> saveRolePermissions(Long roleId, Long[] moduleDetailIds, Long actorUserId, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (roleId == null || roleId <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Role id is required.", false));
            }

            if (roleMapper.getRoleByIdAnyHospital(roleId) == null || roleMapper.getRoleByIdAnyHospital(roleId).isEmpty()) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Role not found.", false));
            }

            List<Long> validModuleDetailIds = moduleDetailIds == null ? List.of() : java.util.Arrays.stream(moduleDetailIds)
                    .filter(id -> id != null && id > 0)
                    .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toCollection(LinkedHashSet::new), ArrayList::new));

            if (!validModuleDetailIds.isEmpty()) {
                Long activeCount = permissionMapper.countActiveModuleDetailsByIds(validModuleDetailIds);
                if (activeCount == null || activeCount != validModuleDetailIds.size()) {
                    return ResponseMessageUtils.makeResponse(false, messageService.message("One or more module detail ids are invalid or inactive.", false));
                }
            }

            rolePermissionMapper.deleteByRoleId(roleId);
            if (!validModuleDetailIds.isEmpty()) {
                rolePermissionMapper.insertBatch(roleId, validModuleDetailIds, actorUserId);
            }

            List<Long> impactedUsers = rolePermissionMapper.findUserIdsByRoleId(roleId);
            for (Long userId : impactedUsers) {
                rolePermissionMapper.bumpPermissionVersion(userId);
            }
            permissionCacheService.invalidateByUsers(impactedUsers);
            evictCache(CacheConfig.DROPDOWN_USER_GROUPS);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert("/permission/permission-save-role-permissions", null, null, "Permission", "Permission (Save Role)", "Edit", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert("/permission/permission-save-role-permissions", errorLine, error.toString(), "Permission", "Permission (Save Role)", "Edit", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private void evictCache(String cacheName) {
        if (cacheManager == null || cacheName == null || cacheName.isBlank()) {
            return;
        }
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

}

