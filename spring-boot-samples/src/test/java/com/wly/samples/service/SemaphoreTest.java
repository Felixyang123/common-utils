package com.wly.samples.service;

import com.wly.samples.semaphore.DynamicConfigSemaphoreManager;
import com.wly.samples.semaphore.SemaphoreService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

@SpringBootTest
public class SemaphoreTest {
    @Resource
    private SemaphoreService semaphoreService;

    @Resource
    DynamicConfigSemaphoreManager dynamicConfigSemaphoreManager;

    @Resource
    private RedissonClient redissonClient;

    private static final String SEMAPHORE_NAME = "test_semaphore2";

    @Test
    @DisplayName("测试信号量")
    void acquiredTest() throws InterruptedException {
//        semaphoreService.initSemaphore(SEMAPHORE_NAME, 1);
        String permitId = semaphoreService.tryAcquire(SEMAPHORE_NAME, 1);
        System.out.println("获取许可证：" + permitId);
        Assertions.assertNotNull(permitId);

        String permitId1 = semaphoreService.tryAcquire(SEMAPHORE_NAME, 1);
        Assertions.assertNull(permitId1);

        semaphoreService.release(SEMAPHORE_NAME, permitId);

        String permitId2 = semaphoreService.tryAcquire(SEMAPHORE_NAME, 1);
        System.out.println("获取许可证：" + permitId2);
        Assertions.assertNotNull(permitId2);
    }

    @Test
    @DisplayName("测试并发获取信号量")
    void concurrencyAcquiredTest() throws InterruptedException {
        String key = "test_semaphore9";
        List<String> permits = new ArrayList<>();
        CountDownLatch count = new CountDownLatch(20);
        for (int i = 0; i < 20; i++) {
            new Thread(() -> {
                try {
                    String permitId = semaphoreService.acquire(key, 5);
                    Optional.ofNullable(permitId).ifPresent(permit -> {
                        permits.add(permit);
                        semaphoreService.release(key, permit);
                    });
                    count.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
        count.await();
        Assertions.assertEquals(20, permits.size());
    }

    @Test
    @DisplayName("测试归还信号量")
    void releaseTest() throws InterruptedException {
        String key = "test_semaphore10";
        String permitId = semaphoreService.acquire(key, 5);
        RPermitExpirableSemaphore semaphore = semaphoreService.getSemaphore(key);
        int permits = semaphore.availablePermits();
        System.out.println("剩余许可证数：" + permits);
        semaphore.delete();
        RPermitExpirableSemaphore semaphore1 = semaphoreService.getSemaphore(key);
        semaphore1.trySetPermits(5);
        if (semaphore.isExists()) {
            semaphoreService.release(key, permitId);
        }
    }

    @Test
    @DisplayName("测试删除信号量")
    void deleteTest() throws InterruptedException {
        String key = "test_semaphore11";
        RPermitExpirableSemaphore semaphore = semaphoreService.getSemaphore(key);
        semaphore.delete();
    }

    @Test
    @DisplayName("测试字符串操作")
    void testStr() {
        RBucket<String> bucket = redissonClient.getBucket("optstr");
        System.out.println(bucket.get());
        bucket.set("hello world");
        String val = bucket.get();
        System.out.println(val);
//        bucket.delete();
//        val = bucket.get();
//        System.out.println(val);
    }
}
