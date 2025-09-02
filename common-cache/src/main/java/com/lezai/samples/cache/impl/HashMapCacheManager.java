package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.EnhanceCache;

public class HashMapCacheManager extends AbstractCacheManager {
    private int cacheSize;

    public HashMapCacheManager(EnhanceCache<Object> globalCache, int cacheSize) {
        super(globalCache);
        this.cacheSize = cacheSize;
    }

    @Override
    public EnhanceCache<Object> createCache(String category) {
        return new HashMapCache<>(this.cacheSize);
    }
}
