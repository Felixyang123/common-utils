package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.storage.CacheService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * local profile 的 {@link CacheService}：纯进程内 {@link ConcurrentHashMap}，无任何外部依赖。
 */
@Slf4j
public class LocalCacheService implements CacheService {

    @Override
    public <K, V> ConcurrentMap<K, V> getMap(String name) {
        log.debug("LocalCacheService: creating in-process ConcurrentHashMap for [{}]", name);
        return new ConcurrentHashMap<>();
    }
}