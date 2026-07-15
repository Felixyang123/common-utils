package com.lezai.threadpool.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class RedissonSyncLock implements SyncLock {

    private static final String LOCK_KEY_PREFIX = "lock:";
    private final RedissonClient redissonClient;

    private String redisKey(String lockName, String key) {
        return LOCK_KEY_PREFIX + lockName + ":" + key;
    }

    @Override
    public void lock(String lockName, String key) {
        RLock lock = redissonClient.getLock(redisKey(lockName, key));
        lock.lock();
    }

    @Override
    public boolean tryLock(String lockName, String key, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        RLock lock = redissonClient.getLock(redisKey(lockName, key));
        return lock.tryLock(waitTime, leaseTime, timeUnit);
    }

    @Override
    public void unlock(String lockName, String key) {
        RLock lock = redissonClient.getLock(redisKey(lockName, key));
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
