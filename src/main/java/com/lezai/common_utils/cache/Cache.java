package com.lezai.common_utils.cache;

public interface Cache<T> {

    void set(String key, T value);

    void set(String key, T value, long ttl);

    T get(String key);
}
