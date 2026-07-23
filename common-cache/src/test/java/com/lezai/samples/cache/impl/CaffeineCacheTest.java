package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.CacheDegradationSupport;
import com.lezai.samples.cache.core.CacheWrapper;
import com.lezai.samples.cache.core.DegradationGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineCacheTest {

    @AfterEach
    void reset() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
    }

    @Test
    void miss_loadsThroughGuard() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        CaffeineCache<Object> cache = new CaffeineCache<>(100, 30_000);
        Object v = cache.loadAndCache("k", 60_000L, key -> "v");
        assertThat(v).isEqualTo("v");
    }

    @Test
    void logicallyExpiredEntry_isRefreshed_notServedAsFresh() {
        // 旧实现用 Caffeine cache.get，对 wrapper.expired() 视而不见会一直返回旧值
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        CaffeineCache<Object> cache = new CaffeineCache<>(100, 30_000);
        cache.innerSet("k", CacheWrapper.of("old", -1L)); // 已逻辑过期但仍在 Caffeine(grace 内）
        AtomicInteger loads = new AtomicInteger();
        Object v = cache.loadAndCache("k", 60_000L, key -> {
            loads.incrementAndGet();
            return "new";
        });
        assertThat(v).isEqualTo("new");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void freshEntry_isReturnedWithoutReload() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        CaffeineCache<Object> cache = new CaffeineCache<>(100, 30_000);
        cache.innerSet("k", CacheWrapper.of("v", 60_000L));
        AtomicInteger loads = new AtomicInteger();
        Object v = cache.loadAndCache("k", 60_000L, key -> {
            loads.incrementAndGet();
            return "other";
        });
        assertThat(v).isEqualTo("v");
        assertThat(loads.get()).isEqualTo(0);
    }

    @Test
    void guardReject_servesStale() {
        CacheDegradationSupport.init(DegradationGuard.of(1_000_000, 0, 10), 1000);
        CaffeineCache<Object> cache = new CaffeineCache<>(100, 30_000);
        cache.innerSet("k", CacheWrapper.of("stale", -1L)); // 过期旧值，grace 内
        Object v = cache.loadAndCache("k", 60_000L, key -> {
            throw new AssertionError("should not hit DB when guard rejects");
        });
        assertThat(v).isEqualTo("stale");
    }
}
