package com.lezai.threadpool.storage.cache;

import java.time.Duration;

public interface Cache<K, V> {

    Object NULL_MARKER = new Object();

    @SuppressWarnings("unchecked")
    default V get(K key) {
        Object raw = rawGet(key);
        return raw == null || raw == NULL_MARKER ? null : (V) raw;
    }

    default void put(K key, V value) {
        rawPut(key, value != null ? value : nullWrapper());
    }

    default void put(K key, V value, Duration ttl) {
        rawPut(key, value != null ? value : nullWrapper(), ttl);
    }

    @SuppressWarnings("unchecked")
    default V putIfAbsent(K key, V value) {
        Object raw = rawPutIfAbsent(key, value != null ? value : nullWrapper());
        return raw == null || raw == NULL_MARKER ? null : (V) raw;
    }

    @SuppressWarnings("unchecked")
    private V nullWrapper() {
        return (V) NULL_MARKER;
    }

    Object rawGet(K key);

    void rawPut(K key, V value);

    void rawPut(K key, V value, Duration ttl);

    Object rawPutIfAbsent(K key, V value);

    void remove(K key);

    boolean containsKey(K key);
}
