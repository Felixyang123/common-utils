package com.lezai.samples.cache;

import com.lezai.lock.LockSupport;

public interface EnhanceCache<T> extends Cache<CacheWrapper<T>> {

    @Override
    default void set(String key, CacheWrapper<T> value) {
        if (value.getExpireTime() == null) {
            value.setExpireTime(ttl() + System.currentTimeMillis());
        }
        put(key, value);
    }

    @Override
    default void set(String key, CacheWrapper<T> value, Long ttl) {
        if (value.getExpireTime() == null) {
            ttl = ttl == null ? this.ttl() : ttl;
            value.setExpireTime(ttl + System.currentTimeMillis());
        }
        put(key, value);
    }

    @Override
    default void remove(String key) {
        delete(key);
    }

    @Override
    default String category() {
        return "";
    }

    void put(String key, CacheWrapper<T> value);

    void delete(String key);

    default T loadAndCache(String key, Long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> cacheWrapper = get(key);
        ttl = ttl == null ? ttl() : ttl;
        long expireTime = System.currentTimeMillis() + ttl;
        if (cacheWrapper == null) {
            return LockSupport.lockAndExecuteOnce("CACHE_REFRESH_LOCK_" + key, () -> {
                T data = loader.load(key);
                put(key, CacheWrapper.<T>builder().key(key).category(category()).data(data).expireTime(expireTime).build());
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
                    put(key, CacheWrapper.<T>builder().key(key).category(category()).data(newData).expireTime(expireTime).build());
                    return newData;
                } finally {
                    LockSupport.getLock().release("CACHE_REFRESH_LOCK_" + key);
                }
            }
        }
        return data;
    }
}
