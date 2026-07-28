package com.lezai.samples.cache.impl;

import com.github.benmanes.caffeine.cache.Expiry;
import com.lezai.samples.cache.core.CacheWrapper;

import java.util.concurrent.TimeUnit;

/**
 * 按 CacheWrapper.expireTime 驱逐的 Caffeine Expiry。
 * 物理驱逐时间 = 逻辑过期时间 + staleGraceMs：逻辑过期（expired()）先行，
 * 过期旧值在 grace 窗口内仍驻留，供降级 serve-stale 使用；读操作不延长寿命（避免热 key 永不过期）。
 */
public class WrapperExpiry implements Expiry<String, CacheWrapper<?>> {

    private final long staleGraceMs;

    public WrapperExpiry(long staleGraceMs) {
        this.staleGraceMs = staleGraceMs;
    }

    @Override
    public long expireAfterCreate(String key, CacheWrapper<?> value, long currentTime) {
        Long expireTime = value.getExpireTime();
        if (expireTime == null) {
            return Long.MAX_VALUE;
        }
        long remainingMs = Math.max(expireTime - System.currentTimeMillis(), 0);
        return TimeUnit.MILLISECONDS.toNanos(remainingMs + staleGraceMs);
    }

    @Override
    public long expireAfterUpdate(String key, CacheWrapper<?> value, long currentTime, long currentDuration) {
        return expireAfterCreate(key, value, currentTime);
    }

    @Override
    public long expireAfterRead(String key, CacheWrapper<?> value, long currentTime, long currentDuration) {
        return currentDuration; // 读不延长
    }
}
