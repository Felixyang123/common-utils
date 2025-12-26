package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.CacheWrapper;
import com.lezai.samples.cache.MultiCache;
import com.lezai.samples.cache.sync.CacheMessagePubSub;
import com.lezai.samples.cache.sync.CacheSyncMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiHashMapCache<T> extends HashMapCache<T> implements MultiCache<T> {
    private final MultiCache<T> multiCache;

    public MultiHashMapCache(int cacheSize, long ttl, MultiCache<T> multiCache, String category) {
        super(cacheSize, ttl, category);
        this.multiCache = multiCache;
        log.info("init MultiHashMapCache with size: {}, ttl: {}", cacheSize, ttl);
    }

    @Override
    public MultiCache<T> nextLevelCache() {
        return multiCache;
    }

    @Override
    public void set(String key, CacheWrapper<T> value) {
        MultiCache.super.set(key, value);
        CacheMessagePubSub.getInstance().publish(new CacheSyncMessage(category(), key), null);
    }

    @Override
    public void set(String key, CacheWrapper<T> value, Long ttl) {
        MultiCache.super.set(key, value, ttl);
        CacheMessagePubSub.getInstance().publish(new CacheSyncMessage(category(), key), null);
    }

    @Override
    public void remove(String key) {
        MultiCache.super.remove(key);
        CacheMessagePubSub.getInstance().publish(new CacheSyncMessage(category(), key), null);
    }
}
