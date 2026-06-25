package com.lezai.threadpool.config;

import com.lezai.threadpool.client.RemoteConfigSourceDetector;
import com.lezai.threadpool.client.ThreadPoolStatsReporter;
import com.lezai.threadpool.init.ThreadPoolInitializer;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

/**
 * SmartLifecycle orchestrator for thread pool components.
 * Ensures correct start order (initializer -> reporter) and
 * stop order (detector -> reporter -> pools last).
 */
@Slf4j
public class ThreadPoolLifecycle implements SmartLifecycle {

    private final RemoteConfigSourceDetector detector;
    private final ThreadPoolStatsReporter reporter;
    private final ThreadPoolInitializer initializer;
    private final ThreadPoolManager threadPoolManager;

    private volatile boolean running = false;

    public ThreadPoolLifecycle(RemoteConfigSourceDetector detector,
                               ThreadPoolStatsReporter reporter,
                               ThreadPoolInitializer initializer,
                               ThreadPoolManager threadPoolManager) {
        this.detector = detector;
        this.reporter = reporter;
        this.initializer = initializer;
        this.threadPoolManager = threadPoolManager;
    }

    @Override
    public void start() {
        if (running) return;
        log.info("Starting ThreadPoolLifecycle");

        // 1. Initialize pool configs (includes detector.start() for CS mode)
        initializer.initialize();

        // 2. Start stats reporter (if enabled)
        if (reporter != null) {
            reporter.start();
        }

        log.info("ThreadPoolLifecycle started");
        running = true;
    }

    @Override
    public void stop() {
        if (!running) return;
        log.info("Stopping ThreadPoolLifecycle");

        // 1. Stop remote config detector (stop receiving new configs)
        if (detector != null) {
            detector.stop();
        }

        // 2. Stop stats reporter (stop reporting)
        if (reporter != null) {
            reporter.stop();
        }

        // 3. Shut down thread pools LAST (after in-flight requests drain)
        threadPoolManager.shutdown();

        log.info("ThreadPoolLifecycle stopped");
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 0;
    }
}
