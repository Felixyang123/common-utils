package com.lezai.threadpool.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

@Slf4j
public class LocalStripedLock implements SyncLock {

    private static final int STRIPES = 64;
    private final Lock[] locks = new ReentrantLock[STRIPES];

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
    public void unlock(String lockName, String key) {
        locks[stripeIndex(lockName, key)].unlock();
    }
}
