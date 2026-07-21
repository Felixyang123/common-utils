package com.lezai.threadpool;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.enumeration.QueueType;
import com.lezai.threadpool.enumeration.RejectPolicyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
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
    @DisplayName("default queue is ResizableCapacityLinkedBlockingQueue (supports runtime capacity adjustment)")
    void defaultIsResizableQueue() {
        ThreadPoolConfig config = defaultConfig();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        assertInstanceOf(com.lezai.threadpool.core.ResizableCapacityLinkedBlockingQueue.class, pool.getQueue());
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

    @Test
    @DisplayName("updateConfig adjusts queue capacity at runtime via ResizableCapacityLinkedBlockingQueue")
    void updateConfigChangesQueueCapacity() throws Exception {
        ThreadPoolConfig config = defaultConfig();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(config);

        ThreadPoolConfig newConfig = ThreadPoolConfig.builder()
                .poolName("test-pool")
                .corePoolSize(1).maximumPoolSize(2)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS)
                .queueCapacity(20)
                .build();

        pool.updateConfig(newConfig);

        assertEquals(20, pool.getQueueRemainingCapacity(),
                "remaining capacity should reflect the new capacity after resize");
        pool.shutdownNow();
    }

    @Test
    @DisplayName("localDeclaredConfig preserves the original declared config")
    void localDeclaredConfig_preserved() {
        ThreadPoolConfig declared = defaultConfig();
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(declared);

        assertEquals(1, pool.getLocalDeclaredConfig().getCorePoolSize());
        assertSame(declared, pool.getLocalDeclaredConfig(),
                "localDeclaredConfig should be the exact config instance passed at construction");
    }

    @Test
    @DisplayName("execute 异常路径：error ⊂ completed（wrap 统一计数）")
    void execute_errorCountedInWrap() throws Exception {
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(defaultConfig());

        CountDownLatch latch = new CountDownLatch(1);
        // 抛出异常的任务：同时计入 error 与 completed
        pool.execute(() -> {
            latch.countDown();
            throw new RuntimeException("boom");
        });
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        assertEquals(1, pool.getSubmittedTaskCount(), "submitted 计入");
        assertEquals(1, pool.getCompletedTaskCount(), "异常任务也计入 completed");
        assertEquals(1, pool.getErrorTaskCount(), "异常任务计入 error（error ⊂ completed）");

        pool.shutdownNow();
    }

    @Test
    @DisplayName("submit(Callable) 路径也走 wrap 统一计数，异常计入 error")
    void submit_countedErrorViaWrap() throws Exception {
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(defaultConfig());

        CompletableFuture<Object> future = pool.submit(() -> {
            throw new IllegalStateException("task failure");
        });

        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception expected) {
            // 预期异常
        }
        // future 在 completeExceptionally 后即返回，但 completed/error 计数在 worker 线程的 finally/catch 中才写入，需等待其完成
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (pool.getCompletedTaskCount() < 1 && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }

        assertEquals(1, pool.getSubmittedTaskCount(), "submit 经 execute，submitted 计入");
        assertEquals(1, pool.getCompletedTaskCount(), "submit 异常完成计入 completed");
        assertEquals(1, pool.getErrorTaskCount(), "submit 异常计入 error（经 wrap 统一计，不重复）");

        pool.shutdownNow();
    }

    @Test
    @DisplayName("revertToLocalConfig restores declared config after server-side update (server unmanage)")
    void revertToLocalConfig_restoresDeclaredValue() {
        // declared value: corePoolSize=1
        DynamicThreadPoolWrapper pool = new DynamicThreadPoolWrapper(defaultConfig());

        // simulate server-side tuning: corePoolSize=8
        ThreadPoolConfig serverTuned = ThreadPoolConfig.builder()
                .poolName("test-pool")
                .corePoolSize(8).maximumPoolSize(16)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS)
                .queueCapacity(10)
                .build();
        pool.updateConfig(serverTuned);
        assertEquals(8, pool.getCorePoolSize(), "precondition: server-tuned value applied");

        // server unmanage (tombstone) -> revert to local declared value
        pool.revertToLocalConfig();
        assertEquals(1, pool.getCorePoolSize(),
                "revertToLocalConfig should restore the declared corePoolSize=1, not the server-tuned value");
    }
}
