package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.CacheDegradationSupport;
import com.lezai.samples.cache.core.CacheWrapper;
import com.lezai.samples.cache.core.DegradationGuard;
import com.lezai.samples.cache.core.MultiCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MultiCaffeineCacheTest {

    @AfterEach
    void reset() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
    }

    @Test
    void l1Miss_l2Hit_backfillsL1() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        MultiCaffeineCache<Object> l2 = new MultiCaffeineCache<>(100, 30_000, null);
        MultiCaffeineCache<Object> l1 = new MultiCaffeineCache<>(100, 30_000, l2);
        l2.innerSet("k", CacheWrapper.of("from-l2", 60_000L));
        AtomicInteger db = new AtomicInteger();
        Object v = l1.loadAndCache("k", 60_000L, key -> {
            db.incrementAndGet();
            return "db";
        });
        assertThat(v).isEqualTo("from-l2");
        assertThat(db.get()).isEqualTo(0);
        assertThat(l1.innerGet("k").getData()).isEqualTo("from-l2");
    }

    @Test
    void remove_clearsBothLevels() {
        MultiCaffeineCache<Object> l2 = new MultiCaffeineCache<>(100, 30_000, null);
        MultiCaffeineCache<Object> l1 = new MultiCaffeineCache<>(100, 30_000, l2);
        l1.innerSet("k", CacheWrapper.of("v", 60_000L));
        l2.innerSet("k", CacheWrapper.of("v", 60_000L));
        l1.remove("k");
        assertThat(l1.innerGet("k")).isNull();
        assertThat(l2.innerGet("k")).isNull();
    }

    @Test
    void nextLevelCache_returnsConfiguredNext() {
        MultiCache<Object> l2 = new MultiCaffeineCache<>(100, 30_000, null);
        MultiCaffeineCache<Object> l1 = new MultiCaffeineCache<>(100, 30_000, l2);
        assertThat(l1.nextLevelCache()).isSameAs(l2);
        assertThat(l2.nextLevelCache()).isNull();
    }
}
