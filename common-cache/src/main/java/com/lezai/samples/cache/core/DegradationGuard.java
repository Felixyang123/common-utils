package com.lezai.samples.cache.core;

import com.google.common.util.concurrent.RateLimiter;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 本地降级护栏：令牌桶限速率（保护共享 DB 吞吐）+ 信号量限并发（保护 DB 连接池）。
 * 全部 JVM 内实现——核心降级场景（Redis 不可用）下 Redis 版限流器同样失效，存在循环依赖。
 * 检查顺序：先非阻塞取令牌，再有界等待舱壁；绝不持有舱壁 permit 去等令牌。
 */
public class DegradationGuard {

    private final RateLimiter tokenBucket;
    private final Semaphore bulkhead;
    private final long bulkheadWaitMs;
    private final boolean enabled;

    private DegradationGuard(Double permitsPerSecond, Integer bulkheadPermits, long bulkheadWaitMs, boolean enabled) {
        this.enabled = enabled;
        this.tokenBucket = permitsPerSecond != null ? RateLimiter.create(permitsPerSecond) : null;
        this.bulkhead = bulkheadPermits != null ? new Semaphore(bulkheadPermits) : null;
        this.bulkheadWaitMs = bulkheadWaitMs;
    }

    public static DegradationGuard of(double permitsPerSecond, int bulkheadPermits, long bulkheadWaitMs) {
        return new DegradationGuard(permitsPerSecond, bulkheadPermits, bulkheadWaitMs, true);
    }

    /** 未配置时的默认护栏：全部放行（行为等同无防护，向后兼容非 Spring 环境与单测）。 */
    public static DegradationGuard disabled() {
        return new DegradationGuard(null, null, 0, false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 尝试获取回源许可。拒绝时抛 {@link CacheDegradedException}；成功返回须 close 的 {@link Permit}.
     */
    public Permit acquire(String key) {
        if (!enabled) {
            return Permit.NOOP;
        }
        if (tokenBucket != null && !tokenBucket.tryAcquire()) {
            throw new CacheDegradedException("rate limit exceeded for key=" + key);
        }
        if (bulkhead != null) {
            try {
                if (!bulkhead.tryAcquire(bulkheadWaitMs, TimeUnit.MILLISECONDS)) {
                    throw new CacheDegradedException("bulkhead full for key=" + key);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CacheDegradedException("interrupted while acquiring bulkhead for key=" + key);
            }
            return new Permit(bulkhead);
        }
        return Permit.NOOP;
    }

    /** 舱壁许可句柄：回源完成后必须 close 释放并发额度。 */
    public static final class Permit implements AutoCloseable {
        static final Permit NOOP = new Permit(null);

        private final Semaphore semaphore;
        private boolean released;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (!released && semaphore != null) {
                semaphore.release();
                released = true;
            }
        }
    }
}