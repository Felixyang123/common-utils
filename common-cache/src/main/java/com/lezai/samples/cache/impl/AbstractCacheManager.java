package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.Cache;
import com.lezai.samples.cache.core.CacheManager;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractCacheManager implements CacheManager {
    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();
    private final Cache globalCache;

    public AbstractCacheManager(Cache<Object> globalCache) {
        this.globalCache = globalCache;
    }

    @Override
    public Cache getCache(String category) {
        return StringUtils.isBlank(category) ? globalCache : cacheMap.computeIfAbsent(category, this::createCache);
    }
}
