package com.lezai.samples.cache.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lezai.samples.cache.core.CacheLoader;
import com.lezai.samples.cache.core.CacheWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CaffeineCache<T> implements com.lezai.samples.cache.core.Cache<T> {
    private final Cache<String, CacheWrapper<T>> cache;

    public CaffeineCache(int cacheSize, long expireAfterAccess) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterAccess(expireAfterAccess, TimeUnit.MILLISECONDS)
                .build();
        log.info("CaffeineCache init, cacheSize: {}, expireAfterAccess: {}", cacheSize, expireAfterAccess);
    }

    @Override
    public void remove(String key) {
        cache.invalidate(key);
    }

    @Override
    public void innerSet(String key, CacheWrapper<T> cacheWrapper) {
        cache.put(key, cacheWrapper);
    }

    @Override
    public CacheWrapper<T> innerGet(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public T loadAndCache(String key, Long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> cacheWrapper = cache.get(key, k -> CacheWrapper.of(loader.load(k), ttl));
        return Optional.ofNullable(cacheWrapper).map(CacheWrapper::getData).orElse(null);
    }
}
