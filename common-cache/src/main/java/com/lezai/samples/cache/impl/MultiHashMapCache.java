package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.CacheWrapper;
import com.lezai.samples.cache.MultiCache;
import com.lezai.samples.cache.sync.CacheMessagePubSub;
import com.lezai.samples.cache.sync.CacheSyncMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiHashMapCache<T> extends HashMapCache<T> implements MultiCache<T> {
    private final MultiCache<T> multiCache;

    private final String category;

    public MultiHashMapCache(int cacheSize, MultiCache<T> multiCache, String category) {
        super(cacheSize);
        this.multiCache = multiCache;
        this.category = category;
        log.info("init MultiHashMapCache with size: {}", cacheSize);
    }

    @Override
    public MultiCache<T> nextLevelCache() {
        return multiCache;
    }

    @Override
    public void set(String key, CacheWrapper<T> value) {
        MultiCache.super.set(key, value);
        CacheMessagePubSub.getInstance().publish(new CacheSyncMessage(category, key, value.getExpireTime()), null);
    }

    @Override
    public void set(String key, CacheWrapper<T> value, Long ttl) {
        MultiCache.super.set(key, value, ttl);
        CacheMessagePubSub.getInstance().publish(new CacheSyncMessage(category, key, value.getExpireTime()), null);
    }

    @Override
    public void remove(String key) {
        MultiCache.super.remove(key);
        CacheMessagePubSub.getInstance().publish(new CacheSyncMessage(category, key, null), null);
    }
}
