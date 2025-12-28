package com.lezai.samples.cache.core;

import com.lezai.lock.LockSupport;

import java.util.Optional;

public interface EnhanceCache<T> extends Cache<CacheWrapper<T>> {

    @Override
    default void set(String key, CacheWrapper<T> value) {
        put(key, value);
    }

    @Override
    default void set(String key, CacheWrapper<T> value, Long ttl) {
        if (value.getExpireTime() == null && ttl != null) {
            value.setExpireTime(ttl + System.currentTimeMillis());
        }
        put(key, value);
    }

    @Override
    default void remove(String key) {
        delete(key);
    }

    void put(String key, CacheWrapper<T> value);

    void delete(String key);

    default T loadAndCache(String key, Long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> cacheWrapper = get(key);
        Long expireTime = Optional.ofNullable(ttl).map(t -> System.currentTimeMillis() + t).orElse(null);
        if (cacheWrapper == null) {
            return LockSupport.lockAndExecuteOnce("CACHE_REFRESH_LOCK_" + key, () -> {
                T data = loader.load(key);
                put(key, CacheWrapper.<T>builder().data(data).expireTime(expireTime).build());
                return data;
            });
        }

        T data = cacheWrapper.getData();
        if (data == null) {
            return null;
        }

        if (cacheWrapper.expired()) {
            if (LockSupport.getLock().tryLock("CACHE_REFRESH_LOCK_" + key, 60000)) {
                try {
                    T newData = loader.load(key);
                    put(key, CacheWrapper.<T>builder().data(newData).expireTime(expireTime).build());
                    return newData;
                } finally {
                    LockSupport.getLock().release("CACHE_REFRESH_LOCK_" + key);
                }
            }
        }
        return data;
    }
}
