package com.lezai.threadpool.storage.cache;

import org.redisson.api.RMapCache;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RedissonCache<K, V> implements Cache<K, V> {

    private final RMapCache<K, Object> delegate;
    private final long defaultTtlMs;

    public RedissonCache(RMapCache<K, Object> delegate, Duration defaultTtl) {
        this.delegate = delegate;
        this.defaultTtlMs = defaultTtl != null ? defaultTtl.toMillis() : 0;
    }

    @Override
    public Object rawGet(K key) {
        return delegate.get(key);
    }

    @Override
    public void rawPut(K key, V value) {
        if (defaultTtlMs > 0) {
            delegate.put(key, value, defaultTtlMs, TimeUnit.MILLISECONDS);
        } else {
            delegate.put(key, value);
        }
    }

    @Override
    public void rawPut(K key, V value, Duration ttl) {
        long ttlMs = ttl != null ? ttl.toMillis() : defaultTtlMs;
        if (ttlMs > 0) {
            delegate.put(key, value, ttlMs, TimeUnit.MILLISECONDS);
        } else {
            delegate.put(key, value);
        }
    }

    @Override
    public Object rawPutIfAbsent(K key, V value) {
        if (defaultTtlMs > 0) {
            return delegate.putIfAbsent(key, value, defaultTtlMs, TimeUnit.MILLISECONDS);
        }
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public void remove(K key) {
        delegate.fastRemove(key);
    }

    @Override
    public boolean containsKey(K key) {
        return delegate.containsKey(key);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }
}
