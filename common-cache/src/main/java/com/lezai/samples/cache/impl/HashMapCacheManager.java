package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.EnhanceCache;

public class HashMapCacheManager extends AbstractCacheManager {
    private final int cacheSize;
    private final long ttl;

    public HashMapCacheManager(EnhanceCache<Object> globalCache, int cacheSize, long ttl) {
        super(globalCache);
        this.cacheSize = cacheSize;
        this.ttl = ttl;
    }

    @Override
    public EnhanceCache<Object> createCache(String category) {
        return new HashMapCache<>(this.cacheSize, this.ttl, category);
    }
}
