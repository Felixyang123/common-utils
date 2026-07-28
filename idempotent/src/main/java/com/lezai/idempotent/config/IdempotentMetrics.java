package com.lezai.idempotent.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 幂等组件 Metrics 埋点（Micrometer，optional 依赖）
 */
@Slf4j
public class IdempotentMetrics {

    private final Counter requestTotal;
    private final Counter requestDuplicate;
    private final Counter takeoverTotal;
    private final Timer executeDuration;

    public IdempotentMetrics(MeterRegistry registry) {
        if (registry != null) {
            this.requestTotal = Counter.builder("idempotent.request.total")
                    .description("幂等请求总量")
                    .register(registry);
            this.requestDuplicate = Counter.builder("idempotent.request.duplicate")
                    .description("重复请求拦截量")
                    .tag("status", "succeeded")
                    .register(registry);
            this.takeoverTotal = Counter.builder("idempotent.request.takeover")
                    .description("崩溃接管次数")
                    .register(registry);
            this.executeDuration = Timer.builder("idempotent.execute.duration")
                    .description("业务执行耗时")
                    .register(registry);
            log.info("Idempotent metrics initialized with Micrometer");
        } else {
            this.requestTotal = null;
            this.requestDuplicate = null;
            this.takeoverTotal = null;
            this.executeDuration = null;
            log.info("Micrometer not available, idempotent metrics disabled");
        }
    }

    public void recordRequest() {
        if (requestTotal != null) requestTotal.increment();
    }

    public void recordDuplicate() {
        if (requestDuplicate != null) requestDuplicate.increment();
    }

    public void recordTakeover() {
        if (takeoverTotal != null) takeoverTotal.increment();
    }

    public void recordExecuteDuration(long durationMs) {
        if (executeDuration != null) executeDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public boolean isEnabled() {
        return requestTotal != null;
    }
}
