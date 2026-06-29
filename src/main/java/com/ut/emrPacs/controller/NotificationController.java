package com.ut.emrPacs.controller;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.NotificationFilter;
import com.ut.emrPacs.model.dto.request.notification.NotificationActionRequest;
import com.ut.emrPacs.service.service.NotificationService;
import com.ut.emrPacs.service.service.RealtimeNotificationService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@Validated
@RequestMapping(ApiConstants.Notification.BASE_PATH)
@Tag(name = "18. Notification Controller", description = "Hospital-scoped clinical notifications for the application topbar.")
@Timed
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RealtimeNotificationService realtimeNotificationService;

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

    @PostMapping(ApiConstants.Notification.MARK_READ_PATH)
    @Operation(
            summary = "Mark notifications read",
            description = "Mark explicit notification IDs as read for the logged-in user and hospital. Endpoint -> POST /notification/notification-read."
    )
    public ResponseMessage<BaseResult> markRead(@Valid @RequestBody(required = false) NotificationActionRequest actionRequest,
                                                HttpServletRequest request) {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return notificationService.markNotificationsRead(actionRequest, request);
    }

    @PostMapping(ApiConstants.Notification.CLEAR_PATH)
    @Operation(
            summary = "Clear notifications",
            description = "Clear explicit notification IDs for the logged-in user and hospital. Endpoint -> POST /notification/notification-clear."
    )
    public ResponseMessage<BaseResult> clear(@Valid @RequestBody(required = false) NotificationActionRequest actionRequest,
                                             HttpServletRequest request) {
        if (UserAuthSession.getCurrentUser() == null) {
            return ResponseMessageUtils.makeResponse(false, 401, "Unauthorized", "You must be logged in");
        }
        return notificationService.clearNotifications(actionRequest, request);
    }

    @GetMapping(value = ApiConstants.Notification.STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream realtime clinical notifications",
            description = "Open an authenticated hospital-scoped SSE stream. Use afterId to replay events after the last received cursor."
    )
    public SseEmitter stream(@RequestParam(required = false) Long afterId, HttpServletResponse response) {
        if (UserAuthSession.getCurrentUser() == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "You must be logged in");
        }
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        return realtimeNotificationService.subscribe(afterId);
    }
}
