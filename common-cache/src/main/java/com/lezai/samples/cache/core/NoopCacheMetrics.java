package com.lezai.samples.cache.core;

/**
 * CacheMetrics 的空实现：所有操作无动作。作为 {@link CacheMetricsHolder} 的默认值，
 * 在未接入指标后端（非 Spring 环境或单测）下保证零开销。
 */
public final class NoopCacheMetrics implements CacheMetrics {

    public static final NoopCacheMetrics INSTANCE = new NoopCacheMetrics();

    private NoopCacheMetrics() {
    }

    @Override
    public void hit() {
    }

    @Override
    public void miss() {
    }

    @Override
    public void staleServed() {
    }

    @Override
    public void recordLoad() {
    }

    @Override
    public void guardRejected(String reason) {
    }

    @Override
    public void singleFlightTimeout() {
    }

    @Override
    public void syncPublished() {
    }

    @Override
    public void syncReceived() {
    }

    @Override
    public void syncError() {
    }
}
