package com.lezai.samples.cache.core;

/**
 * 缓存指标持有器：与 {@link CacheDegradationSupport} 同风格，由自动配置在启动时注入一次。
 * 未注入时使用 {@link NoopCacheMetrics}，保证非 Spring 环境与单元测试零开销。
 */
public final class CacheMetricsHolder {

    private static volatile CacheMetrics metrics = NoopCacheMetrics.INSTANCE;

    private CacheMetricsHolder() {
    }

    /** 注入指标实现（通常由自动配置在启动时调用一次）。 */
    public static void init(CacheMetrics m) {
        metrics = m != null ? m : NoopCacheMetrics.INSTANCE;
    }

    /** 获取当前指标实现。 */
    public static CacheMetrics metrics() {
        return metrics;
    }
}
