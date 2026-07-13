package com.lezai.threadpool;

import com.lezai.threadpool.pojo.bean.ApiKey;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStats;

import java.time.LocalDateTime;
import java.util.List;

public final class TestDataFactory {

    private TestDataFactory() {}

    public static ApiKey.ApiKeyBuilder defaultApiKey() {
        return ApiKey.builder()
                .appId("test-app")
                .apiKeyHash("fakeHash123")
                .appName("Test Application")
                .enabled(true)
                .createTime(LocalDateTime.of(2025, 1, 1, 0, 0))
                .updateTime(LocalDateTime.of(2025, 1, 1, 0, 0))
                .description("test api key");
    }

    public static ApiKey.ApiKeyBuilder expiredApiKey() {
        return defaultApiKey()
                .appId("expired-app")
                .expireTime(LocalDateTime.of(2020, 1, 1, 0, 0));
    }

    public static ApiKey.ApiKeyBuilder disabledApiKey() {
        return defaultApiKey()
                .appId("disabled-app")
                .enabled(false);
    }

    public static ThreadPoolConfig.ThreadPoolConfigBuilder defaultThreadPoolConfig() {
        return ThreadPoolConfig.builder()
                .poolName("test-pool")
                .corePoolSize(4)
                .maximumPoolSize(8)
                .keepAliveTime(60);
    }

    public static ThreadPoolConfig.ThreadPoolConfigBuilder customThreadPoolConfig(String poolName) {
        return defaultThreadPoolConfig().poolName(poolName);
    }

    public static ThreadPoolStats.ThreadPoolStatsBuilder defaultStats() {
        return ThreadPoolStats.builder()
                .poolName("test-pool")
                .corePoolSize(4)
                .maximumPoolSize(8)
                .poolSize(4)
                .activeCount(2)
                .queueSize(10)
                .queueCapacity(100)
                .queueRemainingCapacity(90)
                .completedTaskCount(1000L)
                .submittedTaskCount(1010L)
                .errorTaskCount(0L)
                .rejectedTaskCount(0L)
                .largestPoolSize(6)
                .taskCount(1010L)
                .isShutdown(false)
                .isTerminated(false);
    }

    public static List<ThreadPoolConfig> buildConfigList(String... poolNames) {
        return java.util.Arrays.stream(poolNames)
                .map(name -> customThreadPoolConfig(name).build())
                .toList();
    }
}



