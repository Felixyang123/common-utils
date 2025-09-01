package com.lezai.lock;

import lombok.SneakyThrows;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 本地内存锁的简单实现，分布式场景下可以使用Redis扩展
 */
public class LocalLock implements Lock{
    private final ConcurrentMap<String, Object> lockMap = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key) {
        return lockMap.putIfAbsent(key, new Object()) == null;
    }

    @SneakyThrows
    @Override
    public void lock(String key) {
        while (!tryLock(key)) {
            Thread.sleep(100);
        }
    }

    @Override
    public void release(String key) {
        lockMap.remove(key);
    }
}
