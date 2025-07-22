package com.lezai.common_utils.cache;

@FunctionalInterface
public interface CacheLoader<T> {
    T load(String key);
}
