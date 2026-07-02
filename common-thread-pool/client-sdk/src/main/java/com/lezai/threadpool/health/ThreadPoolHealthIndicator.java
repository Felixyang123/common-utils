package com.lezai.threadpool.health;

import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线程池健康检查：活跃线程占比或队列使用率超过阈值时报 DOWN。
 * <p>
 * 只用标准 {@code UP} / {@code DOWN}——Kubernetes 探针、Prometheus 等基础设施只识别这两种，
 * 自定义 WARNING 状态无处消费。需要分级预警时应基于 {@code threadpool.*} 指标的阈值告警，
 * 而非 health 状态。
 */
@Slf4j
public class ThreadPoolHealthIndicator implements HealthIndicator {

    private final ThreadPoolManager threadPoolManager;
    private final double activeThreadThreshold;
    private final double queueUsageThreshold;

    public ThreadPoolHealthIndicator(ThreadPoolManager threadPoolManager,
                                      double activeThreadThreshold,
                                      double queueUsageThreshold) {
        this.threadPoolManager = threadPoolManager;
        this.activeThreadThreshold = activeThreadThreshold;
        this.queueUsageThreshold = queueUsageThreshold;
    }

    @Override
    public Health health() {
        List<DynamicThreadPoolWrapper> pools = threadPoolManager.getAllWrappers();
        Map<String, Object> details = new LinkedHashMap<>();
        boolean healthy = true;

        for (DynamicThreadPoolWrapper pool : pools) {
            double activeRatio = ratio(pool.getActiveCount(), pool.getMaximumPoolSize());
            int queueCapacity = pool.getQueueRemainingCapacity() + pool.getQueueSize();
            double queueRatio = ratio(pool.getQueueSize(), queueCapacity);

            boolean poolHealthy = activeRatio < activeThreadThreshold && queueRatio < queueUsageThreshold;
            if (!poolHealthy) {
                healthy = false;
            }

            Map<String, Object> poolDetail = new LinkedHashMap<>();
            poolDetail.put("activeRatio", activeRatio);
            poolDetail.put("queueRatio", queueRatio);
            poolDetail.put("status", poolHealthy ? "UP" : "DOWN");
            details.put(pool.getPoolName(), poolDetail);
        }

        Health.Builder builder = healthy ? Health.up() : Health.down();
        return builder.withDetails(details).build();
    }

    private double ratio(int numerator, int denominator) {
        return denominator > 0 ? (double) numerator / denominator : 0.0;
    }
}
