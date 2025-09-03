package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.EnhanceCache;
import com.lezai.samples.cache.MultiCache;

public class MultiHashMapCacheManager extends AbstractCacheManager {
    private final MultiCache<Object> multiCache;
    private final int localCacheSize;
    private final long localCacheTtl;

    public MultiHashMapCacheManager(EnhanceCache<Object> globalCache, MultiCache<Object> multiCache, int localCacheSize, long localCacheTtl) {
        super(globalCache);
        this.multiCache = multiCache;
        this.localCacheSize = localCacheSize;
        this.localCacheTtl = localCacheTtl;
    }

    @Override
    public EnhanceCache<Object> createCache(String category) {
        return new MultiHashMapCache<>(this.localCacheSize, this.localCacheTtl, this.multiCache, category);
    }
}
