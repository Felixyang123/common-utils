package com.lezai.samples.cache.core;

/**
 * 缓存指标持有器：与 {@link CacheDegradationSupport} 同风格，由自动配置在启动时注入一次。
 * 未注入时使用 {@link NoopCacheMetrics}，保证非 Spring 环境与单元测试零开销。
 */
public final class CacheMetricsHolder {

    private static volatile CacheMetrics metrics = NoopCacheMetrics.INSTANCE;

    private static final ThreadLocal<Boolean> SUPPRESSED = ThreadLocal.withInitial(() -> false);

    private CacheMetricsHolder() {
    }

    /** 注入指标实现（通常由自动配置在启动时调用一次）。 */
    public static void init(CacheMetrics m) {
        metrics = m != null ? m : NoopCacheMetrics.INSTANCE;
    }

    /** 获取当前指标实现。当处于抑制状态时返回 noop 实例。 */
    public static CacheMetrics metrics() {
        if (SUPPRESSED.get()) {
            return NoopCacheMetrics.INSTANCE;
        }
        return metrics;
    }

    /** 当前线程是否处于指标抑制状态。 */
    public static boolean isSuppressed() {
        return SUPPRESSED.get();
    }

    /** 抑制当前线程的指标记录。 */
    public static void suppressMetrics() {
        SUPPRESSED.set(true);
    }

    /** 恢复当前线程的指标记录。 */
    public static void restoreMetrics() {
        SUPPRESSED.set(false);
    }
}
