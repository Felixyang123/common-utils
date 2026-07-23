package com.lezai.samples.cache.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SingleFlightTest {

    @Test
    void concurrentSameKey_loadsOnce() throws Exception {
        SingleFlight sf = new SingleFlight();
        AtomicInteger loads = new AtomicInteger();
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successes = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        String v = sf.execute("k", 5000, () -> {
                            loads.incrementAndGet();
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                            return "v";
                        });
                        if ("v".equals(v)) successes.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                });
            }
            ready.await();
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(loads.get()).isEqualTo(1);
        assertThat(successes.get()).isEqualTo(threads);
    }

    @Test
    void reentrant_sameThreadSameKey_doesNotDeadlock() {
        SingleFlight sf = new SingleFlight();
        AtomicInteger inner = new AtomicInteger();
        // 外层成为领导者后，内层对同 key 重入应直接执行而非等待自己
        String result = sf.execute("k", 1000, () ->
                sf.execute("k", 1000, () -> {
                    inner.incrementAndGet();
                    return "inner-result";
                }));
        assertThat(result).isEqualTo("inner-result");
        assertThat(inner.get()).isEqualTo(1);
    }

    @Test
    void leaderException_propagatesToFollowers() {
        SingleFlight sf = new SingleFlight();
        assertThatThrownBy(() -> sf.execute("k", 1000, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("boom");
    }
}