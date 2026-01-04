package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.Cache;
import com.lezai.samples.cache.core.MultiCache;

public class MultiHashMapCacheManager extends AbstractCacheManager {
    private final MultiCache<Object> multiCache;
    private final int localCacheSize;

    public MultiHashMapCacheManager(Cache<Object> globalCache, MultiCache<Object> multiCache, int localCacheSize) {
        super(globalCache);
        this.multiCache = multiCache;
        this.localCacheSize = localCacheSize;
    }

    @Override
    public Cache createCache(String category) {
        return new MultiHashMapCache<>(this.localCacheSize, this.multiCache);
    }
}
