package com.lezai.threadpool.storage;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

/**
 * 缓存 + 持久化存储支持基类。
 * <p>
 * 将 {@link ConcurrentMap}（由 {@link CacheService} 提供）作为运行时缓存，
 * 子类负责实现 cache miss 时的 DB 加载与刷新语义。
 * <p>
 * 不再关心 cache 是进程内 {@code ConcurrentHashMap}（local profile）还是 Redisson
 * {@code RMap}（db profile）—— storage 逻辑对二者透明，仅依赖 {@link ConcurrentMap} 契约。
 *
 * @param <T> 存储实体类型
 */
@Slf4j
public abstract class CachedStorageSupport<T> {

    protected final ConcurrentMap<String, T> cache;

    protected CachedStorageSupport(ConcurrentMap<String, T> cache, String displayName) {
        this.cache = cache;
        log.info("Initialized CachedStorageSupport [{}] with cache impl: {}", displayName, cache.getClass().getSimpleName());
    }
    // ==================== 缓存原语（原 ConcurrentMapStorage）====================

    public Optional<T> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    public void put(String key, T value) {
        cache.put(key, value);
    }

    public void remove(String key) {
        cache.remove(key);
    }

    public boolean exists(String key) {
        return cache.containsKey(key);
    }

    public int getSize() {
        return cache.size();
    }

    public T compute(String key, BiFunction<String, T, T> remappingFunction) {
        return cache.compute(key, remappingFunction);
    }
}