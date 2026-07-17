package com.lezai.threadpool.client.router;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class CircuitBreakerScheduler {

    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "circuit-breaker-cooldown");
                t.setDaemon(true);
                return t;
            });

    private CircuitBreakerScheduler() {
    }

    public static ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return EXECUTOR.schedule(task, delay, unit);
    }

    /**
     * Shuts down the shared scheduler. Called from {@link com.lezai.threadpool.config.ThreadPoolLifecycle}
     * on context shutdown. Safe to call multiple times.
     */
    public static synchronized void shutdown() {
        if (!EXECUTOR.isShutdown()) {
            EXECUTOR.shutdown();
        }
    }
}
