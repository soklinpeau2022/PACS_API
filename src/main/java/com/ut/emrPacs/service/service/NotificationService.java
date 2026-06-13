package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.NotificationFilter;
import jakarta.servlet.http.HttpServletRequest;

public interface NotificationService {

    ResponseMessage<BaseResult> listNotifications(NotificationFilter filter, HttpServletRequest httpServletRequest);
}
