package com.lezai.samples.cache.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lezai.samples.cache.CacheLoader;
import com.lezai.samples.cache.CacheWrapper;
import com.lezai.samples.cache.EnhanceCache;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class CaffeineCache<T> implements EnhanceCache<T> {
    private Cache<String, CacheWrapper<T>> cache;

    public CaffeineCache(int cacheSize, long expireAfterAccess) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterAccess(expireAfterAccess, TimeUnit.SECONDS)
                .build();
        log.info("CaffeineCache init, cacheSize: {}, expireAfterAccess: {}", cacheSize, expireAfterAccess);
    }

    @Override
    public void set(String key, CacheWrapper<T> value) {
        cache.put(key, value);
    }

    @Override
    public void set(String key, CacheWrapper<T> value, long ttl) {
        value.setExpireTime(ttl);
        cache.put(key, value);
    }

    @Override
    public CacheWrapper<T> get(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public T loadAndCache(String key, long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> cacheWrapper = cache.get(key, k -> new CacheWrapper<>(loader.load(k), ttl));
        return cacheWrapper.getData();
    }
}
