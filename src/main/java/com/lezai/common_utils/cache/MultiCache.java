package com.lezai.common_utils.cache;

import com.lezai.common_utils.lock.LockSupport;

import java.util.List;

public interface MultiCache<T> extends EnhanceCache<T> {

    T load(String key, long ttl, CacheLoader<T> loader);

    Iterable<T> loadBatch(List<String> keys);

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
