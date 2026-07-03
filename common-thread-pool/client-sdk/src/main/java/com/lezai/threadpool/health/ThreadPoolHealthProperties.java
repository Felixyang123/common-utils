package com.lezai.threadpool.health;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 线程池健康检查阈值配置
 */
@Data
@ConfigurationProperties(prefix = "thread.pool.health")
public class ThreadPoolHealthProperties {

    /**
     * 活跃线程占比阈值，超过此值报 DOWN（默认 0.9）
     */
    private double activeThreadThreshold = 0.9;

    /**
     * 队列使用率阈值，超过此值报 DOWN（默认 0.9）
     */
    private double queueUsageThreshold = 0.9;
}