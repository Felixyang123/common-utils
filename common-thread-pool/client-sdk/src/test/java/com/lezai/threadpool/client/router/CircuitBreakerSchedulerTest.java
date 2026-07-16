package com.lezai.threadpool.client.router;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerSchedulerTest {

    @Test
    void runsTaskAfterDelay() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        long start = System.currentTimeMillis();
        CircuitBreakerScheduler.schedule(latch::countDown, 50, TimeUnit.MILLISECONDS);
        boolean fired = latch.await(1, TimeUnit.SECONDS);
        assertThat(fired).isTrue();
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(40);
    }

    @Test
    void singleThreadSharedAcrossAllTasks() throws Exception {
        Set<String> threads = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch latch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            CircuitBreakerScheduler.schedule(() -> {
                threads.add(Thread.currentThread().getName());
                latch.countDown();
            }, 10, TimeUnit.MILLISECONDS);
        }
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(threads).hasSize(1);
    }
}
