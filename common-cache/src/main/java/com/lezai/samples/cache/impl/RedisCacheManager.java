package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.CacheManager;
import com.lezai.samples.cache.core.EnhanceCache;

public record RedisCacheManager(RemoteRedisCache redisCache) implements CacheManager {

    @Override
    public EnhanceCache createCache(String category) {
        return this.redisCache;
    }

    @Override
    public EnhanceCache getCache(String category) {
        return this.redisCache;
    }
}
