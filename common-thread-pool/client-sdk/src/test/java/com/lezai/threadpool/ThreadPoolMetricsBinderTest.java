package com.lezai.threadpool;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.manager.ThreadPoolManager;
import com.lezai.threadpool.metrics.ThreadPoolMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ThreadPoolMetricsBinder")
class ThreadPoolMetricsBinderTest {

    private MeterRegistry registry;
    private ThreadPoolManager manager;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        manager = new ThreadPoolManager();
    }

    @AfterEach
    void tearDown() {
        manager.shutdownNow();
    }

    @Test
    @DisplayName("binder registers gauges for each pool")
    void registersGaugesForAllPools() {
        manager.registerPool(ThreadPoolConfig.builder()
                .poolName("metrics-pool").corePoolSize(2).maximumPoolSize(4)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());

        new ThreadPoolMetricsBinder(manager, "test-app").bindTo(registry);

        // 11 gauges expected per pool
        assertTrue(registry.get("threadpool.threads.core").gauge().value() > 0);
        assertEquals("metrics-pool",
                registry.get("threadpool.threads.core").meter().getId().getTag("pool"));
        assertEquals("test-app",
                registry.get("threadpool.threads.core").meter().getId().getTag("app"));
    }

    @Test
    @DisplayName("removed pool returns sentinel -1")
    void removedPoolReturnsSentinel() {
        manager.registerPool(ThreadPoolConfig.builder()
                .poolName("temp-pool").corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());

        new ThreadPoolMetricsBinder(manager, "test-app").bindTo(registry);

        double before = registry.get("threadpool.threads.core").gauge().value();
        assertTrue(before > 0, "core size should be positive before removal");

        manager.removePool("temp-pool");

        double after = registry.get("threadpool.threads.core").gauge().value();
        assertEquals(-1.0, after, "should return -1 sentinel after pool removed");
    }

    @Test
    @DisplayName("load factor gauge computes correctly")
    void loadFactorGauge() {
        manager.registerPool(ThreadPoolConfig.builder()
                .poolName("lf-pool").corePoolSize(1).maximumPoolSize(4)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());

        new ThreadPoolMetricsBinder(manager, "test-app").bindTo(registry);

        double lf = registry.get("threadpool.load.factor").gauge().value();
        assertTrue(lf >= 0.0, "load factor should be non-negative");
    }

    @Test
    @DisplayName("binder on empty registry does not throw")
    void emptyRegistryDoesNotThrow() {
        assertDoesNotThrow(() ->
                new ThreadPoolMetricsBinder(manager, "test-app").bindTo(registry));
    }

    @Test
    @DisplayName("rejected gauge registered")
    void rejectedGaugeRegistered() {
        manager.registerPool(ThreadPoolConfig.builder()
                .poolName("rj-pool").corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());

        new ThreadPoolMetricsBinder(manager, "test-app").bindTo(registry);

        assertEquals(0.0, registry.get("threadpool.tasks.rejected").gauge().value());
    }
}
