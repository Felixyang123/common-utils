package com.lezai.threadpool.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ExecutorUtils {

    public static void shutdown(ExecutorService executor, String name) {
        log.info("Shutting down {} executor...", name);

        if (executor == null) {
            log.warn("{} executor is null, skip shutdown", name);
            return;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("{} executor did not terminate in time, forcing shutdown", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for {} executor to terminate", name);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("{} executor shutdown complete", name);
    }

}
