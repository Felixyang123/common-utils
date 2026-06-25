package com.lezai.threadpool;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.enumeration.RejectPolicyType;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DynamicThreadPoolWrapper counting")
class ThreadPoolErrorCountingTest {

    private DynamicThreadPoolWrapper pool;

    @AfterEach
    void tearDown() {
        if (pool != null) pool.shutdownNow();
    }

    @Test
    @DisplayName("execute increments submittedTaskCount (true submission point)")
    void executeCountsSubmitted() throws Exception {
        pool = new DynamicThreadPoolWrapper(ThreadPoolConfig.builder()
                .poolName("submit-pool").corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());

        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            pool.execute(latch::countDown);
        }
        latch.await(2, TimeUnit.SECONDS);

        assertEquals(3, pool.getSubmittedTaskCount());
    }

    @Test
    @DisplayName("incrementErrorCount increments error counter")
    void incrementErrorCountWorks() {
        pool = new DynamicThreadPoolWrapper(ThreadPoolConfig.builder()
                .poolName("err-pool").corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());

        assertEquals(0, pool.getErrorTaskCount());
        pool.incrementErrorCount();
        pool.incrementErrorCount();
        assertEquals(2, pool.getErrorTaskCount());
    }

    @Test
    @DisplayName("rejected tasks increment rejectedTaskCount")
    void rejectedCounted() throws Exception {
        pool = new DynamicThreadPoolWrapper(ThreadPoolConfig.builder()
                .poolName("reject-pool").corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(1)
                .rejectPolicyType(RejectPolicyType.ABORT).build());

        CountDownLatch block = new CountDownLatch(1);
        pool.execute(() -> {
            try { block.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        });
        pool.execute(() -> {}); // queued
        int rejected = 0;
        for (int i = 0; i < 3; i++) {
            try { pool.execute(() -> {}); } catch (Exception e) { rejected++; }
        }
        block.countDown();

        assertTrue(pool.getRejectedTaskCount() >= 1, "should have counted rejections");
        assertEquals(rejected, pool.getRejectedTaskCount(), "rejected count matches AbortPolicy throws");
    }

    @Test
    @DisplayName("getStats includes rejectedTaskCount")
    void statsHasRejected() {
        pool = new DynamicThreadPoolWrapper(ThreadPoolConfig.builder()
                .poolName("stats-pool").corePoolSize(1).maximumPoolSize(1)
                .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());
        assertEquals(0, pool.getStats().getRejectedTaskCount());
    }
}