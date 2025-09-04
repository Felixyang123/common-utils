package com.lezai.samples.cache;

import com.lezai.lock.LockSupport;
import com.lezai.samples.cache.sync.CacheMessagePubSub;
import com.lezai.samples.cache.sync.CacheSyncMessage;

import java.util.List;

public interface MultiCache<T> extends EnhanceCache<T> {

    default T load(String key, long ttl, CacheLoader<T> loader) {
        MultiCache<T> nextLevelCache = nextLevelCache();
        return nextLevelCache != null ? nextLevelCache.loadAndCache(key, ttl, loader) : loader.load(key);
    }

    @Override
    default void set(String key, CacheWrapper<T> value) {
        EnhanceCache.super.set(key, value);
        cascadeUpdateThenPublish(key, value);
    }

    @Override
    default void set(String key, CacheWrapper<T> value, Long ttl) {
        EnhanceCache.super.set(key, value, ttl);
        cascadeUpdateThenPublish(key, value);
    }

    private void cascadeUpdateThenPublish(String key, CacheWrapper<T> value) {
        MultiCache<T> nextLevelCache = nextLevelCache();
        if (nextLevelCache != null) {
            nextLevelCache.set(key, value);
        } else {
            CacheMessagePubSub.getInstance().publish(new CacheSyncMessage(category(), key), null);
        }
    }

    @Override
    default void remove(String key) {
        delete(key);
        MultiCache<T> nextLevelCache = nextLevelCache();
        if (nextLevelCache != null) {
            nextLevelCache.remove(key);
        } else {
            CacheMessagePubSub.getInstance().publish(new CacheSyncMessage(category(), key), null);
        }
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
