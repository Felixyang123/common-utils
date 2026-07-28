package com.lezai.samples.cache.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CacheTemplate {

    private final CacheManager cacheManager;

    public <T> T get(String category, String key) {
        Cache<T> cache = cacheManager.getCache(category);
        T value = cache.get(buildKey(category, key));
        log.debug("Cache get: category={}, key={}, hit={}", category, key, value != null);
        return value;
    }

    public void set(String category, String key, Object value) {
        cacheManager.getCache(category).set(buildKey(category, key), value);
        log.debug("Cache set: category={}, key={}", category, key);
    }

    public void set(String category, String key, Object value, Long ttl) {
        cacheManager.getCache(category).set(buildKey(category, key), value, ttl);
        log.debug("Cache set: category={}, key={}, ttl={}ms", category, key, ttl);
    }

    public <T> T get(String category, String key, Long ttl, CacheLoader<T> loader) {
        Cache<T> cache = cacheManager.getCache(category);
        return cache.loadAndCache(buildKey(category, key), ttl, loader);
    }

    public void remove(String category, String key) {
        cacheManager.getCache(category).remove(buildKey(category, key));
        log.debug("Cache remove: category={}, key={}", category, key);
    }

    private String buildKey(String category, String key) {
        return category + ":" + key;
    }
}
