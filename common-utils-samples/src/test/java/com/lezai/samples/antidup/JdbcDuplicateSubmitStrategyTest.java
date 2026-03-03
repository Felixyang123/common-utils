package com.lezai.samples.antidup;

import com.lezai.anti.duplicate.strategy.JdbcDuplicateSubmitStrategy;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class JdbcDuplicateSubmitStrategyTest {

    @Autowired
    private JdbcDuplicateSubmitStrategy strategy;

    @Test
    @SneakyThrows
    void shouldAllowAfterExpiration() {
        String key = "test-key-expire";
        int timeout = 1; // 1秒过期

        // 第一次请求
        assertTrue(strategy.tryLock(key, timeout));

        // 等待过期
        Thread.sleep(1500);

        // 第二次请求（应允许）
        assertTrue(strategy.tryLock(key, timeout));
    }

    @Test
    void shouldBlockWithinTTL() {
        String key = "test-key-block";
        int timeout = 5;

        assertTrue(strategy.tryLock(key, timeout));
        assertFalse(strategy.tryLock(key, timeout)); // 立即重复
    }

    @Test
    void shouldHandleConcurrentExpiration() throws InterruptedException {
        String key = "test-concurrent";
        int timeout = 1;

        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        // 模拟两个线程同时在过期后请求
        Runnable task = () -> {
            try {
                if (strategy.tryLock(key, timeout)) {
                    successCount.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        };

        new Thread(task).start();
        Thread.sleep(1500); // 等待第一个锁过期
        new Thread(task).start();

        latch.await();
        assertEquals(2, successCount.get()); // 两个都应成功
    }
}