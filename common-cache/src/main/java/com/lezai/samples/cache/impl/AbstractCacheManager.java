package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.CacheManager;
import com.lezai.samples.cache.core.EnhanceCache;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractCacheManager implements CacheManager {
    private final ConcurrentMap<String, EnhanceCache> cacheMap = new ConcurrentHashMap<>();
    private final EnhanceCache globalCache;

    public AbstractCacheManager(EnhanceCache globalCache) {
        this.globalCache = globalCache;
    }

    @Override
    public EnhanceCache getCache(String category) {
        return StringUtils.isBlank(category) ? globalCache : cacheMap.computeIfAbsent(category, this::createCache);
    }
}
