package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.UserLogFilter;
import com.ut.emrPacs.service.service.UserLogService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.UnknownHostException;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
@RestController
@Validated
@RequestMapping(ApiConstants.UserLog.BASE_PATH)
@Tag(name = "17. User Log Controller", description = "Endpoints for user activity logs.")
@Timed
public class UserLogController {
    @Autowired
    private UserLogService userLogService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.UserLog.LIST_PATH)
    @Operation(
        summary = "List user logs",
        description = "List user logs. Module -> User Log. Endpoint -> POST /report/user-log/user-log-list. Frontend integration: Request details -> request body UserLogFilter. Filter object: send only used fields; supports filtering and pagination fields (for example page and rowsPerPage) when available. Response format -> ResponseMessage<BaseResult> with success, message, data, and pagination metadata when applicable. Security -> use a Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> listUserLog(@Valid @RequestBody UserLogFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return userLogService.listUserLog(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.UserLog.FIND_PATH)
    @Operation(
        summary = "Find user log by public key",
        description = "Find a user log by public key. Module -> User Log. Endpoint -> POST /report/user-log/user-log-find/{key}. Response format -> ResponseMessage<BaseResult> with success, message, and data. Security -> use a Bearer token on protected endpoints."
    )
    public ResponseMessage<BaseResult> getUserLogById(@PathVariable String id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return userLogService.getUserLogById(publicEntityKeyResolver.resolveFromPath(Entity.USER_LOG, id, "User log"), httpServletRequest);
    }

}


