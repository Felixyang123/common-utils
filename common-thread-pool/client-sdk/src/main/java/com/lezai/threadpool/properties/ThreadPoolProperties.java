package com.lezai.threadpool.properties;

import com.lezai.threadpool.client.router.ServerNodeParser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 线程池配置属性
 */
@Data
@Validated
@ConfigurationProperties(prefix = "thread.pool")
@Slf4j
public class ThreadPoolProperties {

    /**
     * 是否启用线程池组件
     */
    private boolean enabled = true;

    /**
     * 自定义线程池配置列表
     */
    @Valid
    private PoolConfig[] pools = new PoolConfig[0];

    /** 远程（CS 客户端）配置 */
    @Valid
    private RemoteConfig remote = new RemoteConfig();

    @Data
    public static class PoolConfig {
        /**
         * 线程池名称
         */
        private String name = "default-pool";

        /**
         * 核心线程数
         */
        @Min(1)
        private int corePoolSize = Runtime.getRuntime().availableProcessors();

        /**
         * 最大线程数
         */
        @Min(1)
        private int maximumPoolSize = Runtime.getRuntime().availableProcessors() * 2;

        /**
         * 空闲线程存活时间（秒）
         */
        @PositiveOrZero
        private long keepAliveTime = 60L;

        /**
         * 队列容量
         */
        private int queueCapacity = 1024;

        /**
         * 队列类型：ARRAY_BLOCKING_QUEUE, BLOCKING_QUEUE, PRIORITY_BLOCKING_QUEUE, DELAY_QUEUE, SYNCHRONOUS_QUEUE, LINKED_BLOCKING_QUEUE
         */
        private String queueType = "BLOCKING_QUEUE";

        /**
         * 拒绝策略类型：ABORT, DISCARD, DISCARD_OLDEST, CALLER_RUNS, BLOCKED
         */
        private String rejectPolicyType = "ABORT";

        /**
         * 是否允许核心线程超时
         */
        private boolean allowCoreThreadTimeout = false;

        /**
         * 线程名称前缀
         */
        private String threadNamePrefix;

        /**
         * 是否为守护线程
         */
        private boolean daemon = false;

        @AssertTrue(message = "corePoolSize must not exceed maximumPoolSize")
        public boolean isPoolSizeValid() {
            return corePoolSize <= maximumPoolSize;
        }
    }

    /**
     * 远程（CS 客户端模式）配置 — 描述客户端如何连接 admin-server。
     * <p>
     * 注意: 这是"客户端连服务端"的连接信息，<b>不是</b>服务端自身的配置（服务端配置在 {@code threadpool.admin.*}）。
     */
    @Data
    public static class RemoteConfig {

        /** 是否启用 CS 客户端模式（默认 false，即 LOCAL 模式） */
        private boolean enabled = false;

        /** admin-server 地址（默认 http://localhost:8080） */
        private String serverUrl = "http://localhost:8080";

        /** 远程连接模式：single / cluster */
        private String mode = "single";

        /** 集群路由算法 */
        @Pattern(regexp = "(?i)round-robin|weighted-round-robin|random|failover",
                message = "routing-algorithm must be one of: round-robin, weighted-round-robin, random, failover")
        private String routingAlgorithm = "round-robin";

        /** 健康检查间隔（毫秒） */
        @PositiveOrZero
        private long healthCheckIntervalMs = 30000L;

        /** 快速健康检查间隔（毫秒） */
        @PositiveOrZero
        private long healthCheckFastIntervalMs = 5000L;

        /** 熔断配置 */
        @Valid
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

        /** 降级轮询配置 */
        @Valid
        private DegradedConfig degraded = new DegradedConfig();

        /** 应用 ID（客户端身份标识） */
        private String appId = "default-app";

        /** API 密钥（remote.enabled=true 时必填） */
        private String apiKey;

        /** 长轮询超时（毫秒），默认 30000 */
        @PositiveOrZero
        private long longPollingTimeoutMs = 30000L;

        /** 短轮询补偿间隔（毫秒），默认 0 = 禁用 */
        @PositiveOrZero
        private long pullIntervalMs = 0L;

        /** 长轮询错误后退避初始间隔（毫秒），默认 1000 */
        @PositiveOrZero
        private long backoffInitialMs = 1000L;

        /** 长轮询错误后退避最大间隔（毫秒），默认 30000 */
        @PositiveOrZero
        private long backoffMaxMs = 30000L;

        /** 是否上报统计信息 */
        private boolean reportEnabled = true;

        /** 统计上报间隔（毫秒），默认 60000 */
        @PositiveOrZero
        private long reportIntervalMs = 60000L;

        /**
         * 条件校验：仅当 remote.enabled=true 时检查必填连接字段。
         * 这样 local 模式（enabled=false、不设 api-key）不会触发校验失败。
         */
        @AssertTrue(message = "When remote.enabled=true, server-url, app-id, and api-key must not be blank")
        public boolean isConnectionConfigValid() {
            if (!enabled) {
                return true;
            }
            return serverUrl != null && !serverUrl.isBlank()
                    && appId != null && !appId.isBlank()
                    && apiKey != null && !apiKey.isBlank();
        }

        @AssertTrue(message = "remote.mode=single requires exactly one server-url, remote.mode=cluster requires at least two server-url values")
        public boolean isModeAndServerUrlValid() {
            if (!enabled) {
                return true;
            }
            int count;
            try {
                count = ServerNodeParser.parse(serverUrl).size();
            } catch (IllegalArgumentException e) {
                log.error("parse server-url failed: {}", e.getMessage());
                return false;
            }
            if ("single".equalsIgnoreCase(mode)) {
                return count == 1;
            }
            if ("cluster".equalsIgnoreCase(mode)) {
                return count >= 2;
            }
            return false;
        }

        @Data
        public static class CircuitBreakerConfig {
            @Min(1)
            private int failureThreshold = 3;

            @PositiveOrZero
            private long openDurationMs = 30000L;
        }

        @Data
        public static class DegradedConfig {
            @PositiveOrZero
            private long pullIntervalMs = 120000L;

            @PositiveOrZero
            private long reportIntervalMs = 300000L;
        }
    }

}
