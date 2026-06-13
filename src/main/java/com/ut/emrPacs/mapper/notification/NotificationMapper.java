package com.ut.emrPacs.mapper.notification;

import com.ut.emrPacs.model.base.filter.NotificationFilter;
import com.ut.emrPacs.model.dto.response.notification.NotificationResponse;

import java.util.List;

public interface NotificationMapper {

    List<NotificationResponse> listNotifications(NotificationFilter filter);

    Long countNotifications(NotificationFilter filter);
}
