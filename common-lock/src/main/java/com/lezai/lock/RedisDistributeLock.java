package com.lezai.lock;

/**
 * TODO 基于Redis实现分布式锁
 */
public class RedisDistributeLock implements Lock{
    @Override
    public boolean tryLock(String key) {
        return false;
    }

    @Override
    public void lock(String key) {

    }

    @Override
    public void release(String key) {

    }
}
