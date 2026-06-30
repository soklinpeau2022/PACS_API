package com.ut.emrPacs.mapper.pacs;

import com.ut.emrPacs.model.components.pacs.dashboard.WorklistStatusCountRow;
import com.ut.emrPacs.model.dto.response.dashboard.DashboardWorklistSnapshotItemResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DashboardMapper {
    Long countRecentPatients(@Param("hospitalId") Long hospitalId,
                             @Param("dateFrom") LocalDate dateFrom,
                             @Param("dateTo") LocalDate dateTo);

    Long countWorklistItems(@Param("hospitalId") Long hospitalId,
                            @Param("dateFrom") LocalDate dateFrom,
                            @Param("dateTo") LocalDate dateTo);

    Long countStudies(@Param("hospitalId") Long hospitalId,
                      @Param("dateFrom") LocalDate dateFrom,
                      @Param("dateTo") LocalDate dateTo);

    /**
     * Cumulative received-study count sourced from the pacs_daily_stats summary
     * table. Used in place of the live COUNT(*) on pacs_studies at large scale
     * (see dashboard.studies-count-source).
     */
    Long sumReceivedStudies(@Param("hospitalId") Long hospitalId,
                            @Param("dateFrom") LocalDate dateFrom,
                            @Param("dateTo") LocalDate dateTo);

    Long countTotalDicomServers(@Param("hospitalId") Long hospitalId);

    Long countActiveDicomServers(@Param("hospitalId") Long hospitalId);

    Long countInactiveDicomServers(@Param("hospitalId") Long hospitalId);

    Long countMappedModalities(@Param("hospitalId") Long hospitalId);

    Long countUnmappedModalities(@Param("hospitalId") Long hospitalId);

    List<WorklistStatusCountRow> listWorklistStatusCounts(@Param("hospitalId") Long hospitalId,
                                                          @Param("dateFrom") LocalDate dateFrom,
                                                          @Param("dateTo") LocalDate dateTo);

    Long countLongWaitingWorklists(@Param("hospitalId") Long hospitalId,
                                   @Param("thresholdMinutes") Integer thresholdMinutes,
                                   @Param("dateFrom") LocalDate dateFrom,
                                   @Param("dateTo") LocalDate dateTo);

    Long countTodayAssignedWorklists(@Param("hospitalId") Long hospitalId);

    Long countTodayCancelledWorklists(@Param("hospitalId") Long hospitalId);

    List<DashboardWorklistSnapshotItemResponse> listWorklistSnapshot(@Param("hospitalId") Long hospitalId,
                                                                     @Param("limit") Integer limit,
                                                                     @Param("dateFrom") LocalDate dateFrom,
                                                                     @Param("dateTo") LocalDate dateTo);
}
