package com.lezai.threadpool.bean;

import com.lezai.threadpool.enumeration.QueueType;
import com.lezai.threadpool.enumeration.RejectPolicyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 线程池配置
 */
@Data
@Builder
public class ThreadPoolConfig {

    /**
     * 线程池名称
     */
    @NotBlank(message = "poolName不能为空")
    @Size(max = 128, message = "poolName长度不能超过128")
    private String poolName;

    /**
     * 核心线程数
     */
    @Min(value = 1, message = "corePoolSize不能小于1")
    @Max(value = 1024, message = "corePoolSize不能超过1024")
    private int corePoolSize;

    /**
     * 最大线程数
     */
    @Min(value = 1, message = "maximumPoolSize不能小于1")
    @Max(value = 1024, message = "maximumPoolSize不能超过1024")
    private int maximumPoolSize;

    /**
     * 空闲线程存活时间
     */
    @Min(value = 0, message = "keepAliveTime不能为负数")
    private long keepAliveTime;

    /**
     * 时间单位
     */
    private TimeUnit timeUnit;

    /**
     * 队列类型
     */
    private QueueType queueType;

    /**
     * 队列容量
     */
    @Min(value = 0, message = "queueCapacity不能为负数")
    @Max(value = 100000, message = "queueCapacity不能超过100000")
    private int queueCapacity;

    /**
     * 拒绝策略类型
     */
    private RejectPolicyType rejectPolicyType;

    /**
     * 是否允许核心线程超时
     */
    private boolean allowCoreThreadTimeout;

    /**
     * 线程工厂名称前缀
     */
    @Size(max = 64, message = "threadNamePrefix长度不能超过64")
    private String threadNamePrefix;

    /**
     * 是否为守护线程
     */
    private boolean daemon;

    public static ThreadPoolConfigBuilder builder() {
        return new ThreadPoolConfigBuilder();
    }

    public static class ThreadPoolConfigBuilder {
        private String poolName = "default-pool";
        private int corePoolSize = Runtime.getRuntime().availableProcessors();
        private int maximumPoolSize = corePoolSize * 2;
        private long keepAliveTime = 60L;
        private TimeUnit timeUnit = TimeUnit.SECONDS;
        private QueueType queueType = QueueType.BLOCKING_QUEUE;
        private int queueCapacity = 1024;
        private RejectPolicyType rejectPolicyType = RejectPolicyType.ABORT;
        private boolean allowCoreThreadTimeout = false;
        private String threadNamePrefix;
        private boolean daemon = false;

        public ThreadPoolConfigBuilder poolName(String poolName) {
            this.poolName = poolName;
            return this;
        }

        public ThreadPoolConfigBuilder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public ThreadPoolConfigBuilder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public ThreadPoolConfigBuilder keepAliveTime(long keepAliveTime) {
            this.keepAliveTime = keepAliveTime;
            return this;
        }

        public ThreadPoolConfigBuilder timeUnit(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
            return this;
        }

        public ThreadPoolConfigBuilder queueType(QueueType queueType) {
            this.queueType = queueType;
            return this;
        }

        public ThreadPoolConfigBuilder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        public ThreadPoolConfigBuilder rejectPolicyType(RejectPolicyType rejectPolicyType) {
            this.rejectPolicyType = rejectPolicyType;
            return this;
        }

        public ThreadPoolConfigBuilder allowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
            this.allowCoreThreadTimeout = allowCoreThreadTimeout;
            return this;
        }

        public ThreadPoolConfigBuilder threadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
            return this;
        }

        public ThreadPoolConfigBuilder daemon(boolean daemon) {
            this.daemon = daemon;
            return this;
        }

        public ThreadPoolConfig build() {
            if (StringUtils.isBlank(threadNamePrefix)) {
                threadNamePrefix = poolName;
            }
            return new ThreadPoolConfig(
                    poolName, corePoolSize, maximumPoolSize, keepAliveTime,
                    timeUnit, queueType, queueCapacity, rejectPolicyType,
                    allowCoreThreadTimeout, threadNamePrefix, daemon
            );
        }
    }

}
