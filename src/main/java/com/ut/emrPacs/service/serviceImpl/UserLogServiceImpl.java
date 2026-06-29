package com.ut.emrPacs.service.serviceImpl;
import com.ut.emrPacs.helper.pagination.PaginationHelper;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.mapper.report.users.UserLogMapper;
import com.ut.emrPacs.mapper.user.ModuleMapper;
import com.ut.emrPacs.mapper.user.ModuleTypeMapper;
import com.ut.emrPacs.mapper.user.UserPermissionMapper;
import com.ut.emrPacs.model.base.*;
import com.ut.emrPacs.model.base.filter.UserLogFilter;
import com.ut.emrPacs.model.dto.response.reports.users.UserLogResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.UserLogService;
import com.ut.emrPacs.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.UnknownHostException;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserLogServiceImpl implements UserLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserLogServiceImpl.class);

    @Autowired
    private UserLogMapper userLogMapper;

    @Autowired
    private UserPermissionMapper permissionMapper;

    @Autowired
    private ModuleTypeMapper moduleTypeMapper;

    @Autowired
    private ModuleMapper moduleMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ActivityLogService activityLogService;

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> listUserLog(UserLogFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load user log list and return API response.
        LocalTime startDuration = LocalTime.now();
        try {
            userService.getUserAuth().getId();

            UserLogFilter safeFilter = filter == null ? new UserLogFilter() : filter;
            Long total = safeFilter.getLastUserLogId() == null
                    ? userLogMapper.countList(safeFilter)
                    : null;
            Pagination pagination = PaginationHelper.buildAndApplyOffsetOrDefault(safeFilter, total);

            List<UserLogResponse> userLogResponses = userLogMapper.listUserLog(safeFilter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.UserLog.BASE_PATH + ApiConstants.UserLog.LIST_PATH, null, null, "UserLog", "UserLog (List)", "List", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", userLogResponses, pagination, true));
        } catch (Exception error) {
            LOGGER.error("Failed to list user logs", error);
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0)
                    ? (long) error.getStackTrace()[0].getLineNumber() : null;
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.UserLog.BASE_PATH + ApiConstants.UserLog.LIST_PATH, errorLine, error.toString(), "UserLog", "UserLog (List)", "List", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> getUserLogById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load user log by id and return API response.
        LocalTime startDuration = LocalTime.now();
        try {
            List<UserLogResponse> userLogResponses = userLogMapper.getUserLogById(id);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.UserLog.BASE_PATH + ApiConstants.UserLog.FIND_PATH, null, null, "UserLog", "UserLog (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", userLogResponses, true));
        } catch (Exception error) {
            LOGGER.error("Failed to get user log by id={}", id, error);
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0)
                    ? (long) error.getStackTrace()[0].getLineNumber() : null;
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.UserLog.BASE_PATH + ApiConstants.UserLog.FIND_PATH, errorLine, error.toString(), "UserLog", "UserLog (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

}
