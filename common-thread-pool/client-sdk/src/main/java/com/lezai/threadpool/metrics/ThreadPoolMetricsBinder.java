package com.lezai.threadpool.metrics;

import com.lezai.threadpool.core.DynamicThreadPoolWrapper;
import com.lezai.threadpool.manager.ThreadPoolManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Micrometer {@link MeterBinder} 用于暴露动态线程池的运行指标。
 * <p>
 * 在 {@link MeterRegistry} 可用时装配（可选依赖）。每个池注册一组 gauge，
 * 以 {@code pool} 和 {@code app} tag 区分。
 * <p>
 * 使用快照模式：bind 时遍历当前所有池注册 Gauge。Gauge 函数持有 wrapper 引用
 * 以零开销读取实时值；通过 {@code threadPoolManager.getPool(name)} 检查存活——
 * 已移除的池返回 sentinel (-1) 供监控系统识别。
 * <p>
 * 指标前缀: {@code threadpool.}
 */
@Slf4j
public class ThreadPoolMetricsBinder implements MeterBinder {

    private final ThreadPoolManager threadPoolManager;
    private final String appId;

    public ThreadPoolMetricsBinder(ThreadPoolManager threadPoolManager, String appId) {
        this.threadPoolManager = threadPoolManager;
        this.appId = appId;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        log.info("Binding thread pool metrics for appId: {}", appId);

        List<DynamicThreadPoolWrapper> pools = getAllWrappers();
        for (DynamicThreadPoolWrapper pool : pools) {
            registerPoolMetrics(registry, pool);
        }
        log.info("Registered metrics for {} thread pools", pools.size());
    }

    private void registerPoolMetrics(MeterRegistry registry, DynamicThreadPoolWrapper pool) {
        String poolName = pool.getPoolName();
        Iterable<Tag> tags = Tags.of("pool", poolName, "app", appId);

        Gauge.builder("threadpool.threads.core", pool, p -> alive(poolName) ? p.getCorePoolSize() : -1)
                .tags(tags).description("Core pool size").register(registry);
        Gauge.builder("threadpool.threads.max", pool, p -> alive(poolName) ? p.getMaximumPoolSize() : -1)
                .tags(tags).description("Maximum pool size").register(registry);
        Gauge.builder("threadpool.threads.active", pool, p -> alive(poolName) ? p.getActiveCount() : -1)
                .tags(tags).description("Active threads").register(registry);
        Gauge.builder("threadpool.threads.pool", pool, p -> alive(poolName) ? p.getPoolSize() : -1)
                .tags(tags).description("Current pool size").register(registry);

        Gauge.builder("threadpool.queue.size", pool, p -> alive(poolName) ? p.getQueueSize() : -1)
                .tags(tags).description("Queue size").register(registry);
        Gauge.builder("threadpool.queue.capacity", pool, p ->
                        alive(poolName) ? p.getQueueRemainingCapacity() + p.getQueueSize() : -1)
                .tags(tags).description("Queue capacity").register(registry);

        Gauge.builder("threadpool.tasks.completed", pool, p -> alive(poolName) ? p.getCompletedTaskCount() : -1)
                .tags(tags).description("Completed tasks").register(registry);
        Gauge.builder("threadpool.tasks.submitted", pool, p -> alive(poolName) ? p.getSubmittedTaskCount() : -1)
                .tags(tags).description("Submitted tasks").register(registry);
        Gauge.builder("threadpool.tasks.error", pool, p -> alive(poolName) ? p.getErrorTaskCount() : -1)
                .tags(tags).description("Error tasks").register(registry);
        Gauge.builder("threadpool.tasks.rejected", pool, p -> alive(poolName) ? p.getRejectedTaskCount() : -1)
                .tags(tags).description("Rejected tasks").register(registry);

        Gauge.builder("threadpool.load.factor", pool, p -> {
                    if (!alive(poolName)) return -1.0;
                    int max = p.getMaximumPoolSize();
                    return max > 0 ? (double) p.getActiveCount() / max : 0.0;
                })
                .tags(tags).description("Load factor: active/max").register(registry);
    }

    private boolean alive(String poolName) {
        return threadPoolManager.getPool(poolName) != null;
    }

    private List<DynamicThreadPoolWrapper> getAllWrappers() {
        return threadPoolManager.getAllWrappers();
    }
}
