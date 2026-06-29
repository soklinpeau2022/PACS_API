package com.ut.emrPacs.model.dto.request.notification;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationActionRequestContractTest {

    @Test
    void actionRequestExposesPropertiesUsedBySharedNotificationSql() {
        MetaObject metaObject = SystemMetaObject.forObject(new NotificationActionRequest());

        assertTrue(metaObject.hasGetter("hospitalId"));
        assertTrue(metaObject.hasGetter("userId"));
        assertTrue(metaObject.hasGetter("notificationIds"));
        assertTrue(metaObject.hasGetter("days"));
    }
}
