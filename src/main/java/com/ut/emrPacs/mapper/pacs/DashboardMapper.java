package com.ut.emrPacs.mapper.pacs;

import com.ut.emrPacs.model.components.pacs.dashboard.WorklistStatusCountRow;
import com.ut.emrPacs.model.dto.response.dashboard.DashboardWorklistSnapshotItemResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DashboardMapper {
    Long countRecentPatients(@Param("hospitalId") Long hospitalId);

    Long countWorklistItems(@Param("hospitalId") Long hospitalId);

    Long countStudies(@Param("hospitalId") Long hospitalId);

    Long countTotalDicomServers(@Param("hospitalId") Long hospitalId);

    Long countActiveDicomServers(@Param("hospitalId") Long hospitalId);

    Long countInactiveDicomServers(@Param("hospitalId") Long hospitalId);

    Long countMappedModalities(@Param("hospitalId") Long hospitalId);

    Long countUnmappedModalities(@Param("hospitalId") Long hospitalId);

    List<WorklistStatusCountRow> listWorklistStatusCounts(@Param("hospitalId") Long hospitalId);

    Long countLongWaitingWorklists(@Param("hospitalId") Long hospitalId, @Param("thresholdMinutes") Integer thresholdMinutes);

    Long countTodayAssignedWorklists(@Param("hospitalId") Long hospitalId);

    Long countTodayCancelledWorklists(@Param("hospitalId") Long hospitalId);

    List<DashboardWorklistSnapshotItemResponse> listWorklistSnapshot(@Param("hospitalId") Long hospitalId, @Param("limit") Integer limit);
}
