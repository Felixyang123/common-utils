package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.Cache;
import com.lezai.samples.cache.core.MultiCache;

public class MultiCaffeineCacheManager extends AbstractCacheManager {
    private final MultiCache<Object> nextLevel;
    private final int cacheSize;
    private final long staleGraceMs;

    public MultiCaffeineCacheManager(Cache<Object> globalCache, MultiCache<Object> nextLevel, int cacheSize, long staleGraceMs) {
        super(globalCache);
        this.nextLevel = nextLevel;
        this.cacheSize = cacheSize;
        this.staleGraceMs = staleGraceMs;
    }

    @Override
    public Cache createCache(String category) {
        return new MultiCaffeineCache<>(this.cacheSize, this.staleGraceMs, this.nextLevel);
    }
}
