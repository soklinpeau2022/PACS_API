package com.ut.emrPacs.scheduler;

import com.ut.emrPacs.mapper.pacs.PacsMaintenanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacsLargeScaleMaintenanceSchedulerTest {

    @Mock
    private PacsMaintenanceMapper maintenanceMapper;

    private PacsLargeScaleMaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PacsLargeScaleMaintenanceScheduler(maintenanceMapper);
        ReflectionTestUtils.setField(scheduler, "partitionsEnabled", true);
        ReflectionTestUtils.setField(scheduler, "weekCacheEnabled", true);
    }

    @Test
    void weeklyRefreshShouldUseAdvisoryLockAndReleaseIt() {
        when(maintenanceMapper.tryWeekCacheRefreshLock()).thenReturn(true);
        when(maintenanceMapper.refreshPacsWeekCache()).thenReturn("refreshed");

        scheduler.refreshWeeklyPacsCache();

        verify(maintenanceMapper).tryWeekCacheRefreshLock();
        verify(maintenanceMapper).refreshPacsWeekCache();
        verify(maintenanceMapper).releaseWeekCacheRefreshLock();
    }

    @Test
    void weeklyRefreshShouldSkipWhenAnotherInstanceHasTheLock() {
        when(maintenanceMapper.tryWeekCacheRefreshLock()).thenReturn(false);

        scheduler.refreshWeeklyPacsCache();

        verify(maintenanceMapper, never()).refreshPacsWeekCache();
        verify(maintenanceMapper, never()).releaseWeekCacheRefreshLock();
    }

    @Test
    void weeklyRefreshFailureShouldNotEscapeAndShouldReleaseTheLock() {
        when(maintenanceMapper.tryWeekCacheRefreshLock()).thenReturn(true);
        when(maintenanceMapper.refreshPacsWeekCache()).thenThrow(new IllegalStateException("refresh failed"));

        assertDoesNotThrow(scheduler::refreshWeeklyPacsCache);

        verify(maintenanceMapper).releaseWeekCacheRefreshLock();
    }

    @Test
    void dailyCleanupShouldUseTheSameAdvisoryLock() {
        when(maintenanceMapper.tryWeekCacheRefreshLock()).thenReturn(true);
        when(maintenanceMapper.cleanupPacsWeekCache()).thenReturn("cleaned");

        scheduler.cleanupWeeklyPacsCache();

        verify(maintenanceMapper).cleanupPacsWeekCache();
        verify(maintenanceMapper).releaseWeekCacheRefreshLock();
    }

    @Test
    void partitionMaintenanceShouldUseAdvisoryLockAndReleaseIt() {
        when(maintenanceMapper.tryPartitionMaintenanceLock()).thenReturn(true);
        when(maintenanceMapper.runPartitionMaintenance()).thenReturn("maintained");

        scheduler.runMonthlyPartitionMaintenance();

        verify(maintenanceMapper).runPartitionMaintenance();
        verify(maintenanceMapper).releasePartitionMaintenanceLock();
    }

    @Test
    void partitionFailureShouldNotEscapeAndShouldReleaseTheLock() {
        when(maintenanceMapper.tryPartitionMaintenanceLock()).thenReturn(true);
        when(maintenanceMapper.runPartitionMaintenance()).thenThrow(new IllegalStateException("maintenance failed"));

        assertDoesNotThrow(scheduler::runMonthlyPartitionMaintenance);

        verify(maintenanceMapper).releasePartitionMaintenanceLock();
    }
}
