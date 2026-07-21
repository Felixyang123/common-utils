package com.lezai.threadpool.storage.cache;

import com.lezai.threadpool.util.SyncLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
public abstract class CachedStorageSupport<T> {

    protected final Cache<String, T> cache;
    protected final SyncLock syncLock;
    private final String cacheName;
    private final Duration nullValueTtl;

    protected CachedStorageSupport(Cache<String, T> cache, SyncLock syncLock, String cacheName, Duration nullValueTtl) {
        this.cache = cache;
        this.syncLock = syncLock;
        this.cacheName = cacheName;
        this.nullValueTtl = nullValueTtl;
    }

    public Optional<T> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    public void put(String key, T value) {
        cache.put(key, value);
    }

    public void remove(String key) {
        cache.remove(key);
    }

    /**
     * 注册一个在事务提交后执行的回调。
     * <p>
     * 若当前无事务同步激活（如 local 边界场景），则立即执行。
     * 回滚时不执行（afterCommit 仅在提交后触发），避免缓存写入脏数据或误通知订阅者。
     */
    protected void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * 事务提交后再写缓存。
     * <p>
     * 事务回滚时缓存不写入，避免缓存里留下 DB 中不存在的"幽灵"数据（见 ADR-0009）。
     */
    protected void putAfterCommit(String key, T value) {
        afterCommit(() -> cache.put(key, value));
    }

    /**
     * 事务提交后再清缓存。
     * <p>
     * 事务回滚时缓存不清理，避免其它并发读在事务提交前读到旧值并写缓存的窗口被误扩大（见 ADR-0009）。
     */
    protected void removeAfterCommit(String key) {
        afterCommit(() -> cache.remove(key));
    }

    public T getOrLoad(String key, Supplier<T> dbLoader) {
        // 负缓存命中：key 存在但值为 NULL_MARKER 时 cache.get 返回 null，containsKey 返回 true —— 直接返回 null，不查 DB
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        return compute(key, () -> {
            if (cache.containsKey(key)) {
                return cache.get(key);
            }

            T value = dbLoader.get();
            if (value != null) {
                cache.put(key, value);
            } else if (nullValueTtl != null && !nullValueTtl.isZero() && !nullValueTtl.isNegative()) {
                cache.put(key, null, nullValueTtl);
            }
            return value;
        });
    }

    /**
     * 在 key 粒度锁内执行 computer。
     * <p>
     * TODO(已知窗口，暂不修): compute 锁在方法 return 时释放，早于外层 @Transactional 提交。并发场景下，
     * 线程 T1 释放锁后事务提交前，T2 拿锁 getOrLoad 可能读到旧值并写缓存——靠事务隔离(RR)保证最终一致，
     * 但存在短暂不一致。彻底修法是将 syncLock 提升到 @Transactional 方法外层（锁覆盖整个事务），
     * 需重构 CachedStorageSupport 与事务边界的分层，侵入性大。当前窗口业务可接受（见 ADR-0009）。
     */
    public <R> R compute(String key, Supplier<R> computer) {
        syncLock.lock(cacheName, key);
        try {
            return computer.get();
        } finally {
            syncLock.unlock(cacheName, key);
        }
    }

    public void compute(String key, Runnable computer) {
        syncLock.lock(cacheName, key);
        try {
            computer.run();
        } finally {
            syncLock.unlock(cacheName, key);
        }
    }
}
