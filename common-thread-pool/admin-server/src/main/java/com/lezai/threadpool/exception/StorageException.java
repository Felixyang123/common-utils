package com.lezai.threadpool.exception;

import java.io.Serial;

/**
 * 存储层异常
 * HTTP 200，业务码 500
 * 注意：此异常不会将内部错误信息暴露给客户端
 */
public class StorageException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 2046504268373722369L;

    public StorageException(String message) {
        super(500, message);
    }

    public StorageException(String message, Throwable cause) {
        super(500, message, cause);
    }
}
