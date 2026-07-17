package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.Cache;
import com.lezai.samples.cache.core.CacheLoader;
import com.lezai.samples.cache.core.CacheWrapper;
import com.lezai.samples.cache.sync.CacheMessagePubSub;
import com.lezai.samples.cache.sync.CacheNodeRegisterInfo;
import com.lezai.samples.cache.sync.CacheSyncMessageImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class DistributeCache<T> implements Cache<T> {

    private final Cache<T> delegate;
    private final String category;
    private final CacheMessagePubSub pubSub;
    private final String nodeAddress;

    @Override
    public void set(String key, T value) {
        registerAndExecute(key, () -> {
            delegate.set(key, value);
            pubSub.publish(new CacheSyncMessageImpl(category, key, null));
            return null;
        });
    }

    @Override
    public void set(String key, T value, Long ttl) {
        registerAndExecute(key, () -> {
            delegate.set(key, value, ttl);
            pubSub.publish(new CacheSyncMessageImpl(category, key, ttl));
            return null;
        });
    }

    @Override
    public T get(String key) {
        return delegate.get(key);
    }

    @Override
    public void remove(String key) {
        delegate.remove(key);
        pubSub.publish(new CacheSyncMessageImpl(category, key, null));
    }

    @Override
    public void innerSet(String key, CacheWrapper<T> cacheWrapper) {
        registerAndExecute(key, () -> {
            delegate.innerSet(key, cacheWrapper);
            return null;
        });
    }

    @Override
    public CacheWrapper<T> innerGet(String key) {
        return delegate.innerGet(key);
    }

    @Override
    public T loadAndCache(String key, Long ttl, CacheLoader<T> loader) {
        return registerAndExecute(key, () -> delegate.loadAndCache(key, ttl, loader));
    }

    private T registerAndExecute(String key, Supplier<T> executor) {
        T result = executor.get();
        pubSub.register(new CacheNodeRegisterInfo(key, nodeAddress));
        return result;
    }
}
