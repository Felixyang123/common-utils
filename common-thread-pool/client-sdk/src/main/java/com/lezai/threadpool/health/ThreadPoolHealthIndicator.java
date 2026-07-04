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
 * 线程池健康检查。
 * <p>
 * 每个池三态：
 * <ul>
 *     <li><b>UP</b>：正常工作（活跃/队列未超阈值，且非闲置）</li>
 *     <li><b>IDLE</b>：闲置——从未执行过任务且当前无排队（{@code completedTaskCount == 0 &&
 *         activeCount == 0 && queueSize == 0}）。可能是新池还没流量，也可能是配错导致任务没路由进来。
 *         IDLE 只在 details 标记，<b>不拖累整体健康</b>——闲置池不影响进程的服务能力，
 *         是否真有问题应由 metrics + 告警规则判断，而非 health 状态</li>
 *     <li><b>DOWN</b>：过载——活跃线程占比或队列使用率超过阈值</li>
 * </ul>
 * 整体健康 = 所有池都不是 DOWN（IDLE 不影响）。只用标准 {@code UP} / {@code DOWN}——
 * Kubernetes 探针、Prometheus 等基础设施只识别这两种。
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

            String status = classify(activeRatio, queueRatio, pool);

            // 只有 DOWN 拖累整体健康；IDLE 不拖累（闲置池不代表进程不能服务）
            if ("DOWN".equals(status)) {
                healthy = false;
            }

            Map<String, Object> poolDetail = new LinkedHashMap<>();
            poolDetail.put("activeRatio", activeRatio);
            poolDetail.put("queueRatio", queueRatio);
            poolDetail.put("completedTaskCount", pool.getCompletedTaskCount());
            poolDetail.put("status", status);
            if ("IDLE".equals(status)) {
                poolDetail.put("hint", "pool has never executed any task — verify task routing / pool name");
            }
            details.put(pool.getPoolName(), poolDetail);
        }

        Health.Builder builder = healthy ? Health.up() : Health.down();
        return builder.withDetails(details).build();
    }

    /**
     * 分类池状态：过载(DOWN) > 闲置(IDLE) > 正常(UP)。
     * 先判过载（即便 completedTaskCount==0，一旦过载也是 DOWN）。
     */
    private String classify(double activeRatio, double queueRatio, DynamicThreadPoolWrapper pool) {
        if (activeRatio >= activeThreadThreshold || queueRatio >= queueUsageThreshold) {
            return "DOWN";
        }
        if (pool.getCompletedTaskCount() == 0 && pool.getActiveCount() == 0 && pool.getQueueSize() == 0) {
            return "IDLE";
        }
        return "UP";
    }

    private double ratio(int numerator, int denominator) {
        return denominator > 0 ? (double) numerator / denominator : 0.0;
    }
}
