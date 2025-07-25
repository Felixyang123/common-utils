package com.lezai.common_utils.lock;

import java.util.concurrent.locks.ReentrantLock;

public class LocalReentrantLock implements Lock {
    private final java.util.concurrent.locks.Lock delegate = new ReentrantLock();

    @Override
    public boolean tryLock(String key) {
        return delegate.tryLock();
    }

    @Override
    public void lock(String key) {
        delegate.lock();
    }

    @Override
    public void release(String key) {
        delegate.unlock();
    }
}
