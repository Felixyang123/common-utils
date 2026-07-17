package com.lezai.lock;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class LocalReentrantLock implements Lock {
    private final ReentrantLock delegate = new ReentrantLock();
    private final long timeout;

    public LocalReentrantLock(long timeout) {
        this.timeout = timeout;
    }

    public LocalReentrantLock() {
        this(30000);
    }

    @Override
    public boolean tryLock(String key, long leaseTime) {
        try {
            return delegate.tryLock(leaseTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for key: {}", key);
            return false;
        }
    }

    @Override
    public void lock(String key) {
        boolean locked = tryLock(key, this.timeout, -1);
        if (!locked) {
            throw new RuntimeException("Lock timeout: " + key);
        }
    }

    @Override
    public void release(String key) {
        if (delegate.isHeldByCurrentThread()) {
            delegate.unlock();
        }
    }

    @Override
    public boolean heldByCurrentThread(String key) {
        return delegate.isHeldByCurrentThread();
    }
}
