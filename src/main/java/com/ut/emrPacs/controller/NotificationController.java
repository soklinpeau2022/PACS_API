package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.NotificationFilter;
import com.ut.emrPacs.service.service.NotificationService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(ApiConstants.Notification.BASE_PATH)
@Tag(name = "18. Notification Controller", description = "Hospital-scoped clinical notifications for the application topbar.")
@Timed
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping(ApiConstants.Notification.LIST_PATH)
    @Operation(
            summary = "List notifications",
            description = "List recent worklist and image-received notifications for the logged-in user's hospital. Endpoint -> POST /notification/notification-list."
    )
    public ResponseMessage<BaseResult> list(@Valid @RequestBody(required = false) NotificationFilter filter, HttpServletRequest request) {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return notificationService.listNotifications(filter, request);
    }
}
