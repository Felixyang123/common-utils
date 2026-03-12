package com.lezai.idempotent.exception;

/**
 * 幂等性执行异常
 * 业务逻辑执行失败时抛出
 */
public class IdempotentExecutionException extends IdempotentException {
    
    public IdempotentExecutionException(String message) {
        super(message);
    }
    
    public IdempotentExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
