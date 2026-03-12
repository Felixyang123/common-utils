package com.lezai.idempotent.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幂等异常类测试
 */
@DisplayName("幂等异常类测试")
class IdempotentExceptionTest {

    @Test
    @DisplayName("创建异常-仅消息")
    void testCreateWithMessage() {
        String message = "Duplicate request detected";
        IdempotentException exception = new IdempotentException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("创建异常-消息和原因")
    void testCreateWithMessageAndCause() {
        String message = "Idempotent check failed";
        Throwable cause = new RuntimeException("Underlying error");
        
        IdempotentException exception = new IdempotentException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals("Underlying error", exception.getCause().getMessage());
    }

    @Test
    @DisplayName("执行异常-消息")
    void testExecutionExceptionWithMessage() {
        String message = "Business method execution failed";
        IdempotentExecutionException exception = new IdempotentExecutionException(message);

        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("执行异常-消息和原因")
    void testExecutionExceptionWithCause() {
        Throwable cause = new IllegalArgumentException("Invalid argument");
        IdempotentExecutionException exception = 
            new IdempotentExecutionException("Execution failed", cause);

        assertEquals("Execution failed", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("锁异常-消息")
    void testLockExceptionWithMessage() {
        String message = "Failed to acquire lock";
        IdempotentLockException exception = new IdempotentLockException(message);

        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("锁异常-消息和原因")
    void testLockExceptionWithCause() {
        Throwable cause = new InterruptedException("Thread interrupted");
        IdempotentLockException exception = 
            new IdempotentLockException("Lock acquisition interrupted", cause);

        assertEquals("Lock acquisition interrupted", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("异常链传递")
    void testExceptionChain() {
        Throwable rootCause = new NullPointerException("NPE occurred");
        Throwable intermediateCause = new RuntimeException("Wrapped NPE", rootCause);
        
        IdempotentExecutionException exception = 
            new IdempotentExecutionException("Business error", intermediateCause);

        assertEquals(intermediateCause, exception.getCause());
        assertEquals(rootCause, exception.getCause().getCause());
    }

    @Test
    @DisplayName("异常可以被抛出和捕获")
    void testExceptionCanBeThrownAndCaught() {
        try {
            throw new IdempotentException("Test exception");
        } catch (IdempotentException e) {
            assertEquals("Test exception", e.getMessage());
        }
    }
}
