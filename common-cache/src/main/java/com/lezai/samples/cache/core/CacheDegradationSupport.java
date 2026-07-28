package com.lezai.samples.cache.core;

/**
 * 降级防护持有器：与 LockSupport 同风格，由自动配置在启动时注入一次。
 * 未注入时使用默认值（护栏放行 + 共享单飞），保证非 Spring 环境与单元测试可用。
 */
public final class CacheDegradationSupport {

    private static final SingleFlight SINGLE_FLIGHT = new SingleFlight();

    private static volatile DegradationGuard guard = DegradationGuard.disabled();
    private static volatile long singleFlightWaitMs = 5000;

    private CacheDegradationSupport() {
    }

    public static void init(DegradationGuard g, long waitMs) {
        guard = g;
        singleFlightWaitMs = waitMs;
    }

    public static SingleFlight singleFlight() {
        return SINGLE_FLIGHT;
    }

    public static DegradationGuard guard() {
        return guard;
    }

    public static long singleFlightWaitMs() {
        return singleFlightWaitMs;
    }
}