package com.lezai.threadpool.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 线程池配置属性
 */
@Data
@ConfigurationProperties(prefix = "thread.pool")
public class ThreadPoolProperties {

    /**
     * 是否启用线程池组件
     */
    private boolean enabled = true;

    /**
     * 自定义线程池配置列表
     */
    private PoolConfig[] pools = new PoolConfig[0];

    /**
     * CS 模式配置
     */
    private RemoteServerConfig remote = new RemoteServerConfig();

    @Data
    public static class PoolConfig {
        /**
         * 线程池名称
         */
        private String name = "default-pool";

        /**
         * 核心线程数
         */
        private int corePoolSize = Runtime.getRuntime().availableProcessors();

        /**
         * 最大线程数
         */
        private int maximumPoolSize = Runtime.getRuntime().availableProcessors() * 2;

        /**
         * 空闲线程存活时间（秒）
         */
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
    }

    /**
     * CS 模式配置（包含服务端和客户端）
     */
    @Data
    public static class RemoteServerConfig {

        /**
         * 服务端配置（仅服务端启用）
         */
        private ServerConfig server = new ServerConfig();

        /**
         * 客户端配置（仅客户端启用）
         */
        private ClientConfig client = new ClientConfig();

        @Data
        public static class ServerConfig {
            /**
             * 是否启用服务端
             */
            private boolean enabled = false;

            /**
             * 服务端端口
             */
            private int port = 8088;

            /**
             * 服务端地址
             */
            private String host = "http://localhost";

            public String getServerUrl() {
                return host + ":" + port;
            }

        }

        @Data
        public static class ClientConfig {

            /**
             * 应用 ID
             */
            private String appId = "default-app";

            /**
             * API 密钥
             */
            private String apiKey;

            /**
             * 长轮询超时时间（毫秒）
             */
            private long longPollingTimeoutMs = 30000L;

            /**
             * 短轮询间隔时间（毫秒）
             */
            private long pullIntervalMs;
        }
    }

}
