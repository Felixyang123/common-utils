package com.lezai.threadpool.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorUtilsTest {

    @Test
    @DisplayName("shutdown with null executor does not throw")
    void shutdown_null() {
        ExecutorUtils.shutdown(null, "test-executor");
    }

    @Test
    @DisplayName("shutdown terminates executor gracefully")
    void shutdown_terminates() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ExecutorUtils.shutdown(executor, "test-executor");

        assertThat(executor.isShutdown()).isTrue();
        assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    @DisplayName("shutdownNow on hung executor")
    void shutdown_hung() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean interrupted = new AtomicBoolean(false);
        executor.submit(() -> {
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        ExecutorUtils.shutdown(executor, "hung-executor");

        assertThat(executor.isShutdown()).isTrue();
    }
}
