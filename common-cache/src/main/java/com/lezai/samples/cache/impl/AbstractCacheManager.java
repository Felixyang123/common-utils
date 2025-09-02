package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.CacheManager;
import com.lezai.samples.cache.EnhanceCache;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractCacheManager implements CacheManager<Object> {
    private final ConcurrentMap<String, EnhanceCache<Object>> cacheMap = new ConcurrentHashMap<>();
    private final EnhanceCache<Object> globalCache;

    public AbstractCacheManager(EnhanceCache<Object> globalCache) {
        this.globalCache = globalCache;
    }

    @Override
    public EnhanceCache<Object> getCache(String category) {
        return StringUtils.isBlank(category) ? globalCache : cacheMap.computeIfAbsent(category, k -> createCache(category));
    }
}
