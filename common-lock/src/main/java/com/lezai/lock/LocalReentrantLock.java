package com.lezai.lock;

import lombok.extern.slf4j.Slf4j;

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
        return delegate.tryLock();
    }

    @Override
    public void lock(String key) {
        lock(key, this.timeout, 0);
    }

    @Override
    public void release(String key) {
        delegate.unlock();
    }
}
