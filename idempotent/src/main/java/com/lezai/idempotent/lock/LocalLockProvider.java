package com.lezai.idempotent.lock;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地锁提供者
 * 使用 ConcurrentHashMap + ReentrantLock 实现，避免 Caffeine 驱逐导致的锁泄漏
 */
@Slf4j
public class LocalLockProvider implements IdempotentLockProvider {

    private final ConcurrentHashMap<String, ReentrantLock> lockMap;

    public LocalLockProvider() {
        this.lockMap = new ConcurrentHashMap<>();
    }

    @Override
    public boolean tryLock(String key, long waitTimeoutSeconds) {
        ReentrantLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock());
        try {
            return lock.tryLock(waitTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for key: {}", key);
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        ReentrantLock lock = lockMap.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released for key: {}", key);
            // 清理不再持有的锁，防止内存泄漏
            if (!lock.isLocked()) {
                lockMap.remove(key, lock);
            }
        }
    }

    @Override
    public boolean heldByCurrentThread(String key) {
        ReentrantLock lock = lockMap.get(key);
        return lock != null && lock.isHeldByCurrentThread();
    }
}
