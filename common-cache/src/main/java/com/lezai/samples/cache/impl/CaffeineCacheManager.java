package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.Cache;

public class CaffeineCacheManager extends AbstractCacheManager {
    private final int cacheSize;
    private final long staleGraceMs;

    public CaffeineCacheManager(Cache<Object> globalCache, int cacheSize, long staleGraceMs) {
        super(globalCache);
        this.cacheSize = cacheSize;
        this.staleGraceMs = staleGraceMs;
    }

    @Override
    public Cache createCache(String category) {
        return new CaffeineCache<>(this.cacheSize, this.staleGraceMs);
    }
}
