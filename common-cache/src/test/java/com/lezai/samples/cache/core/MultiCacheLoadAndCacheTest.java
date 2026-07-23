package com.lezai.samples.cache.core;

import com.lezai.samples.cache.impl.MultiHashMapCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MultiCacheLoadAndCacheTest {

    @AfterEach
    void reset() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
    }

    @Test
    void chain_loadsOnceFromDb_andPopulatesBothLevels() {
        // bulkhead=1：若护栏被重复获取且未释放，第二次会超时失败，从而验证只获取一次
        CacheDegradationSupport.init(DegradationGuard.of(1_000_000, 1, 200), 5000);
        MultiHashMapCache<Object> l2 = new MultiHashMapCache<>(100, null);
        MultiHashMapCache<Object> l1 = new MultiHashMapCache<>(100, l2);
        AtomicInteger dbLoads = new AtomicInteger();

        Object v = l1.loadAndCache("k", 60_000L, key -> {
            dbLoads.incrementAndGet();
            return "v";
        });

        assertThat(v).isEqualTo("v");
        assertThat(dbLoads.get()).isEqualTo(1);
        assertThat(l1.innerGet("k").getData()).isEqualTo("v");
        assertThat(l2.innerGet("k").getData()).isEqualTo("v");
    }

    @Test
    void guardReject_servesL1Stale() {
        CacheDegradationSupport.init(DegradationGuard.of(1_000_000, 0, 10), 1000);
        MultiHashMapCache<Object> l2 = new MultiHashMapCache<>(100, null);
        MultiHashMapCache<Object> l1 = new MultiHashMapCache<>(100, l2);
        l1.innerSet("k", CacheWrapper.of("l1-stale", -1L)); // L1 过期旧值

        Object v = l1.loadAndCache("k", 60_000L, key -> {
            throw new AssertionError("should not hit DB when guard rejects");
        });

        assertThat(v).isEqualTo("l1-stale");
    }

    @Test
    void l2Hit_backfillsL1_withoutDb() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
        MultiHashMapCache<Object> l2 = new MultiHashMapCache<>(100, null);
        MultiHashMapCache<Object> l1 = new MultiHashMapCache<>(100, l2);
        l2.innerSet("k", CacheWrapper.of("from-l2", 60_000L)); // L2 新鲜命中
        AtomicInteger dbLoads = new AtomicInteger();

        Object v = l1.loadAndCache("k", 60_000L, key -> {
            dbLoads.incrementAndGet();
            return "db";
        });

        assertThat(v).isEqualTo("from-l2");
        assertThat(dbLoads.get()).isEqualTo(0);
        assertThat(l1.innerGet("k").getData()).isEqualTo("from-l2");
    }
}