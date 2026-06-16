package com.ut.emrPacs.mapper.notification;

import com.ut.emrPacs.model.dto.response.notification.RealtimeNotificationEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RealtimeNotificationMapper {

    int insertEvent(RealtimeNotificationEvent event);

    Long findLatestEventId(@Param("hospitalId") Long hospitalId);

    List<RealtimeNotificationEvent> listEventsAfter(
            @Param("hospitalId") Long hospitalId,
            @Param("afterId") Long afterId,
            @Param("limit") int limit
    );

    int deleteEventsOlderThan(@Param("retentionDays") int retentionDays);
}
