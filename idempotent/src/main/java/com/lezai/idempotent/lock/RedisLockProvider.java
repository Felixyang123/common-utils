package com.lezai.idempotent.lock;

import com.lezai.lock.RedisDistributeLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 锁提供者
 * 使用 Redis SETNX + Lua 脚本实现分布式锁
 */
@Slf4j
@RequiredArgsConstructor
public class RedisLockProvider implements IdempotentLockProvider {
    private final RedisDistributeLock lock;

    @Override
    public boolean tryLock(String key, long expireSeconds) {
        String lockKey = "lock:" + key;
        boolean locked = lock.tryLock(lockKey, expireSeconds * 1000, expireSeconds * 1000);

        if (locked) {
            log.debug("Lock acquired for key: {}", lockKey);
            return true;
        }

        log.debug("Failed to acquire lock for key: {}", lockKey);
        return false;
    }

    @Override
    public void unlock(String key) {
        String lockKey = "lock:" + key;
        lock.release(lockKey);
        log.debug("Lock released for key: {}", lockKey);
    }

    @Override
    public boolean heldByCurrentThread(String key) {
        String lockKey = "lock:" + key;
        return lock.heldByCurrentThread(lockKey);
    }
}
