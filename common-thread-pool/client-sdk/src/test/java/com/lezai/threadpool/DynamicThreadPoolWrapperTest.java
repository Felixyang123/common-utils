package com.lezai.threadpool;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.enumeration.QueueType;
import com.lezai.threadpool.enumeration.RejectPolicyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED — tests for DynamicThreadPoolWrapper.
 */
@DisplayName("DynamicThreadPoolWrapper")
class DynamicThreadPoolWrapperTest {

    private ThreadPoolConfig defaultConfig() {
        return ThreadPoolConfig.builder()
                .poolName("test-pool")
                .corePoolSize(1)
                .maximumPoolSize(2)
                .keepAliveTime(1)
                .timeUnit(TimeUnit.SECONDS)
                .queueCapacity(10)
                .build();
    }

    @Test
    @DisplayName("creates pool with correct initial parameters")
    void createsPoolWithCorrectParams() {
        ThreadPoolConfig config = defaultConfig();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        assertEquals("test-pool", pool.getPoolName());
        assertEquals(1, pool.getCorePoolSize());
        assertEquals(2, pool.getMaximumPoolSize());
        assertEquals(1, pool.getKeepAliveTime(TimeUnit.SECONDS));
        assertFalse(pool.isShutdown());
    }

    @Test
    @DisplayName("creates pool with ARRAY_BLOCKING_QUEUE")
    void createsPoolWithArrayBlockingQueue() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .poolName("array-queue")
                .corePoolSize(1)
                .maximumPoolSize(1)
                .queueType(QueueType.ARRAY_BLOCKING_QUEUE)
                .queueCapacity(5)
                .build();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        assertInstanceOf(java.util.concurrent.ArrayBlockingQueue.class, pool.getQueue());
        assertEquals(5, pool.getQueue().remainingCapacity());
        pool.shutdownNow();
    }

    @Test
    @DisplayName("creates pool with SYNCHRONOUS_QUEUE")
    void createsPoolWithSynchronousQueue() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .poolName("sync-queue")
                .corePoolSize(1)
                .maximumPoolSize(2)
                .queueType(QueueType.SYNCHRONOUS_QUEUE)
                .build();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        assertInstanceOf(java.util.concurrent.SynchronousQueue.class, pool.getQueue());
        pool.shutdownNow();
    }

    @Test
    @DisplayName("creates pool with PRIORITY_BLOCKING_QUEUE")
    void createsPoolWithPriorityBlockingQueue() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .poolName("priority-queue")
                .corePoolSize(1)
                .maximumPoolSize(1)
                .queueType(QueueType.PRIORITY_BLOCKING_QUEUE)
                .queueCapacity(5)
                .build();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        assertInstanceOf(java.util.concurrent.PriorityBlockingQueue.class, pool.getQueue());
        pool.shutdownNow();
    }

    @Test
    @DisplayName("default queue is LinkedBlockingQueue")
    void defaultIsLinkedBlockingQueue() {
        ThreadPoolConfig config = defaultConfig();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        assertInstanceOf(java.util.concurrent.LinkedBlockingQueue.class, pool.getQueue());
        pool.shutdownNow();
    }

    @Test
    @DisplayName("updateConfig changes core pool size")
    void updateConfigChangesCorePoolSize() throws Exception {
        ThreadPoolConfig config = defaultConfig();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        ThreadPoolConfig newConfig = ThreadPoolConfig.builder()
                .poolName("test-pool")
                .corePoolSize(5)
                .maximumPoolSize(10)
                .keepAliveTime(1)
                .timeUnit(TimeUnit.SECONDS)
                .queueCapacity(10)
                .build();

        pool.updateConfig(newConfig);

        assertEquals(5, pool.getCorePoolSize());
        assertEquals(10, pool.getMaximumPoolSize());
        pool.shutdownNow();
    }

    @Test
    @DisplayName("updateConfig changes keep alive time")
    void updateConfigChangesKeepAliveTime() throws Exception {
        ThreadPoolConfig config = defaultConfig();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        ThreadPoolConfig newConfig = ThreadPoolConfig.builder()
                .poolName("test-pool")
                .corePoolSize(1)
                .maximumPoolSize(2)
                .keepAliveTime(5)
                .timeUnit(TimeUnit.SECONDS)
                .queueCapacity(10)
                .build();

        pool.updateConfig(newConfig);

        assertEquals(5, pool.getKeepAliveTime(TimeUnit.SECONDS));
        pool.shutdownNow();
    }

    @Test
    @DisplayName("getStats returns correct values after task execution")
    void getStatsAfterTaskExecution() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .poolName("stats-pool")
                .corePoolSize(1)
                .maximumPoolSize(2)
                .keepAliveTime(1)
                .timeUnit(TimeUnit.SECONDS)
                .queueCapacity(10)
                .build();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        CountDownLatch latch = new CountDownLatch(1);
        pool.execute(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
            latch.countDown();
        });
        latch.await(5, TimeUnit.SECONDS);

        // Give afterExecute time to fire
        Thread.sleep(100);

        ThreadPoolStats stats = pool.getStats();

        assertEquals("stats-pool", stats.getPoolName());
        assertEquals(1, stats.getCorePoolSize());
        assertEquals(2, stats.getMaximumPoolSize());
        assertTrue(stats.getCompletedTaskCount() >= 1,
                "completedTaskCount should be >= 1, got " + stats.getCompletedTaskCount());
        assertTrue(stats.getSubmittedTaskCount() >= 1,
                "submittedTaskCount should be >= 1, got " + stats.getSubmittedTaskCount());

        pool.shutdownNow();
    }

    @Test
    @DisplayName("getCurrentConfig returns latest config")
    void getCurrentConfig() {
        ThreadPoolConfig config = defaultConfig();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        ThreadPoolConfig current = pool.getCurrentConfig();
        assertEquals("test-pool", current.getPoolName());
        assertEquals(1, current.getCorePoolSize());
    }

    @Test
    @DisplayName("shutdown prevents new tasks")
    void shutdownPreventsNewTasks() throws Exception {
        ThreadPoolConfig config = defaultConfig();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        pool.shutdown();
        assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        assertTrue(pool.isShutdown());
        assertTrue(pool.isTerminated());
    }
}
