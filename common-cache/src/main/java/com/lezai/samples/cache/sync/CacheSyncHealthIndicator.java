package com.lezai.samples.cache.sync;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 缓存同步订阅健康检查：报告 RedisMessageListenerContainer 是否在运行。
 * 供 K8s liveness/readiness 探针感知订阅断连（容器虽自动重连，但长期 down 表示异常）。
 */
public class CacheSyncHealthIndicator implements HealthIndicator {

    private final RedisMessageListenerContainer container;

    public CacheSyncHealthIndicator(RedisMessageListenerContainer container) {
        this.container = container;
    }

    @Override
    public Health health() {
        if (container.isRunning()) {
            return Health.up().withDetail("state", "active").build();
        }
        return Health.down().withDetail("state", "inactive").build();
    }
}
