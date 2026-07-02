package com.lezai.threadpool.health;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.manager.ThreadPoolManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ThreadPoolHealthIndicator")
class ThreadPoolHealthIndicatorTest {

    private ThreadPoolManager manager;

    @BeforeEach
    void setUp() {
        manager = new ThreadPoolManager();
    }

    @AfterEach
    void tearDown() {
        manager.shutdownNow();
    }

    private ThreadPoolConfig config(String name, int core, int max, int queueCapacity) {
        return ThreadPoolConfig.builder()
                .poolName(name).corePoolSize(core).maximumPoolSize(max)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(queueCapacity).build();
    }

    @Test
    @DisplayName("reports UP when no pools are registered")
    void health_noPools_reportsUp() {
        ThreadPoolHealthIndicator indicator = new ThreadPoolHealthIndicator(manager, 0.9, 0.9);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("reports UP when pools are well within thresholds")
    void health_idlePools_reportsUp() {
        manager.registerPool(config("idle-pool", 2, 4, 10));
        ThreadPoolHealthIndicator indicator = new ThreadPoolHealthIndicator(manager, 0.9, 0.9);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("reports DOWN when active thread ratio exceeds threshold")
    void health_highActiveRatio_reportsDown() throws Exception {
        manager.registerPool(config("busy-pool", 1, 1, 10));
        CountDownLatch block = new CountDownLatch(1);
        manager.getRequiredPool("busy-pool").execute(() -> {
            try { block.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        });
        // give the worker thread a moment to actually start running
        Thread.sleep(100);

        ThreadPoolHealthIndicator indicator = new ThreadPoolHealthIndicator(manager, 0.5, 0.9);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        block.countDown();
    }

    @Test
    @DisplayName("reports DOWN when queue usage ratio exceeds threshold")
    void health_highQueueRatio_reportsDown() throws Exception {
        manager.registerPool(config("full-queue-pool", 1, 1, 2));
        CountDownLatch block = new CountDownLatch(1);
        manager.getRequiredPool("full-queue-pool").execute(() -> {
            try { block.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        });
        manager.getRequiredPool("full-queue-pool").execute(() -> {});
        manager.getRequiredPool("full-queue-pool").execute(() -> {});

        ThreadPoolHealthIndicator indicator = new ThreadPoolHealthIndicator(manager, 0.99, 0.5);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        block.countDown();
    }

    @Test
    @DisplayName("details map includes per-pool ratios")
    void health_details_includePerPoolRatios() {
        manager.registerPool(config("detail-pool", 2, 4, 10));
        ThreadPoolHealthIndicator indicator = new ThreadPoolHealthIndicator(manager, 0.9, 0.9);

        Health health = indicator.health();

        assertThat(health.getDetails()).containsKey("detail-pool");
    }
}
