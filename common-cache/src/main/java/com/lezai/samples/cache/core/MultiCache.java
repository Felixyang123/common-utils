package com.lezai.samples.cache.core;

import com.lezai.lock.LockSupport;

import java.util.Optional;

public interface MultiCache<T> extends Cache<T> {

    default T load(String key, Long ttl, CacheLoader<T> loader) {
        MultiCache<T> nextLevelCache = nextLevelCache();
        return nextLevelCache != null ? nextLevelCache.loadAndCache(key, ttl, loader) : loader.load(key);
    }

    @Override
    default void set(String key, T value) {
        Optional.ofNullable(nextLevelCache()).ifPresent(cache -> cache.set(key, value));
        Cache.super.set(key, value);
    }

    @Override
    default void set(String key, T value, Long ttl) {
        Optional.ofNullable(nextLevelCache()).ifPresent(cache -> cache.set(key, value, ttl));
        Cache.super.set(key, value, ttl);
    }

    @Override
    default void remove(String key) {
        Optional.ofNullable(nextLevelCache()).ifPresent(cache -> cache.remove(key));
    }

    MultiCache<T> nextLevelCache();

    @Override
    default T loadAndCache(String key, Long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> cacheWrapper = innerGet(key);
        String lockKey = "MULTI_CACHE_LOCK_" + key;
        if (cacheWrapper == null) {
            return LockSupport.lockAndExecuteQueued(lockKey, () -> {
                CacheWrapper<T> newCacheWrapper = innerGet(key);
                if (newCacheWrapper == null) {
                    T data = load(key, ttl, loader);
                    innerSet(key, CacheWrapper.of(data, ttl));
                    return data;
                }
                return newCacheWrapper.getData();
            });
        }

        if (cacheWrapper.expired()) {
            return LockSupport.lockAndExecuteOnce(lockKey, () -> {
                CacheWrapper<T> newCacheWrapper = innerGet(key);
                if (newCacheWrapper == null) {
                    T data = load(key, ttl, loader);
                    innerSet(key, CacheWrapper.of(data, ttl));
                    return data;
                }

                if (newCacheWrapper.expired()) {
                    T data = load(key, ttl, loader);
                    innerSet(key, CacheWrapper.of(data, ttl));
                    return data;
                }
                return newCacheWrapper.getData();
            });
        }
        return cacheWrapper.getData();
    }
}
