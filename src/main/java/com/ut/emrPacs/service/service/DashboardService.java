package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.dashboard.DashboardOverviewRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface DashboardService {
    ResponseMessage<BaseResult> getOverview(DashboardOverviewRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
