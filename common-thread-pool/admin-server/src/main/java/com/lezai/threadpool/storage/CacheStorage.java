package com.lezai.threadpool.storage;

import java.util.Optional;

public interface CacheStorage<T> {

    Optional<T> getFromCache(String key);

    void putToCache(String key, T value);

    void removeFromCache(String key);

    boolean existsInCache(String key);

    int getCacheSize();
}
