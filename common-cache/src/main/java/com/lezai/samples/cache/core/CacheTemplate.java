package com.lezai.samples.cache.core;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CacheTemplate {

    private final CacheManager cacheManager;

    public <T> T get(String category, String key) {
        Cache<T> cache = cacheManager.getCache(category);
        return cache.get(buildKey(category, key));
    }

    public void set(String category, String key, Object value) {
        cacheManager.getCache(category).set(buildKey(category, key), CacheWrapper.builder().data(value).build());
    }

    public void set(String category, String key, Object value, Long ttl) {
        cacheManager.getCache(category).set(buildKey(category, key), CacheWrapper.builder().data(value).build(), ttl);
    }

    public <T> T get(String category, String key, Long ttl, CacheLoader<T> loader) {
        Cache<T> cache = cacheManager.getCache(category);
        return cache.loadAndCache(buildKey(category, key), ttl, loader);
    }

    public void remove(String category, String key) {
        cacheManager.getCache(category).remove(buildKey(category, key));
    }

    private String buildKey(String category, String key) {
        return category + ":" + key;
    }
}
