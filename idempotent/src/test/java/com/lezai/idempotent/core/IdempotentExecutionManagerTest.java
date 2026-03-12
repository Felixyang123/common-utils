package com.lezai.idempotent.core;

import com.lezai.idempotent.annotation.Idempotent;
import com.lezai.idempotent.config.IdempotentProperties;
import com.lezai.idempotent.enums.IdempotentStatus;
import com.lezai.idempotent.exception.IdempotentException;
import com.lezai.idempotent.exception.IdempotentExecutionException;
import com.lezai.idempotent.lock.IdempotentLockProvider;
import com.lezai.idempotent.storage.IdempotentStorage;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotentExecutionManager 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("幂等执行管理器测试")
class IdempotentExecutionManagerTest {

    @Mock
    private IdempotentKeyResolver keyResolver;

    @Mock
    private IdempotentStorage storage;

    @Mock
    private IdempotentLockProvider lockProvider;

    @Mock
    private IdempotentProperties properties;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Idempotent idempotent;

    private IdempotentExecutionManager executionManager;

    @BeforeEach
    void setUp() {
        executionManager = new IdempotentExecutionManager(keyResolver, storage, lockProvider, properties);
        lenient().when(properties.getExpireTime()).thenReturn(3600L);
    }

    @Test
    @DisplayName("首次请求成功执行")
    void testFirstRequestSuccess() throws Throwable {
        // 设置
        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("test-key");
        when(storage.get("test-key")).thenReturn(null);
        when(lockProvider.tryLock(eq("test-key"), anyLong())).thenReturn(true);
        when(lockProvider.heldByCurrentThread("test-key")).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("success-result");
        when(idempotent.storeResult()).thenReturn(true);

        // 执行
        Object result = executionManager.execute(joinPoint, idempotent);

        // 验证
        assertEquals("success-result", result);
        verify(storage, times(2)).save(any(IdempotentRecord.class), anyLong());
        verify(lockProvider).unlock("test-key");
    }

    @Test
    @DisplayName("首次请求执行失败")
    void testFirstRequestFailure() throws Throwable {
        // 设置
        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("fail-key");
        when(storage.get("fail-key")).thenReturn(null);
        when(lockProvider.tryLock(eq("fail-key"), anyLong())).thenReturn(true);
        when(lockProvider.heldByCurrentThread("fail-key")).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Business error"));

        // 执行并验证
        assertThrows(IdempotentExecutionException.class, 
            () -> executionManager.execute(joinPoint, idempotent));

        verify(lockProvider).unlock("fail-key");
    }

