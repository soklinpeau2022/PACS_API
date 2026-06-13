package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.dto.request.dashboard.DashboardOverviewRequest;
import com.ut.emrPacs.service.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;

@RestController
@RequestMapping(ApiConstants.Dashboard.BASE_PATH)
@Tag(
        name = "15. Dashboard Controller",
        description = "Dashboard overview controller for operational KPIs, Worklist snapshot, and system alerts."
)
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @PostMapping(ApiConstants.Dashboard.OVERVIEW_PATH)
    @Operation(summary = "Dashboard overview", description = "Module -> Dashboard. Endpoint -> POST /dashboard/dashboard-overview")
    public ResponseMessage<BaseResult> overview(@RequestBody(required = false) DashboardOverviewRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in.");
        }
        return dashboardService.getOverview(request, httpServletRequest);
    }
}
