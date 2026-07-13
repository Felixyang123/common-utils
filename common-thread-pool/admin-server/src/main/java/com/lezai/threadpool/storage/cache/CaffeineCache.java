package com.lezai.threadpool.storage.cache;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Set;

@Slf4j
public class CaffeineCache<K, V> implements Cache<K, V> {

    private final com.github.benmanes.caffeine.cache.Cache<K, Object> delegate;

    public CaffeineCache(com.github.benmanes.caffeine.cache.Cache<K, Object> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object rawGet(K key) {
        return delegate.getIfPresent(key);
    }

    @Override
    public void rawPut(K key, V value) {
        delegate.put(key, value);
    }

    @Override
    public void rawPut(K key, V value, Duration ttl) {
        delegate.put(key, value);
    }

    @Override
    public Object rawPutIfAbsent(K key, V value) {
        return delegate.asMap().putIfAbsent(key, value);
    }

    @Override
    public void remove(K key) {
        delegate.invalidate(key);
    }

    @Override
    public boolean containsKey(K key) {
        return delegate.getIfPresent(key) != null;
    }

    @Override
    public int size() {
        return (int) delegate.estimatedSize();
    }

    @Override
    public void clear() {
        delegate.invalidateAll();
    }

    @Override
    public Set<K> keySet() {
        return delegate.asMap().keySet();
    }
}
