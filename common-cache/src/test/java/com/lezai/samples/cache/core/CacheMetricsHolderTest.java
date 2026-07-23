package com.lezai.samples.cache.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheMetricsHolderTest {

    @AfterEach
    void reset() {
        CacheMetricsHolder.init(NoopCacheMetrics.INSTANCE);
    }

    @Test
    void defaults_toNoop() {
        assertThat(CacheMetricsHolder.metrics()).isSameAs(NoopCacheMetrics.INSTANCE);
    }

    @Test
    void init_replacesMetrics() {
        CacheMetrics probe = new CacheMetrics() {
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
            public void recordLoad(long nanos) {
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
        };
        CacheMetricsHolder.init(probe);
        assertThat(CacheMetricsHolder.metrics()).isSameAs(probe);
    }

    @Test
    void noop_allMethodsAreNoop() {
        NoopCacheMetrics noop = NoopCacheMetrics.INSTANCE;
        // 调用不应抛异常、不产生副作用
        noop.hit();
        noop.miss();
        noop.staleServed();
        noop.recordLoad(1_000_000L);
        noop.guardRejected("rate-limit");
        noop.singleFlightTimeout();
        noop.syncPublished();
        noop.syncReceived();
        noop.syncError();
        assertThat(noop).isSameAs(NoopCacheMetrics.INSTANCE);
    }
}
