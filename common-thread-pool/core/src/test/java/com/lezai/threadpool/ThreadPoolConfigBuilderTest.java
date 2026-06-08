package com.lezai.threadpool;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.enumeration.QueueType;
import com.lezai.threadpool.enumeration.RejectPolicyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED — tests that should fail before the builder fix.
 */
@DisplayName("ThreadPoolConfig Builder")
class ThreadPoolConfigBuilderTest {

    @Test
    @DisplayName("builder provides sensible defaults")
    void builderDefaults() {
        ThreadPoolConfig config = ThreadPoolConfig.builder().poolName("test").build();

        assertNotNull(config.getPoolName());
        assertEquals("test", config.getPoolName());
        assertTrue(config.getCorePoolSize() > 0);
        assertTrue(config.getMaximumPoolSize() > 0);
        assertEquals(60L, config.getKeepAliveTime());
        assertEquals(TimeUnit.SECONDS, config.getTimeUnit());
        assertEquals(QueueType.BLOCKING_QUEUE, config.getQueueType());
        assertEquals(1024, config.getQueueCapacity());
        assertEquals(RejectPolicyType.ABORT, config.getRejectPolicyType());
        assertFalse(config.isAllowCoreThreadTimeout());
        assertFalse(config.isDaemon());
    }

    @Test
    @DisplayName("threadNamePrefix falls back to poolName when not set")
    void threadNamePrefixDefaultsToPoolName() {
        ThreadPoolConfig config = ThreadPoolConfig.builder().poolName("my-pool").build();
        assertEquals("my-pool", config.getThreadNamePrefix());
    }

    @Test
    @DisplayName("custom threadNamePrefix overrides poolName")
    void customThreadNamePrefix() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .poolName("my-pool")
                .threadNamePrefix("worker")
                .build();
        assertEquals("worker", config.getThreadNamePrefix());
    }

    @Test
    @DisplayName("maximumPoolSize is recalculated when corePoolSize is customized")
    void maximumPoolSizeFollowsCorePoolSize() {
        // When corePoolSize=4, maxPoolSize should be 4*2=8, NOT the default (availableProcessors*2)
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .poolName("test")
                .corePoolSize(4)
                .build();

        assertEquals(4, config.getCorePoolSize());
        assertEquals(8, config.getMaximumPoolSize(),
                "maximumPoolSize should be corePoolSize * 2 when only corePoolSize is customized");
    }

    @Test
    @DisplayName("explicit maximumPoolSize overrides automatic calculation")
    void explicitMaximumPoolSizeWins() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .poolName("test")
                .corePoolSize(4)
                .maximumPoolSize(10)
                .build();

        assertEquals(4, config.getCorePoolSize());
        assertEquals(10, config.getMaximumPoolSize());
    }

    @Test
    @DisplayName("builder chain sets all values correctly")
    void builderChain() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .poolName("worker-pool")
                .corePoolSize(2)
                .maximumPoolSize(8)
                .keepAliveTime(30L)
                .timeUnit(TimeUnit.MILLISECONDS)
                .queueType(QueueType.ARRAY_BLOCKING_QUEUE)
                .queueCapacity(500)
                .rejectPolicyType(RejectPolicyType.CALLER_RUNS)
                .allowCoreThreadTimeout(true)
                .threadNamePrefix("wkr")
                .daemon(true)
                .build();

        assertEquals("worker-pool", config.getPoolName());
        assertEquals(2, config.getCorePoolSize());
        assertEquals(8, config.getMaximumPoolSize());
        assertEquals(30L, config.getKeepAliveTime());
        assertEquals(TimeUnit.MILLISECONDS, config.getTimeUnit());
        assertEquals(QueueType.ARRAY_BLOCKING_QUEUE, config.getQueueType());
        assertEquals(500, config.getQueueCapacity());
        assertEquals(RejectPolicyType.CALLER_RUNS, config.getRejectPolicyType());
        assertTrue(config.isAllowCoreThreadTimeout());
        assertEquals("wkr", config.getThreadNamePrefix());
        assertTrue(config.isDaemon());
    }
}
