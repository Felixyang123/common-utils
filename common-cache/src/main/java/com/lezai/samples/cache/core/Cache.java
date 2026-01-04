package com.lezai.samples.cache.core;

import com.lezai.lock.LockSupport;

import java.util.Optional;

public interface Cache<T> {

    default void set(String key, T value) {
        innerSet(key, CacheWrapper.of(value, null));
    }

    default void set(String key, T value, Long ttl) {
        innerSet(key, CacheWrapper.of(value, ttl));
    }

    default T get(String key) {
        return Optional.ofNullable(innerGet(key)).map(CacheWrapper::getData).orElse(null);
    }

    void remove(String key);

    void innerSet(String key, CacheWrapper<T> cacheWrapper);

    CacheWrapper<T> innerGet(String key);

    default T loadAndCache(String key, Long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> cacheWrapper = innerGet(key);
        if (cacheWrapper == null) {
            // 加锁预防缓存击穿
            return LockSupport.lockAndExecuteOnce("CACHE_REFRESH_LOCK_" + key, () -> {
                T data = loader.load(key);
                set(key, data, ttl);
                return data;
            });
        }

        // 预防缓存穿透
        T data = cacheWrapper.getData();
        if (data == null) {
            return null;
        }

        if (cacheWrapper.expired()) {
            if (LockSupport.getLock().tryLock("CACHE_REFRESH_LOCK_" + key, 60000)) {
                try {
                    T newData = loader.load(key);
                    set(key, data, ttl);
                    return newData;
                } finally {
                    LockSupport.getLock().release("CACHE_REFRESH_LOCK_" + key);
                }
            }
        }
        return data;
    }

}
