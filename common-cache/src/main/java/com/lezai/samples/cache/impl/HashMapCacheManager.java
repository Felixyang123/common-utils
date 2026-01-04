package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.Cache;

public class HashMapCacheManager extends AbstractCacheManager {
    private final int cacheSize;

    public HashMapCacheManager(Cache globalCache, int cacheSize) {
        super(globalCache);
        this.cacheSize = cacheSize;
    }

    @Override
    public Cache createCache(String category) {
        return new HashMapCache<>(this.cacheSize);
    }
}
