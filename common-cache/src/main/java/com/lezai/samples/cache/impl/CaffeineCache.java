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
    private final Cache<String, CacheWrapper<T>> cache;
    private final long ttl;
    private final String category;

    public CaffeineCache(int cacheSize, long expireAfterAccess, String category) {
        this.ttl = expireAfterAccess;
        this.category = category;
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterAccess(expireAfterAccess, TimeUnit.MILLISECONDS)
                .build();
        log.info("CaffeineCache init, cacheSize: {}, expireAfterAccess: {}", cacheSize, expireAfterAccess);
    }

    @Override
    public Long ttl() {
        return this.ttl;
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
    public String category() {
        return this.category;
    }

    @Override
    public T loadAndCache(String key, long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> cacheWrapper = cache.get(key, k -> new CacheWrapper<>(key, category, loader.load(k), ttl));
        return cacheWrapper.getData();
    }
}
