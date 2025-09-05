package com.lezai.lock;

import lombok.Setter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 本地内存锁的简单实现，分布式场景下可以使用Redis扩展
 */
public class LocalLock implements Lock {
    private final ConcurrentMap<String, Object> lockMap = new ConcurrentHashMap<>();
    @Setter
    private volatile long timeout = 30000;

    @Override
    public boolean tryLock(String key, long leaseTime) {
        return lockMap.putIfAbsent(key, new Object()) == null;
    }

    @Override
    public void lock(String key) {
        lock(key, timeout, -1);
    }


    @Override
    public void release(String key) {
        lockMap.remove(key);
    }
}
