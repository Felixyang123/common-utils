package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.MultiCache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiHashMapCache<T> extends HashMapCache<T> implements MultiCache<T> {
    private final MultiCache<T> multiCache;

    public MultiHashMapCache(int cacheSize, MultiCache<T> multiCache) {
        super(cacheSize);
        this.multiCache = multiCache;
        log.info("init MultiHashMapCache with size: {}", cacheSize);
    }

    @Override
    public MultiCache<T> nextLevelCache() {
        return multiCache;
    }

    @Override
    public void remove(String key) {
        MultiCache.super.remove(key);
        super.remove(key);
    }
}
