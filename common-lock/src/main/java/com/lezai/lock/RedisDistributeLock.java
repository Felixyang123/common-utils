package com.lezai.lock;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis实现分布式锁
 */
@Slf4j
@RequiredArgsConstructor
public class RedisDistributeLock implements Lock {
    private final StringRedisTemplate redisTemplate;

    private final WatchDogExecutor watchDogExecutor;

    private static final String UNLOCK_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final ThreadLocal<Map<String, Integer>> lockCounts = ThreadLocal.withInitial(HashMap::new);

    private final String instanceId = UUID.randomUUID().toString();

    @Setter
    private volatile long timeout = 30000;
    @Setter
    private volatile long leaseTime = 90000;

    @Override
    public boolean tryLock(String key, long leaseTime) {
        Map<String, Integer> lockCntMap = lockCounts.get();
        Integer lockCnt = lockCntMap.get(key);
        if (lockCnt != null) {
            lockCntMap.put(key, ++lockCnt);
            return true;
        }
        String lockVal = instanceId + ":" + Thread.currentThread().threadId();
        Boolean set = redisTemplate.opsForValue().setIfAbsent(key, lockVal, leaseTime, TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(set)) {
            lockCntMap.put(key, 1);
            watchDogExecutor.createLeaseTask(key, lockVal, leaseTime);
            return true;
        }
        return false;
    }

    @Override
    public void lock(String key) {
        boolean locked = tryLock(key, timeout, leaseTime);
        if (!locked) {
            throw new RuntimeException("Lock timeout: " + key);
        }
    }

    @Override
    public void release(String key) {
        Map<String, Integer> lockCntMap = lockCounts.get();
        Integer lockCnt = lockCntMap.get(key);
        if (lockCnt == null) {
            // 抛出异常，客户端catch异常后回滚当前业务
            throw new RuntimeException("Release other thread's lock, current key is " + key);
        }

        lockCnt--;
        if (lockCnt < 1) {
            lockCntMap.remove(key);
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
            String lockVal = instanceId + ":" + Thread.currentThread().threadId();
            redisTemplate.execute(script, Collections.singletonList(key), lockVal);
            watchDogExecutor.removeLeaseTask(key, lockVal);
        } else {
            lockCntMap.put(key, lockCnt);
        }
    }

    @Override
    public boolean heldByCurrentThread(String key) {
        String val = redisTemplate.opsForValue().get(key);
        return StringUtils.equals(val, instanceId + ":" + Thread.currentThread().threadId());
    }

    /**
     * 判断锁 key 是否存在（被任意实例/线程持有）。
     * 用于幂等组件的"锁即租约"语义：锁不存在 = 执行者已崩溃。
     *
     * @param key 锁键
     * @return 锁存在返回 true，不存在返回 false
     */
    public boolean isLocked(String key) {
        Boolean exists = redisTemplate.hasKey(key);
        if (exists == null) {
            // hasKey 返回 null 通常出现在 pipeline 模式或连接异常
            log.warn("Redis hasKey returned null for lock existence check, key: {}", key);
            return false;
        }
        return exists;
    }
}
