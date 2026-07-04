package com.lezai.threadpool.exception;

/**
 * 参数校验异常。
 * <p>
 * client-sdk 内部的参数非法统一抛本异常，不使用 {@link IllegalArgumentException}，
 * 便于调用方按本库的异常体系统一捕获。继承 {@link RuntimeException}——与
 * {@link PoolNotFoundException} 风格一致（client-sdk 不依赖 admin-server 的
 * BusinessException 体系，见 CONTEXT.md）。
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
