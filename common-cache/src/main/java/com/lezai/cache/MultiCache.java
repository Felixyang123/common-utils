package com.lezai.cache;

import com.lezai.lock.LockSupport;

import java.util.List;

public interface MultiCache<T> extends EnhanceCache<T> {

    default T load(String key, long ttl, CacheLoader<T> loader) {
        MultiCache<T> nextLevelCache = nextLevelCache();
        return nextLevelCache != null ? nextLevelCache.load(key, ttl, loader) : loader.load(key);
    }

    MultiCache<T> nextLevelCache();

    default Iterable<T> loadBatch(List<String> keys) {
        throw new UnsupportedOperationException();
    }

    @Override
    default T loadAndCache(String key, long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> cacheWrapper = get(key);
        String lockKey = "MULTI_CACHE_LOCK_" + key;
        if (cacheWrapper == null) {
            return LockSupport.lockAndExecuteQueued(lockKey, () -> {
                CacheWrapper<T> newCacheWrapper = get(key);
                if (newCacheWrapper == null) {
                    T data = load(key, ttl, loader);
                    set(key, CacheWrapper.<T>builder().data(data).expireTime(ttl).build());
                    return data;
                }
                return newCacheWrapper.getData();
            });
        }

        if (cacheWrapper.expired()) {
            return LockSupport.lockAndExecuteOnce(lockKey, () -> {
                CacheWrapper<T> newCacheWrapper = get(key);
                if (newCacheWrapper == null) {
                    T data = load(key, ttl, loader);
                    set(key, CacheWrapper.<T>builder().data(data).expireTime(ttl).build());
                    return data;
                }

                if (newCacheWrapper.expired()) {
                    T data = load(key, ttl, loader);
                    set(key, CacheWrapper.<T>builder().data(data).expireTime(ttl).build());
                    return data;
                }
                return newCacheWrapper.getData();
            });
        }
        return cacheWrapper.getData();
    }
}
