package com.wly.samples.semaphore;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SemaphoreService {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 获取信号量
     */
    public RPermitExpirableSemaphore getSemaphore(String name) {
        return redissonClient.getPermitExpirableSemaphore(name);
    }

    /**
     * 初始化信号量（设置许可证数量）
     */
    public void initSemaphore(String name, int permits) {
        RPermitExpirableSemaphore semaphore = getSemaphore(name);
        semaphore.trySetPermits(permits);
    }

    /**
     * 获取许可证（阻塞直到获取成功）
     */
    public String acquire(String name, int initialPermits) throws InterruptedException {
        RPermitExpirableSemaphore semaphore = getSemaphore(name);
        if (!semaphore.isExists()) {
            log.info("信号量不存在，创建信号量: {}, 信号量: {}", name, initialPermits);
            semaphore.trySetPermits(initialPermits);
        }
        return semaphore.acquire();
    }

    /**
     * 尝试获取许可证（非阻塞）
     */
    public String tryAcquire(String name, int initialPermits) {
        RPermitExpirableSemaphore semaphore = getSemaphore(name);
        if (!semaphore.isExists()) {
            log.info("信号量不存在，创建信号量: {}, 信号量: {}", name, initialPermits);
            semaphore.trySetPermits(initialPermits);
        }
        return semaphore.tryAcquire();
    }

    /**
     * 尝试获取许可证（带超时）
     */
    public String tryAcquire(String name, long waitTime, TimeUnit unit)
            throws InterruptedException {
        RPermitExpirableSemaphore semaphore = getSemaphore(name);
        return semaphore.tryAcquire(waitTime, unit);
    }

    /**
     * 释放许可证
     */
    public void release(String name, String permitId) {
        RPermitExpirableSemaphore semaphore = getSemaphore(name);
        semaphore.release(permitId);
    }
}