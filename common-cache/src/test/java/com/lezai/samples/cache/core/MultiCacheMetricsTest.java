package com.lezai.samples.cache.core;

import com.lezai.samples.cache.impl.MultiHashMapCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MultiCacheMetricsTest {

    static class Rec implements CacheMetrics {
        final Map<String, AtomicInteger> c = new ConcurrentHashMap<>();
        private void inc(String k) { c.computeIfAbsent(k, x -> new AtomicInteger()).incrementAndGet(); }
        int get(String k) { return c.getOrDefault(k, new AtomicInteger()).get(); }
        @Override public void hit() { inc("hit"); }
        @Override public void miss() { inc("miss"); }
        @Override public void staleServed() { inc("staleServed"); }
        @Override public void recordLoad(long n) { inc("load"); }
        @Override public void guardRejected(String r) { inc("reject:" + r); }
        @Override public void singleFlightTimeout() { inc("sfTimeout"); }
        @Override public void syncPublished() { inc("pub"); }
        @Override public void syncReceived() { inc("recv"); }
        @Override public void syncError() { inc("err"); }
    }

    @AfterEach
    void reset() {
        CacheMetricsHolder.init(null);
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
    }

    @Test
    void freshHit_recordsHit() {
        Rec rec = new Rec();
        CacheMetricsHolder.init(rec);
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        MultiHashMapCache<Object> l2 = new MultiHashMapCache<>(100, null);
        MultiHashMapCache<Object> l1 = new MultiHashMapCache<>(100, l2);
        l1.innerSet("k", CacheWrapper.of("v", 60_000L));
        l1.loadAndCache("k", 60_000L, key -> "x");
        assertThat(rec.get("hit")).isEqualTo(1);
        assertThat(rec.get("miss")).isEqualTo(0);
    }

    @Test
    void miss_recordsMissAndLoad() {
        Rec rec = new Rec();
        CacheMetricsHolder.init(rec);
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        MultiHashMapCache<Object> l2 = new MultiHashMapCache<>(100, null);
        MultiHashMapCache<Object> l1 = new MultiHashMapCache<>(100, l2);
        l1.loadAndCache("k", 60_000L, key -> "v");
        assertThat(rec.get("miss")).isEqualTo(1);
        assertThat(rec.get("load")).isEqualTo(1); // 终端 load 记录
    }

    @Test
    void l2Hit_doesNotDoubleCountMetrics() {
        Rec rec = new Rec();
        CacheMetricsHolder.init(rec);
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        MultiHashMapCache<Object> l2 = new MultiHashMapCache<>(100, null);
        MultiHashMapCache<Object> l1 = new MultiHashMapCache<>(100, l2);
        l2.innerSet("k", CacheWrapper.of("v", 60_000L)); // L2 有数据
        l1.loadAndCache("k", 60_000L, key -> "db");
        // L1 miss -> L2 hit -> 不应记 load（没打 DB）
        assertThat(rec.get("miss")).isEqualTo(1);
        assertThat(rec.get("load")).isEqualTo(0);
        assertThat(rec.get("hit")).isEqualTo(0);
    }
}
