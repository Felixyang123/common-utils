package com.lezai.cache;

import com.lezai.lock.LockSupport;

public interface EnhanceCache<T> extends Cache<CacheWrapper<T>> {

    default T loadAndCache(String key, long ttl, CacheLoader<T> loader) {
        CacheWrapper<T> cacheWrapper = get(key);
        if (cacheWrapper == null) {
            return LockSupport.lockAndExecuteOnce("CACHE_REFRESH_LOCK_" + key, () -> {
                T data = loader.load(key);
                set(key, CacheWrapper.<T>builder().data(data).expireTime(ttl).build());
                return data;
            });
        }

        T data = cacheWrapper.getData();
        if (data == null) {
            return null;
        }

        if (cacheWrapper.expired()) {
            if (LockSupport.getLock().tryLock("CACHE_REFRESH_LOCK_" + key)) {
                try {
                    T newData = loader.load(key);
                    cacheWrapper = CacheWrapper.<T>builder().data(newData).expireTime(ttl).build();
                    set(key, cacheWrapper);
                    return newData;
                } finally {
                    LockSupport.getLock().release("CACHE_REFRESH_LOCK_" + key);
                }
            }
        }
        return data;
    }
}
