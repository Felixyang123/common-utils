package com.lezai.idempotent.lock;

import com.lezai.lock.RedisDistributeLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis锁提供者测试")
class RedisLockProviderTest {

    @Mock
    private RedisDistributeLock redisDistributeLock;

    private RedisLockProvider lockProvider;

    @BeforeEach
    void setUp() {
        lockProvider = new RedisLockProvider(redisDistributeLock);
    }

    @Test
    @DisplayName("tryLock应使用非阻塞两参数方法")
    void testTryLockUsesNonBlockingOverload() {
        when(redisDistributeLock.tryLock(eq("lock:test-key"), anyLong()))
                .thenReturn(true);

        boolean result = lockProvider.tryLock("test-key", 30);

        assertTrue(result);
        verify(redisDistributeLock).tryLock(eq("lock:test-key"), eq(30000L));
        verify(redisDistributeLock, never())
                .tryLock(eq("lock:test-key"), anyLong(), anyLong());
    }

    @Test
    @DisplayName("tryLock失败时返回false")
    void testTryLockReturnsFalse() {
        when(redisDistributeLock.tryLock(eq("lock:test-key"), anyLong()))
                .thenReturn(false);

        assertFalse(lockProvider.tryLock("test-key", 30));
    }

    @Test
    @DisplayName("unlock调用release")
    void testUnlock() {
        lockProvider.unlock("test-key");
        verify(redisDistributeLock).release("lock:test-key");
    }

    @Test
    @DisplayName("heldByCurrentThread委托给底层锁")
    void testHeldByCurrentThread() {
        when(redisDistributeLock.heldByCurrentThread("lock:test-key"))
                .thenReturn(true);

        assertTrue(lockProvider.heldByCurrentThread("test-key"));
    }
}
