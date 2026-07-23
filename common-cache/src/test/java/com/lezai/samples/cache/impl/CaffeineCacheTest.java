package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.CacheDegradationSupport;
import com.lezai.samples.cache.core.CacheDegradedException;
import com.lezai.samples.cache.core.DegradationGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaffeineCacheTest {

    @AfterEach
    void reset() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
    }

    @Test
    void miss_loadsThroughGuard() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        CaffeineCache<Object> cache = new CaffeineCache<>(100, 60_000);
        Object v = cache.loadAndCache("k", 60_000L, key -> "v");
        assertThat(v).isEqualTo("v");
    }

    @Test
    void guardReject_onMiss_throws() {
        CacheDegradationSupport.init(DegradationGuard.of(1_000_000, 0, 10), 1000);
        CaffeineCache<Object> cache = new CaffeineCache<>(100, 60_000);
        assertThatThrownBy(() -> cache.loadAndCache("missing", 60_000L, key -> "v"))
                .isInstanceOf(CacheDegradedException.class);
    }

    @Test
    void guardReject_withStale_servesStale() {
        CacheDegradationSupport.init(DegradationGuard.of(1_000_000, 0, 10), 1000);
        CaffeineCache<Object> cache = new CaffeineCache<>(100, 60_000);
        cache.innerSet("k", com.lezai.samples.cache.core.CacheWrapper.of("stale", -1L)); // expired stale value
        Object v = cache.loadAndCache("k", 60_000L, key -> {
            throw new AssertionError("should not hit DB when guard rejects");
        });
        org.assertj.core.api.Assertions.assertThat(v).isEqualTo("stale");
    }
}