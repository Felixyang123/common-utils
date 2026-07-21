package com.lezai.threadpool.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class LocalStripedLock implements SyncLock {

    private static final int STRIPES = 64;
    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

    public LocalStripedLock() {
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    private int stripeIndex(String lockName, String key) {
        return Math.abs((lockName + ":" + key).hashCode() % STRIPES);
    }

    @Override
    public void lock(String lockName, String key) {
        locks[stripeIndex(lockName, key)].lock();
    }

    @Override
    public boolean tryLock(String lockName, String key, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        ReentrantLock lock = locks[stripeIndex(lockName, key)];
        // 与 ReentrantLock 原生重入语义对齐：同线程可重入，不再前置拒绝
        return lock.tryLock(waitTime, timeUnit);
    }

    @Override
    public void unlock(String lockName, String key) {
        ReentrantLock lock = locks[stripeIndex(lockName, key)];
        // 与 RedissonSyncLock.unlock 行为对齐：仅当前线程持锁时才解锁，避免非法解锁抛异常
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
