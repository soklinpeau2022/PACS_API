package com.ut.emrPacs.scheduler;

import com.ut.emrPacs.mapper.pacs.PacsMaintenanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Keeps the large-scale structures healthy:
 *  - refreshes pacs_daily_stats so dashboards can read summaries instead of
 *    running live COUNT(*) on high-growth clinical tables;
 *  - runs config-driven partition maintenance so inserts avoid DEFAULT
 *    partitions, fixed technical logs age out, and policy/audit tables only
 *    report policy-eligible cleanup by default.
 *  - refreshes small weekly Worklist/Study caches used by default list screens
 *    while the source-of-truth tables keep full history.
 *
 * Every action is best-effort and individually guarded: a failure is logged and
 * never breaks the schedule, matching the resilience style of the other PACS
 * schedulers. All cadences are configurable; the whole component can be disabled.
 */
@Component
public class PacsLargeScaleMaintenanceScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacsLargeScaleMaintenanceScheduler.class);

    private final PacsMaintenanceMapper maintenanceMapper;

    @Value("${pacs.maintenance.daily-stats.enabled:true}")
    private boolean dailyStatsEnabled;

    @Value("${pacs.maintenance.partitions.enabled:true}")
    private boolean partitionsEnabled;

    @Value("${pacs.maintenance.week-cache.enabled:true}")
    private boolean weekCacheEnabled;

    public PacsLargeScaleMaintenanceScheduler(PacsMaintenanceMapper maintenanceMapper) {
        this.maintenanceMapper = maintenanceMapper;
    }

    @Scheduled(
            initialDelayString = "${pacs.maintenance.daily-stats.initial-delay-ms:120000}",
            fixedDelayString = "${pacs.maintenance.daily-stats.fixed-delay-ms:600000}"
    )
    public void refreshTodayStats() {
        if (!dailyStatsEnabled) {
            return;
        }
        try {
            maintenanceMapper.refreshDailyStats(LocalDate.now(), null);
        } catch (Exception ex) {
            LOGGER.warn("pacs_daily_stats refresh for today failed: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "${pacs.maintenance.daily-stats.nightly-cron:0 15 0 * * *}")
    public void refreshYesterdayStats() {
        if (!dailyStatsEnabled) {
            return;
        }
        try {
            maintenanceMapper.refreshDailyStats(LocalDate.now().minusDays(1), null);
        } catch (Exception ex) {
            LOGGER.warn("pacs_daily_stats refresh for yesterday failed: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "${pacs.maintenance.partitions.monthly-cron:0 0 2 1 * *}")
    @Transactional
    public void runMonthlyPartitionMaintenance() {
        if (!partitionsEnabled) {
            return;
        }
        boolean locked = false;
        try {
            locked = Boolean.TRUE.equals(maintenanceMapper.tryPartitionMaintenanceLock());
            if (!locked) {
                LOGGER.info("partition maintenance skipped: another API instance holds the advisory lock");
                return;
            }
            LOGGER.info("partition maintenance started");
            String summary = maintenanceMapper.runPartitionMaintenance();
            LOGGER.info("partition maintenance finished: {}", summary);
        } catch (Exception ex) {
            LOGGER.warn("partition maintenance failed: {}", ex.getMessage(), ex);
        } finally {
            if (locked) {
                try {
                    maintenanceMapper.releasePartitionMaintenanceLock();
                } catch (Exception ex) {
                    LOGGER.warn("partition maintenance lock release failed: {}", ex.getMessage(), ex);
                }
            }
        }
    }

    @Scheduled(cron = "${pacs.maintenance.week-cache.weekly-cron:0 0 2 * * MON}")
    @Transactional
    public void refreshWeeklyPacsCache() {
        if (!weekCacheEnabled) {
            return;
        }
        runWeekCacheJob("weekly PACS cache refresh", maintenanceMapper::refreshPacsWeekCache);
    }

    @Scheduled(cron = "${pacs.maintenance.week-cache.cleanup-cron:0 30 2 * * *}")
    @Transactional
    public void cleanupWeeklyPacsCache() {
        if (!weekCacheEnabled) {
            return;
        }
        runWeekCacheJob("weekly PACS cache cleanup", maintenanceMapper::cleanupPacsWeekCache);
    }

    private void runWeekCacheJob(String jobName, java.util.function.Supplier<String> action) {
        boolean locked = false;
        try {
            locked = Boolean.TRUE.equals(maintenanceMapper.tryWeekCacheRefreshLock());
            if (!locked) {
                LOGGER.info("{} skipped: another API instance holds the advisory lock", jobName);
                return;
            }
            LOGGER.info("{} started", jobName);
            String summary = action.get();
            LOGGER.info("{} finished: {}", jobName, summary);
        } catch (Exception ex) {
            LOGGER.warn("{} failed: {}", jobName, ex.getMessage(), ex);
        } finally {
            if (locked) {
                try {
                    maintenanceMapper.releaseWeekCacheRefreshLock();
                } catch (Exception ex) {
                    LOGGER.warn("{} lock release failed: {}", jobName, ex.getMessage(), ex);
                }
            }
        }
    }
}
