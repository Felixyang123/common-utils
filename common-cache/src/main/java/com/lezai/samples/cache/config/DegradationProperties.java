package com.lezai.samples.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 降级防护配置。所有限流/舱壁均为 JVM 内实现，用于在缓存雪崩/击穿时保护共享 DB。
 * 调参需按利特尔法则对齐：bulkheadPermits ÷ 平均回源延迟(秒) ≈ 护栏允许的最大回源 QPS，
 * permitsPerSecond 应不高于该值，且为共享 DB 上的核心业务预留吞吐。
 */
@Data
@ConfigurationProperties(prefix = "cache.degradation")
public class DegradationProperties {

    /** 是否启用降级护栏。false 时护栏放行，行为等同无防护。 */
    private boolean enabled = true;

    /** 令牌桶速率（回源 QPS 上限，保护共享 DB 的 CPU/IO 吞吐）。 */
    private double permitsPerSecond = 100;

    /** 舱壁并发数（同时在飞的回源查询上限，应 ≤ DB 连接池容量）。 */
    private int bulkheadPermits = 50;

    /** 舱壁有界等待毫秒数，超时则拒绝（配合 serve-stale）。 */
    private long bulkheadWaitMs = 200;

    /** 单飞追随者等待领导者结果的毫秒数，超时抛 CacheDegradedException。 */
    private long singleFlightWaitMs = 5000;
}