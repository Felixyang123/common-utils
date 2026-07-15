package com.lezai.threadpool.service;

import com.lezai.threadpool.util.SyncLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAggregationService {

    private static final String LOCK_NAME = "stats";
    private static final String LOCK_KEY = "aggregation";

    private final SyncLock syncLock;
    private final ThreadPoolStatsPersistenceService statsPersistenceService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void aggregateAndClean() {
        boolean locked = false;
        try {
            locked = syncLock.tryLock(LOCK_NAME, LOCK_KEY, 0, 120, TimeUnit.SECONDS);
            if (!locked) {
                log.info("Skip stats aggregation because another node holds the lock");
                return;
            }
            int aggregated = statsPersistenceService.aggregateYesterdayStats();
            int rawDeleted = statsPersistenceService.cleanRawStatsBefore(LocalDateTime.now().minusDays(7));
            int dailyDeleted = statsPersistenceService.cleanDailyStatsBefore(LocalDate.now().minusDays(365));
            log.info("Stats aggregation finished, aggregated={}, rawDeleted={}, dailyDeleted={}", aggregated, rawDeleted, dailyDeleted);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Stats aggregation interrupted", e);
        } catch (Exception e) {
            log.error("Stats aggregation failed", e);
        } finally {
            if (locked) {
                syncLock.unlock(LOCK_NAME, LOCK_KEY);
            }
        }
    }
}
