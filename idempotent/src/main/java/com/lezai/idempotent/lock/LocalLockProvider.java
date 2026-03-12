package com.lezai.idempotent.lock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地锁提供者
 * 使用 Caffeine Cache + ReentrantLock 实现
 */
@Slf4j
public class LocalLockProvider implements IdempotentLockProvider {
    
    private final Cache<String, ReentrantLock> lockCache;
    
    public LocalLockProvider() {
        this.lockCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build();
    }
    
    @Override
    public boolean tryLock(String key, long expireSeconds) {
        ReentrantLock lock = lockCache.get(key, k -> new ReentrantLock());
        try {
            return lock != null && lock.tryLock(expireSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for key: {}", key);
            return false;
        }
    }
    
    @Override
    public void unlock(String key) {
        ReentrantLock lock = lockCache.getIfPresent(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released for key: {}", key);
        }
    }
    
    @Override
    public boolean heldByCurrentThread(String key) {
        ReentrantLock lock = lockCache.getIfPresent(key);
        return lock != null && lock.isHeldByCurrentThread();
    }
}
