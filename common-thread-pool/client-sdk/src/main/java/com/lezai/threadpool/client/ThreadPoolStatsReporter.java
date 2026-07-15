package com.lezai.threadpool.client;

import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@Slf4j
public class ThreadPoolStatsReporter {

    private final ConfigOperations configOperations;
    private final String appId;
    private final long reportIntervalMs;
    private final long degradedReportIntervalMs;
    private final ThreadPoolManager threadPoolManager;
    private final ScheduledExecutorService reportExecutor;
    private final BooleanSupplier degradedSupplier;
    private volatile boolean running = false;

    public ThreadPoolStatsReporter(ConfigOperations configOperations, String appId,
                                   long reportIntervalMs, long degradedReportIntervalMs,
                                   ThreadPoolManager threadPoolManager, BooleanSupplier degradedSupplier) {
        this.configOperations = configOperations;
        this.appId = appId;
        this.reportIntervalMs = reportIntervalMs;
        this.degradedReportIntervalMs = degradedReportIntervalMs;
        this.threadPoolManager = threadPoolManager;
        this.degradedSupplier = degradedSupplier;
        this.reportExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stats-reporter-thread");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!running) {
            running = true;
            scheduleNext(0);
        }
    }

    private void scheduleNext(long delayMs) {
        if (running) {
            reportExecutor.schedule(this::reportStatsAndReschedule, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        }
    }

    private void reportStatsAndReschedule() {
        try {
            reportStats();
        } finally {
            long interval = degradedSupplier.getAsBoolean() ? degradedReportIntervalMs : reportIntervalMs;
            scheduleNext(interval);
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        log.info("Stopping ThreadPoolStatsReporter for appId: {}", appId);
        reportExecutor.shutdown();
        try {
            if (!reportExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                reportExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            reportExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("ThreadPoolStatsReporter stopped for appId: {}", appId);
    }

    private void reportStats() {
        try {
            List<ThreadPoolStats> statsList = threadPoolManager.getAllPoolStats();
            if (statsList.isEmpty()) {
                log.debug("No thread pools to report for appId: {}", appId);
                return;
            }

            ThreadPoolStatsReport report = ThreadPoolStatsReport.builder()
                    .appId(appId)
                    .reportTime(System.currentTimeMillis())
                    .statsList(statsList)
                    .build();

            configOperations.reportStats(report);
            log.debug("Successfully reported {} thread pool stats for appId: {}", statsList.size(), appId);
        } catch (Exception e) {
            log.error("Error reporting thread pool stats for appId: {}", appId, e);
        }
    }
}