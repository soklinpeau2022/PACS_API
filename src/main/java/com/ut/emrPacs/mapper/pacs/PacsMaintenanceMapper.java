package com.ut.emrPacs.mapper.pacs;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * Large-scale maintenance hooks backed by database functions introduced in the
 * V186/V192/V198 migrations. Used by {@code PacsLargeScaleMaintenanceScheduler}
 * to keep the dashboard summary table fresh and run partition
 * maintenance safely.
 */
@Mapper
public interface PacsMaintenanceMapper {

    /**
     * Refreshes pacs_daily_stats for one day. Pass a null hospitalId to refresh
     * every active hospital. Returns the number of summary rows written.
     */
    @Select("SELECT pacs_refresh_daily_stats(#{statDate}, #{hospitalId})")
    Integer refreshDailyStats(@Param("statDate") LocalDate statDate,
                              @Param("hospitalId") Long hospitalId);

    /** Takes a session-level advisory lock so only one API instance runs partition maintenance. */
    @Select("SELECT pg_try_advisory_lock(hashtext('emr_pacs_partition_maintenance'))")
    Boolean tryPartitionMaintenanceLock();

    /** Releases the partition-maintenance advisory lock held by this DB session. */
    @Select("SELECT pg_advisory_unlock(hashtext('emr_pacs_partition_maintenance'))")
    Boolean releasePartitionMaintenanceLock();

    /** Runs config-driven fixed-log and policy-aware partition maintenance. */
    @Select("SELECT run_partition_maintenance()")
    String runPartitionMaintenance();

    /** Takes a session-level advisory lock so only one API instance refreshes weekly caches. */
    @Select("SELECT pg_try_advisory_lock(hashtext('pacs_week_cache_refresh'))")
    Boolean tryWeekCacheRefreshLock();

    /** Releases the weekly-cache advisory lock held by this DB session. */
    @Select("SELECT pg_advisory_unlock(hashtext('pacs_week_cache_refresh'))")
    Boolean releaseWeekCacheRefreshLock();

    /** Rebuilds recent Worklist/Study list caches from source-of-truth tables. */
    @Select("SELECT refresh_pacs_week_cache()")
    String refreshPacsWeekCache();

    /** Removes expired rows from recent Worklist/Study list caches. */
    @Select("SELECT cleanup_pacs_week_cache()")
    String cleanupPacsWeekCache();
}
