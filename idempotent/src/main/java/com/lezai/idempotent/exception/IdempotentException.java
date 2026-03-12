package com.lezai.idempotent.exception;

/**
 * 幂等性异常基类
 */
public class IdempotentException extends RuntimeException {
    
    public IdempotentException(String message) {
        super(message);
    }
    
    public IdempotentException(String message, Throwable cause) {
        super(message, cause);
    }
}
