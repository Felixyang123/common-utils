package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.MultiCache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiHashMapCache<T> extends HashMapCache<T> implements MultiCache<T> {
    private final MultiCache<T> multiCache;

    public MultiHashMapCache(int cacheSize, long ttl, MultiCache<T> multiCache, String category) {
        super(cacheSize, ttl, category);
        this.multiCache = multiCache;
        log.info("init MultiHashMapCache with size: {}, ttl: {}", cacheSize, ttl);
    }

    @Override
    public MultiCache<T> nextLevelCache() {
        return multiCache;
    }
}
