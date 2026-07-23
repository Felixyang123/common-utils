package com.lezai.samples.cache.core;

import com.lezai.samples.cache.impl.HashMapCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheLoadAndCacheTest {

    @AfterEach
    void reset() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
    }

    @Test
    void miss_loadsAndCaches() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        HashMapCache<Object> cache = new HashMapCache<>(100);
        Object v = cache.loadAndCache("k", 60_000L, key -> "v");
        assertThat(v).isEqualTo("v");
        assertThat(cache.innerGet("k").getData()).isEqualTo("v");
    }

    @Test
    void expiredReload_cachesNewValue_H1() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        HashMapCache<Object> cache = new HashMapCache<>(100);
        cache.innerSet("k", CacheWrapper.of("old", -1L)); // 已过期
        Object v = cache.loadAndCache("k", 60_000L, key -> "new");
        assertThat(v).isEqualTo("new");
        // H1 关键断言：缓存里必须是新值，旧实现会写回 "old"
        assertThat(cache.innerGet("k").getData()).isEqualTo("new");
    }

    @Test
    void concurrentMiss_loadsOnce() throws Exception {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        HashMapCache<Object> cache = new HashMapCache<>(100);
        AtomicInteger loads = new AtomicInteger();
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        cache.loadAndCache("k", 60_000L, key -> {
                            loads.incrementAndGet();
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return "v";
                        });
                    } catch (Exception ignored) {
                    }
                });
            }
            ready.await();
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void guardReject_withStale_servesStale() {
        // bulkhead=0 → 总是拒绝
        CacheDegradationSupport.init(DegradationGuard.of(1_000_000, 0, 10), 1000);
        HashMapCache<Object> cache = new HashMapCache<>(100);
        cache.innerSet("k", CacheWrapper.of("stale", -1L)); // 过期旧值
        Object v = cache.loadAndCache("k", 60_000L, key -> {
            throw new AssertionError("should not hit DB when guard rejects");
        });
        assertThat(v).isEqualTo("stale");
    }

    @Test
    void guardReject_withoutStale_throws() {
        CacheDegradationSupport.init(DegradationGuard.of(1_000_000, 0, 10), 1000);
        HashMapCache<Object> cache = new HashMapCache<>(100);
        assertThatThrownBy(() -> cache.loadAndCache("missing", 60_000L, key -> "v"))
                .isInstanceOf(CacheDegradedException.class);
    }

    @Test
    void cachedNull_fresh_returnsNullWithoutReload_penetration() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        HashMapCache<Object> cache = new HashMapCache<>(100);
        cache.innerSet("k", CacheWrapper.of(null, 60_000L)); // 新鲜的缓存空值
        AtomicInteger loads = new AtomicInteger();
        Object v = cache.loadAndCache("k", 60_000L, key -> {
            loads.incrementAndGet();
            return "v";
        });
        assertThat(v).isNull();
        assertThat(loads.get()).isEqualTo(0);
    }
}