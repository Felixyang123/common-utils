package com.lezai.threadpool.config;

import com.lezai.threadpool.client.ConfigPollingService;
import com.lezai.threadpool.client.ThreadPoolStatsReporter;
import com.lezai.threadpool.client.router.CircuitBreakerScheduler;
import com.lezai.threadpool.client.router.HealthChecker;
import com.lezai.threadpool.init.ThreadPoolInitializer;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

/**
 * SmartLifecycle orchestrator for thread pool components.
 * Ensures correct start order (initializer -> reporter) and
 * stop order (pollingService -> reporter -> healthChecker -> pools last).
 */
@Slf4j
public class ThreadPoolLifecycle implements SmartLifecycle {

    private final ConfigPollingService pollingService;
    private final ThreadPoolStatsReporter reporter;
    private final ThreadPoolInitializer initializer;
    private final ThreadPoolManager threadPoolManager;
    private final HealthChecker healthChecker;

    private volatile boolean running = false;

    public ThreadPoolLifecycle(ConfigPollingService pollingService,
                               ThreadPoolStatsReporter reporter,
                               ThreadPoolInitializer initializer,
                               ThreadPoolManager threadPoolManager,
                               HealthChecker healthChecker) {
        this.pollingService = pollingService;
        this.reporter = reporter;
        this.initializer = initializer;
        this.threadPoolManager = threadPoolManager;
        this.healthChecker = healthChecker;
    }

    @Override
    public void start() {
        if (running) return;
        log.info("Starting ThreadPoolLifecycle");
        initializer.initialize();
        if (reporter != null) reporter.start();
        if (healthChecker != null) healthChecker.start();
        log.info("ThreadPoolLifecycle started");
        running = true;
    }

    @Override
    public void stop() {
        if (!running) return;
        log.info("Stopping ThreadPoolLifecycle");
        if (pollingService != null) pollingService.stop();
        if (reporter != null) reporter.stop();
        if (healthChecker != null) healthChecker.stop();
        threadPoolManager.shutdown();
        CircuitBreakerScheduler.shutdown();
        log.info("ThreadPoolLifecycle stopped");
        running = false;
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public int getPhase() { return Integer.MIN_VALUE; }
}
