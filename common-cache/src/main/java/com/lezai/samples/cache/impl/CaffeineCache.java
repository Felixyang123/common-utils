package com.lezai.samples.cache.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lezai.samples.cache.CacheLoader;
import com.lezai.samples.cache.CacheWrapper;
import com.lezai.samples.cache.EnhanceCache;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CaffeineCache<T> implements EnhanceCache<T> {
    private final Cache<String, CacheWrapper<T>> cache;
    private final long ttl;

    public CaffeineCache(int cacheSize, long expireAfterAccess) {
        this.ttl = expireAfterAccess;
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterAccess(expireAfterAccess, TimeUnit.MILLISECONDS)
                .build();
        log.info("CaffeineCache init, cacheSize: {}, expireAfterAccess: {}", cacheSize, expireAfterAccess);
    }

    @Override
    public void put(String key, CacheWrapper<T> value) {
        cache.put(key, value);
    }

    @Override
    public CacheWrapper<T> get(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void delete(String key) {
        cache.invalidate(key);
    }

    @Override
    public T loadAndCache(String key, Long ttl, CacheLoader<T> loader) {
        Long expireTime = Optional.ofNullable(ttl).map(t -> t + System.currentTimeMillis()).orElse(null);
        CacheWrapper<T> cacheWrapper = cache.get(key, k -> new CacheWrapper<>(loader.load(k), expireTime));
        return cacheWrapper.getData();
    }
}
