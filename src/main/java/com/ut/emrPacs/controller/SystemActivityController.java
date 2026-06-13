package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.ActivityFilter;
import com.ut.emrPacs.service.service.ActivityLogService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;

@RestController
@Validated
@RequestMapping(ApiConstants.SystemActivity.BASE_PATH)
@Tag(
        name = "13. System Activity Controller",
        description = "System activity log controller. Business status: 1 = Success, 2 = Error. Frontend should use this status for log outcome color/state."
)
@Timed
public class SystemActivityController {

    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.SystemActivity.LIST_PATH)
    @Operation(summary = "List system activity logs", description = "Module -> System Activity. Endpoint -> POST /system-activity/system-activity-list.")
    public ResponseMessage<BaseResult> list(@Valid @RequestBody ActivityFilter filter, HttpServletRequest request) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return activityLogService.listActivityLog(filter, request);
    }

    @PostMapping(ApiConstants.SystemActivity.FIND_PATH)
    @Operation(summary = "Find system activity log by public key", description = "Module -> System Activity. Endpoint -> POST /system-activity/system-activity-find/{key}.")
    public ResponseMessage<BaseResult> findById(@PathVariable String id, HttpServletRequest request) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return activityLogService.getActivityLogById(publicEntityKeyResolver.resolveFromPath(Entity.SYSTEM_ACTIVITY, id, "System activity"), request);
    }
}