    @Test
    @DisplayName("重复成功请求返回缓存结果")
    void testDuplicateSuccessfulRequest() throws Throwable {
        // 设置已成功的记录
        IdempotentRecord successRecord = createTestRecord("success-key", IdempotentStatus.SUCCEEDED);
        successRecord.setResult("{\"data\":\"cached\"}");
        successRecord.setResultType("java.lang.String");

        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("success-key");
        when(storage.get("success-key")).thenReturn(successRecord);
        when(idempotent.returnResultOnDuplicate()).thenReturn(true);

        // 执行
        Object result = executionManager.execute(joinPoint, idempotent);

        // 验证：直接返回，不执行业务
        assertNotNull(result);
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("重复成功请求抛出异常")
    void testDuplicateSuccessfulRequestThrowsException() throws Throwable {
        IdempotentRecord successRecord = createTestRecord("success-key", IdempotentStatus.SUCCEEDED);

        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("success-key");
        when(storage.get("success-key")).thenReturn(successRecord);
        when(idempotent.returnResultOnDuplicate()).thenReturn(false);

        // 执行并验证
        assertThrows(IdempotentException.class, 
            () -> executionManager.execute(joinPoint, idempotent));
    }

    @Test
    @DisplayName("处理中的请求-fastFail模式")
    void testProcessingRequestFastFail() throws Throwable {
        IdempotentRecord processingRecord = createTestRecord("processing-key", IdempotentStatus.PROCESSING);

        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("processing-key");
        when(storage.get("processing-key")).thenReturn(processingRecord);
        when(idempotent.failFast()).thenReturn(true);

        // 执行并验证
        assertThrows(IdempotentException.class, 
            () -> executionManager.execute(joinPoint, idempotent));
    }

    @Test
    @DisplayName("获取锁失败-fastFail模式")
    void testLockFailureFastFail() throws Throwable {
        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("lock-fail-key");
        when(storage.get("lock-fail-key")).thenReturn(null);
        when(lockProvider.tryLock(eq("lock-fail-key"), anyLong())).thenReturn(false);
        when(idempotent.failFast()).thenReturn(true);

        // 执行并验证
        assertThrows(IdempotentException.class, 
            () -> executionManager.execute(joinPoint, idempotent));
    }

    @Test
    @DisplayName("失败请求允许重试")
    void testFailedRequestRetry() throws Throwable {
        // 第一次获取失败记录
        IdempotentRecord failedRecord = createTestRecord("retry-key", IdempotentStatus.FAILED);

        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("retry-key");
        when(storage.get("retry-key")).thenReturn(failedRecord);
        when(lockProvider.heldByCurrentThread("retry-key")).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("retry-success");
        when(idempotent.storeResult()).thenReturn(true);

        // 执行
        Object result = executionManager.execute(joinPoint, idempotent);

        // 验证
        assertEquals("retry-success", result);
    }

    @Test
    @DisplayName("双重检查-并发情况下防止重复创建")
    void testDoubleCheck() throws Throwable {
        IdempotentRecord concurrentRecord = createTestRecord("double-check-key", IdempotentStatus.PROCESSING);

        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("double-check-key");
        // 第一次查询为null，获取锁后再次查询有记录（并发情况）
        when(storage.get("double-check-key"))
            .thenReturn(null)
            .thenReturn(concurrentRecord);
        when(lockProvider.tryLock(eq("double-check-key"), anyLong())).thenReturn(true);
        when(lockProvider.heldByCurrentThread("double-check-key")).thenReturn(true);
        when(idempotent.failFast()).thenReturn(true);

        // 执行并验证
        assertThrows(IdempotentException.class, 
            () -> executionManager.execute(joinPoint, idempotent));

        verify(lockProvider).unlock("double-check-key");
    }

    @Test
    @DisplayName("锁释放验证")
    void testLockRelease() throws Throwable {
        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("release-key");
        when(storage.get("release-key")).thenReturn(null);
        when(lockProvider.tryLock(eq("release-key"), anyLong())).thenReturn(true);
        when(lockProvider.heldByCurrentThread("release-key")).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("result");
        when(idempotent.storeResult()).thenReturn(true);

        executionManager.execute(joinPoint, idempotent);

        verify(lockProvider).unlock("release-key");
    }

    @Test
    @DisplayName("业务异常正确传播")
    void testBusinessExceptionPropagation() throws Throwable {
        RuntimeException businessException = new RuntimeException("Business logic error");

        when(keyResolver.resolve(joinPoint, idempotent)).thenReturn("exception-key");
        when(storage.get("exception-key")).thenReturn(null);
        when(lockProvider.tryLock(eq("exception-key"), anyLong())).thenReturn(true);
        when(lockProvider.heldByCurrentThread("exception-key")).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(businessException);

        assertThrows(IdempotentExecutionException.class, 
            () -> executionManager.execute(joinPoint, idempotent));
    }

    // ==================== 辅助方法 ====================

    private IdempotentRecord createTestRecord(String key, IdempotentStatus status) {
        IdempotentRecord record = new IdempotentRecord();
        record.setKey(key);
        record.setStatus(status);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setExpireTime(LocalDateTime.now().plusHours(1));
        return record;
    }
}
