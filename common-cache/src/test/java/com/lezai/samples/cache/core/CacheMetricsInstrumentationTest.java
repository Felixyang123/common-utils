package com.lezai.samples.cache.core;

import com.lezai.samples.cache.impl.HashMapCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CacheMetricsInstrumentationTest {

    static class Recording implements CacheMetrics {
        final Map<String, AtomicInteger> c = new ConcurrentHashMap<>();
        private void inc(String k) { c.computeIfAbsent(k, x -> new AtomicInteger()).incrementAndGet(); }
        int get(String k) { return c.getOrDefault(k, new AtomicInteger()).get(); }
        @Override public void hit() { inc("hit"); }
        @Override public void miss() { inc("miss"); }
        @Override public void staleServed() { inc("staleServed"); }
        @Override public void recordLoad(long nanos) { inc("load"); }
        @Override public void guardRejected(String reason) { inc("reject:" + reason); }
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
    void freshHit_recordsHit_noLoad() {
        Recording rec = new Recording();
        CacheMetricsHolder.init(rec);
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        HashMapCache<Object> cache = new HashMapCache<>(100);
        cache.innerSet("k", CacheWrapper.of("v", 60_000L));
        cache.loadAndCache("k", 60_000L, key -> "x");
        assertThat(rec.get("hit")).isEqualTo(1);
        assertThat(rec.get("miss")).isEqualTo(0);
        assertThat(rec.get("load")).isEqualTo(0);
    }

    @Test
    void miss_recordsMissAndLoad() {
        Recording rec = new Recording();
        CacheMetricsHolder.init(rec);
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        HashMapCache<Object> cache = new HashMapCache<>(100);
        cache.loadAndCache("k", 60_000L, key -> "v");
        assertThat(rec.get("miss")).isEqualTo(1);
        assertThat(rec.get("load")).isEqualTo(1);
    }

    @Test
    void guardReject_recordsReason_andStaleServed() {
        Recording rec = new Recording();
        CacheMetricsHolder.init(rec);
        CacheDegradationSupport.init(DegradationGuard.of(1_000_000, 0, 10), 1000); // bulkhead 必拒
        HashMapCache<Object> cache = new HashMapCache<>(100);
        cache.innerSet("k", CacheWrapper.of("stale", -1L));
        Object v = cache.loadAndCache("k", 60_000L, key -> {
            throw new AssertionError("no DB");
        });
        assertThat(v).isEqualTo("stale");
        assertThat(rec.get("reject:bulkhead")).isGreaterThanOrEqualTo(1);
        assertThat(rec.get("staleServed")).isEqualTo(1);
    }
}
