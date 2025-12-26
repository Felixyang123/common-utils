package com.lezai.samples.cache;

import com.lezai.lock.LockSupport;

import java.util.List;
import java.util.Optional;

public interface MultiCache<T> extends EnhanceCache<T> {

    default T load(String key, long ttl, CacheLoader<T> loader) {
        MultiCache<T> nextLevelCache = nextLevelCache();
        return nextLevelCache != null ? nextLevelCache.loadAndCache(key, ttl, loader) : loader.load(key);
    }

    @Override
    default void set(String key, CacheWrapper<T> value) {
        EnhanceCache.super.set(key, value);
        Optional.ofNullable(nextLevelCache()).ifPresent(cache -> cache.set(key, value));
    }

    @Override
    default void set(String key, CacheWrapper<T> value, Long ttl) {
        EnhanceCache.super.set(key, value, ttl);
        Optional.ofNullable(nextLevelCache()).ifPresent(cache -> cache.set(key, value));
    }

    @Override
    default void remove(String key) {
        delete(key);
        Optional.ofNullable(nextLevelCache()).ifPresent(cache -> cache.remove(key));
    }

    MultiCache<T> nextLevelCache();

    default Iterable<T> loadBatch(List<String> keys) {
        throw new UnsupportedOperationException();
    }

    @Override
    default T loadAndCache(String key, Long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> cacheWrapper = get(key);
        String lockKey = "MULTI_CACHE_LOCK_" + key;
        long curTtl = ttl == null ? ttl() : ttl;
        long expireTime = System.currentTimeMillis() + curTtl;
        if (cacheWrapper == null) {
            return LockSupport.lockAndExecuteQueued(lockKey, () -> {
                CacheWrapper<T> newCacheWrapper = get(key);
                if (newCacheWrapper == null) {
                    T data = load(key, curTtl, loader);
                    put(key, CacheWrapper.<T>builder().key(key).category(category()).data(data).expireTime(expireTime).build());
                    return data;
                }
                return newCacheWrapper.getData();
            });
        }

        if (cacheWrapper.expired()) {
            return LockSupport.lockAndExecuteOnce(lockKey, () -> {
                CacheWrapper<T> newCacheWrapper = get(key);
                if (newCacheWrapper == null) {
                    T data = load(key, curTtl, loader);
                    put(key, CacheWrapper.<T>builder().data(data).expireTime(expireTime).build());
                    return data;
                }

                if (newCacheWrapper.expired()) {
                    T data = load(key, curTtl, loader);
                    put(key, CacheWrapper.<T>builder().key(key).category(category()).data(data).expireTime(expireTime).build());
                    return data;
                }
                return newCacheWrapper.getData();
            });
        }
        return cacheWrapper.getData();
    }
}
