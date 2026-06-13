package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.mapper.user.ModuleMapper;
import com.ut.emrPacs.mapper.user.ModuleTypeMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.ModuleTypeFilter;
import com.ut.emrPacs.model.dto.response.authentication.module.MenuGroupResponse;
import com.ut.emrPacs.model.dto.response.authentication.module.ModuleType;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.ModuleTypeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ut.emrPacs.helper.FunctionHelper;
import java.net.UnknownHostException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModuleTypeServiceImpl implements ModuleTypeService {

    @Autowired
    private ModuleTypeMapper moduleTypeMapper;

    @Autowired
    private ModuleMapper moduleMapper;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ActivityLogService activityLogService;

    @Override
    public ResponseMessage<BaseResult> listModuleType(ModuleTypeFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            ModuleTypeFilter safeFilter = filter == null ? new ModuleTypeFilter() : filter;
            Pagination pagination = PaginationHelper.buildAndApplyOffsetOrDefault(
                    safeFilter,
                    moduleTypeMapper.countList(safeFilter),
                    1,
                    100
            );

            List<ModuleType> moduleTypeList = moduleTypeMapper.listModuleType(safeFilter);
            if (moduleTypeList != null) {
                for (ModuleType moduleType : moduleTypeList) {
                    if (moduleType == null || moduleType.getId() == null) {
                        continue;
                    }
                    Long moduleTypeId = moduleType.getId();
                    if (safeFilter.getRoleId() == null || safeFilter.getRoleId() <= 0) {
                        moduleType.setModuleList(moduleMapper.getModuleById(moduleTypeId));
                    } else {
                        moduleType.setModuleList(moduleMapper.getOneByRoleId(moduleTypeId, safeFilter.getRoleId()));
                    }
                }
            }
            BaseResult baseResult = messageService.message("Success", moduleTypeList, pagination, true);
            baseResult.setMenuGroupList(buildMenuGroupsFromDb(moduleTypeList));

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    ApiConstants.ModuleType.LIST_PATH,
                    null,
                    null,
                    "Module Type",
                    "Module Type (View)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, baseResult);
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0)
                    ? (long) error.getStackTrace()[0].getLineNumber()
                    : null;
            activityLogService.insert(
                    ApiConstants.ModuleType.LIST_PATH,
                    errorLine,
                    error.toString(),
                    "Module Type",
                    "Module Type (View)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> getModuleTypeById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            List<ModuleType> moduleTypes = moduleTypeMapper.getModuleTypeById(id);
            if (moduleTypes != null) {
                for (ModuleType moduleType : moduleTypes) {
                    if (moduleType == null || moduleType.getId() == null) {
                        continue;
                    }
                    moduleType.setModuleList(moduleMapper.getModuleById(moduleType.getId()));
                }
            }

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(
                    ApiConstants.ModuleType.BASE_PATH + ApiConstants.ModuleType.FIND_PATH,
                    null,
                    null,
                    "Module Type",
                    "Module Type (View)",
                    "View",
                    1,
                    "Success",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", moduleTypes, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0)
                    ? (long) error.getStackTrace()[0].getLineNumber()
                    : null;
            activityLogService.insert(
                    ApiConstants.ModuleType.BASE_PATH + ApiConstants.ModuleType.FIND_PATH,
                    errorLine,
                    error.toString(),
                    "Module Type",
                    "Module Type (View)",
                    "View",
                    2,
                    "Error",
                    startDuration,
                    endDuration,
                    httpServletRequest
            );
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private static List<MenuGroupResponse> buildMenuGroupsFromDb(List<ModuleType> moduleTypes) {
        Map<String, MenuGroupResponse> groupsByCode = new LinkedHashMap<>();
        if (moduleTypes == null || moduleTypes.isEmpty()) {
            return new ArrayList<>();
        }

        for (ModuleType moduleType : moduleTypes) {
            if (moduleType == null || isBlank(moduleType.getGroupCode())) {
                continue;
            }
            groupsByCode.computeIfAbsent(moduleType.getGroupCode(), groupCode -> {
                MenuGroupResponse response = new MenuGroupResponse();
                response.setGroupCode(groupCode);
                response.setGroupName(moduleType.getGroupName());
                response.setOrderNo(moduleType.getGroupOrderNo());
                return response;
            });
        }

        List<MenuGroupResponse> groups = new ArrayList<>(groupsByCode.values());
        groups.sort(Comparator
                .comparing((MenuGroupResponse item) -> item.getOrderNo() == null ? Integer.MAX_VALUE : item.getOrderNo())
                .thenComparing(item -> item.getGroupCode() == null ? "" : item.getGroupCode()));
        return groups;
    }

    private static boolean isBlank(String value) {
        return !FunctionHelper.hasText(value);
    }
}
