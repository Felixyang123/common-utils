package com.lezai.threadpool.storage.cache;

public interface CacheService {

    <K, V> Cache<K, V> getCache(String name);
}
