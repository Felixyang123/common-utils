package com.lezai.samples.cache.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lezai.samples.cache.core.CacheWrapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 有界 Caffeine 缓存。loadAndCache 继承 Cache 默认实现（单飞 + 护栏 + serve-stale + 逻辑过期刷新），
 * 故不再覆盖；Caffeine 仅承担容量上限与按 WrapperExpiry 的物理驱逐。
 */
@Slf4j
public class CaffeineCache<T> implements com.lezai.samples.cache.core.Cache<T> {
    private final Cache<String, CacheWrapper<T>> cache;

    public CaffeineCache(int cacheSize, long staleGraceMs) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfter(new WrapperExpiry(staleGraceMs))
                .build();
        log.info("CaffeineCache init, cacheSize: {}, staleGraceMs: {}", cacheSize, staleGraceMs);
    }

    @Override
    public void remove(String key) {
        cache.invalidate(key);
    }

    @Override
    public void innerSet(String key, CacheWrapper<T> cacheWrapper) {
        cache.put(key, cacheWrapper);
    }

    @Override
    public CacheWrapper<T> innerGet(String key) {
        return cache.getIfPresent(key);
    }
}
