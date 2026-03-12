package com.lezai.idempotent.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalLockProvider 单元测试
 */
@DisplayName("本地锁提供者测试")
class LocalLockProviderTest {

    private LocalLockProvider lockProvider;

    @BeforeEach
    void setUp() {
        lockProvider = new LocalLockProvider();
    }

    @Test
    @DisplayName("获取锁成功")
    void testTryLockSuccess() {
        boolean locked = lockProvider.tryLock("test-lock", 1);
        assertTrue(locked);
        
        // 清理
        lockProvider.unlock("test-lock");
    }

    @Test
    @DisplayName("释放锁后可以再次获取")
    void testUnlockAndRelock() {
        assertTrue(lockProvider.tryLock("relock-test", 1));
        lockProvider.unlock("relock-test");
        assertTrue(lockProvider.tryLock("relock-test", 1));
        lockProvider.unlock("relock-test");
    }

    @Test
    @DisplayName("检查当前线程是否持有锁")
    void testHeldByCurrentThread() {
        assertFalse(lockProvider.heldByCurrentThread("held-test"));
        
        lockProvider.tryLock("held-test", 1);
        assertTrue(lockProvider.heldByCurrentThread("held-test"));
        
        lockProvider.unlock("held-test");
        assertFalse(lockProvider.heldByCurrentThread("held-test"));
    }

    @Test
    @DisplayName("同一键名可重入")
    void testReentrantLock() {
        // 第一次获取锁
        assertTrue(lockProvider.tryLock("reentrant-key", 1));
        assertTrue(lockProvider.heldByCurrentThread("reentrant-key"));
        
        // 同一线程再次获取（ReentrantLock 支持重入）
        assertTrue(lockProvider.tryLock("reentrant-key", 1));
        
        // 释放两次
        lockProvider.unlock("reentrant-key");
        assertTrue(lockProvider.heldByCurrentThread("reentrant-key"));
        
        lockProvider.unlock("reentrant-key");
        assertFalse(lockProvider.heldByCurrentThread("reentrant-key"));
    }

    @Test
    @DisplayName("释放未持有的锁不会抛异常")
    void testUnlockNotHeld() {
        assertDoesNotThrow(() -> lockProvider.unlock("not-held-key"));
    }

    @Test
    @DisplayName("并发获取锁测试")
    void testConcurrentLock() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    // 尝试获取锁，使用很短的超时
                    boolean locked = lockProvider.tryLock("concurrent-lock", 1);
                    if (locked) {
                        successCount.incrementAndGet();
                        Thread.sleep(1100); // 模拟业务处理
                        lockProvider.unlock("concurrent-lock");
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // 只有一个线程能成功获取锁（因为是串行处理）
        assertEquals(1, successCount.get());
        assertEquals(threadCount - 1, failCount.get());
    }

    @Test
    @DisplayName("不同键名的锁互不影响")
    void testDifferentKeys() {
        assertTrue(lockProvider.tryLock("key-1", 1));
        assertTrue(lockProvider.tryLock("key-2", 1));
        
        assertTrue(lockProvider.heldByCurrentThread("key-1"));
        assertTrue(lockProvider.heldByCurrentThread("key-2"));
        
        lockProvider.unlock("key-1");
        assertFalse(lockProvider.heldByCurrentThread("key-1"));
        assertTrue(lockProvider.heldByCurrentThread("key-2"));
        
        lockProvider.unlock("key-2");
        assertFalse(lockProvider.heldByCurrentThread("key-2"));
    }
}
