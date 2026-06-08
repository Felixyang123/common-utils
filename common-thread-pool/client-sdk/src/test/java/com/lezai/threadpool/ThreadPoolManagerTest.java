package com.lezai.threadpool;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.manager.ThreadPoolManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED — tests for ThreadPoolManager.
 */
@DisplayName("ThreadPoolManager")
class ThreadPoolManagerTest {

    private ThreadPoolManager manager;

    @BeforeEach
    void setUp() {
        manager = new ThreadPoolManager();
    }

    @AfterEach
    void tearDown() {
        manager.shutdownNow();
    }

    private ThreadPoolConfig config(String name) {
        return ThreadPoolConfig.builder()
                .poolName(name)
                .corePoolSize(1)
                .maximumPoolSize(1)
                .keepAliveTime(1)
                .timeUnit(TimeUnit.SECONDS)
                .queueCapacity(10)
                .build();
    }

    @Test
    @DisplayName("registerPool creates and returns a new pool")
    void registerPoolCreatesNew() {
        DynamicThreadPoolWrapper pool = manager.registerPool(config("my-pool"));
        assertNotNull(pool);
        assertEquals("my-pool", pool.getPoolName());
        assertFalse(pool.isShutdown());
    }

    @Test
    @DisplayName("registerPool returns same instance for duplicate registration")
    void registerPoolIsIdempotent() {
        DynamicThreadPoolWrapper first = manager.registerPool(config("dup-pool"));
        DynamicThreadPoolWrapper second = manager.registerPool(config("dup-pool"));

        assertSame(first, second, "should return same pool instance for same name");
    }

    @Test
    @DisplayName("getPool returns existing registered pool")
    void getPoolReturnsExisting() {
        manager.registerPool(config("existing"));
        DynamicThreadPoolWrapper pool = manager.getPool("existing");

        assertNotNull(pool);
        assertEquals("existing", pool.getPoolName());
    }

    @Test
    @DisplayName("getPool creates default pool when not registered")
    void getPoolCreatesDefault() {
        DynamicThreadPoolWrapper pool = manager.getPool("auto-created");
        assertNotNull(pool);
        assertEquals("auto-created", pool.getPoolName());
        assertFalse(pool.isShutdown());
    }

    @Test
    @DisplayName("removePool removes and shuts down the pool")
    void removePoolRemovesPool() throws Exception {
        DynamicThreadPoolWrapper pool = manager.registerPool(config("to-remove"));
        assertNotNull(manager.getPool("to-remove"));

        boolean removed = manager.removePool("to-remove");

        assertTrue(removed, "removePool should return true");
        // After removal, getPool recreates a new one — verify it's a different instance
        DynamicThreadPoolWrapper recreated = manager.getPool("to-remove");
        assertNotSame(pool, recreated, "should be a new instance after removal");
    }

    @Test
    @DisplayName("removePool returns false for non-existent pool")
    void removePoolReturnsFalseForMissing() {
        boolean removed = manager.removePool("does-not-exist");
        assertFalse(removed);
    }

    @Test
    @DisplayName("updatePool updates existing pool configuration")
    void updatePoolUpdatesExisting() {
        manager.registerPool(config("updatable"));

        ThreadPoolConfig updatedConfig = ThreadPoolConfig.builder()
                .poolName("updatable")
                .corePoolSize(2)
                .maximumPoolSize(4)
                .keepAliveTime(5)
                .timeUnit(TimeUnit.SECONDS)
                .queueCapacity(10)
                .build();

        manager.updatePool(updatedConfig);

        DynamicThreadPoolWrapper pool = manager.getPool("updatable");
        assertEquals(2, pool.getCorePoolSize());
        assertEquals(4, pool.getMaximumPoolSize());
        assertEquals(5, pool.getKeepAliveTime(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("updatePool creates pool when it doesn't exist")
    void updatePoolCreatesWhenMissing() {
        ThreadPoolConfig config = config("auto-create-on-update");
        manager.updatePool(config);

        DynamicThreadPoolWrapper pool = manager.getPool("auto-create-on-update");
        assertNotNull(pool);
        assertEquals("auto-create-on-update", pool.getPoolName());
    }

    @Test
    @DisplayName("getAllPoolStats returns stats for all registered pools")
    void getAllPoolStats() {
        manager.registerPool(config("stats-a"));
        manager.registerPool(config("stats-b"));

        List<ThreadPoolStats> stats = manager.getAllPoolStats();
        assertEquals(2, stats.size());
        List<String> names = stats.stream().map(ThreadPoolStats::getPoolName).toList();
        assertTrue(names.contains("stats-a"));
        assertTrue(names.contains("stats-b"));
    }

    @Test
    @DisplayName("getAllPoolStats returns empty list when no pools")
    void getAllPoolStatsEmpty() {
        List<ThreadPoolStats> stats = manager.getAllPoolStats();
        assertTrue(stats.isEmpty());
    }

    @Test
    @DisplayName("removeAllPools removes all pools")
    void removeAllPools() {
        manager.registerPool(config("pool-a"));
        manager.registerPool(config("pool-b"));

        manager.removeAllPools();

        // After removeAll, getPool auto-creates - verify instance is new
        DynamicThreadPoolWrapper pa = manager.getPool("pool-a");
        assertNotNull(pa);
        assertFalse(pa.isShutdown(), "auto-created pool should not be shut down");
    }

    @Test
    @DisplayName("shutdown shuts down all pools gracefully")
    void shutdownAll() throws Exception {
        manager.registerPool(config("shutdown-a"));
        manager.registerPool(config("shutdown-b"));

        manager.shutdown();

        // After shutdown, getPool auto-creates new instances
        DynamicThreadPoolWrapper pa = manager.getPool("shutdown-a");
        assertNotNull(pa);
    }

    @Test
    @DisplayName("registerPools registers multiple pools at once")
    void registerPoolsBatch() {
        List<ThreadPoolConfig> configs = List.of(config("batch-a"), config("batch-b"), config("batch-c"));
        manager.registerPools(configs);

        assertEquals(3, manager.getAllPoolStats().size());
        assertNotNull(manager.getPool("batch-a"));
        assertNotNull(manager.getPool("batch-b"));
        assertNotNull(manager.getPool("batch-c"));
    }

    @Test
    @DisplayName("getPoolStats returns stats for a named pool")
    void getPoolStats() {
        manager.registerPool(config("stats-test"));

        ThreadPoolStats stats = manager.getPoolStats("stats-test");
        assertEquals("stats-test", stats.getPoolName());
    }
}
