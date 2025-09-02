package com.lezai.samples.cache;

public interface Cache<T> {

    void set(String key, T value);

    void set(String key, T value, long ttl);

    T get(String key);
}
