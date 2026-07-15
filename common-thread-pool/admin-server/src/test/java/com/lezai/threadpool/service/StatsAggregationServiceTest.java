package com.lezai.threadpool.service;

import com.lezai.threadpool.util.SyncLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsAggregationServiceTest {

    @Mock private SyncLock syncLock;
    @Mock private ThreadPoolStatsPersistenceService statsPersistenceService;
    private StatsAggregationService service;

    @BeforeEach
    void setUp() {
        service = new StatsAggregationService(syncLock, statsPersistenceService);
    }

    @Test
    @DisplayName("skips when lock not acquired")
    void skipWhenLockNotAcquired() throws Exception {
        when(syncLock.tryLock("stats", "aggregation", 0, 120, TimeUnit.SECONDS)).thenReturn(false);

        service.aggregateAndClean();

        verify(statsPersistenceService, never()).aggregateYesterdayStats();
        verify(syncLock, never()).unlock("stats", "aggregation");
    }

    @Test
    @DisplayName("aggregates and cleans when lock acquired")
    void aggregateAndCleanWhenLocked() throws Exception {
        when(syncLock.tryLock("stats", "aggregation", 0, 120, TimeUnit.SECONDS)).thenReturn(true);

        service.aggregateAndClean();

        verify(statsPersistenceService).aggregateYesterdayStats();
        verify(statsPersistenceService).cleanRawStatsBefore(any());
        verify(statsPersistenceService).cleanDailyStatsBefore(any());
        verify(syncLock).unlock("stats", "aggregation");
    }
}
