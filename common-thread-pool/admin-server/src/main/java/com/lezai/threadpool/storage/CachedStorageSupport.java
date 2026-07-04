package com.lezai.threadpool.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ExecutorService warmupExecutor;

    protected CachedStorageSupport(ConcurrentMap<String, T> cache, String displayName) {
        this.cache = cache;
        this.warmupExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "storage-warmup-" + displayName);
            t.setDaemon(true);
            return t;
        });
        log.info("Initialized CachedStorageSupport [{}] with cache impl: {}", displayName, cache.getClass().getSimpleName());
    }

    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(this::loadAllFromDb, warmupExecutor)
                .whenComplete((v, t) -> gracefulShutdown());
    }

    public abstract void loadAllFromDb();

    // ==================== 缓存原语（原 ConcurrentMapStorage）====================

    public Optional<T> getFromCache(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    public void putToCache(String key, T value) {
        cache.put(key, value);
    }

    public void removeFromCache(String key) {
        cache.remove(key);
    }

    public boolean existsInCache(String key) {
        return cache.containsKey(key);
    }

    public int getCacheSize() {
        return cache.size();
    }

    public T compute(String key, BiFunction<String, T, T> remappingFunction) {
        return cache.compute(key, remappingFunction);
    }

    private void gracefulShutdown() {
        warmupExecutor.shutdown();
    }
}