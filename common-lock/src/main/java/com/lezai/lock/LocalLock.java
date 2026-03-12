package com.lezai.lock;

import lombok.Setter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 本地内存锁的简单实现，分布式场景下可以使用Redis扩展
 */
public class LocalLock implements Lock {
    private final ConcurrentMap<String, Long> lockContainer = new ConcurrentHashMap<>();
    @Setter
    private volatile long timeout = 30000;

    @Override
    public boolean tryLock(String key, long leaseTime) {
        return lockContainer.putIfAbsent(key, Thread.currentThread().threadId()) == null;
    }

    @Override
    public void lock(String key) {
        boolean locked = tryLock(key, timeout, -1);
        if (!locked) {
            throw new RuntimeException("Lock timeout: " + key);
        }
    }


    @Override
    public void release(String key) {
        lockContainer.compute(key, (k, v) -> {
            if (v != null && v == Thread.currentThread().threadId()) {
                return null;
            }
            return v;
        });
    }

    @Override
    public boolean heldByCurrentThread(String key) {
        Long lockVal = lockContainer.get(key);
        return lockVal != null && lockVal == Thread.currentThread().threadId();
    }
}
