package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.Cache;
import com.lezai.samples.cache.core.MultiCache;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineCacheManagerTest {

    @Test
    void createsCaffeineCachePerCategory_andCachesInstance() {
        Cache<Object> global = new CaffeineCache<>(10, 30_000);
        CaffeineCacheManager mgr = new CaffeineCacheManager(global, 50, 30_000);
        Cache a1 = mgr.getCache("A");
        Cache a2 = mgr.getCache("A");
        Cache b = mgr.getCache("B");
        assertThat(a1).isInstanceOf(CaffeineCache.class);
        assertThat(a1).isSameAs(a2);
        assertThat(b).isNotSameAs(a1);
        assertThat(mgr.getCache("")).isSameAs(global); // 空 category 走 globalCache
    }

    @Test
    void createsMultiCaffeineCacheWithNextLevel() {
        Cache<Object> global = new CaffeineCache<>(10, 30_000);
        MultiCache<Object> l2 = new MultiCaffeineCache<>(50, 30_000, null);
        MultiCaffeineCacheManager mgr = new MultiCaffeineCacheManager(global, l2, 50, 30_000);
        Cache c = mgr.getCache("A");
        assertThat(c).isInstanceOf(MultiCaffeineCache.class);
        assertThat(((MultiCaffeineCache<?>) c).nextLevelCache()).isSameAs(l2);
    }
}
