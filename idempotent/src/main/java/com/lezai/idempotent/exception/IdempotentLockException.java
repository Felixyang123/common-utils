package com.lezai.idempotent.exception;

/**
 * 幂等性锁异常
 * 获取锁失败时抛出
 */
public class IdempotentLockException extends IdempotentException {
    
    public IdempotentLockException(String message) {
        super(message);
    }
    
    public IdempotentLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
