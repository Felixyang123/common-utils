package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.CacheWrapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WrapperExpiryTest {

    @Test
    void nullExpireTime_neverExpires() {
        WrapperExpiry expiry = new WrapperExpiry(30_000);
        CacheWrapper<Object> w = CacheWrapper.of("v", null);
        long d = expiry.expireAfterCreate("k", w, System.nanoTime());
        assertThat(d).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void liveEntry_expiresAfterRemainingPlusGrace() {
        long grace = 30_000;
        WrapperExpiry expiry = new WrapperExpiry(grace);
        CacheWrapper<Object> w = CacheWrapper.of("v", 60_000L); // 还有约 60s 逻辑寿命
        long before = System.currentTimeMillis();
        long d = expiry.expireAfterCreate("k", w, System.nanoTime());
        long nanos = d;
        // 应约为 (60000 + 30000)ms = 90s，允许 2s 误差
        long ms = TimeUnit.NANOSECONDS.toMillis(nanos);
        long elapsed = System.currentTimeMillis() - before;
        assertThat(ms).isBetween(90_000 - elapsed - 2_000, 90_000L);
    }

    @Test
    void alreadyExpired_stillLivesForGrace() {
        long grace = 30_000;
        WrapperExpiry expiry = new WrapperExpiry(grace);
        CacheWrapper<Object> w = CacheWrapper.of("v", -1L); // 已逻辑过期
        long d = expiry.expireAfterCreate("k", w, System.nanoTime());
        long ms = TimeUnit.NANOSECONDS.toMillis(d);
        // 已过期也应保留约 grace 时长供 serve-stale
        assertThat(ms).isBetween(grace - 2_000, grace);
    }

    @Test
    void readDoesNotExtend() {
        WrapperExpiry expiry = new WrapperExpiry(30_000);
        CacheWrapper<Object> w = CacheWrapper.of("v", 60_000L);
        long current = TimeUnit.MILLISECONDS.toNanos(12_345);
        long d = expiry.expireAfterRead("k", w, System.nanoTime(), current);
        assertThat(d).isEqualTo(current);
    }
}
