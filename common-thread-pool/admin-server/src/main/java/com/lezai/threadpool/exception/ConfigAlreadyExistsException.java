package com.lezai.threadpool.exception;

import java.io.Serial;

/**
 * 配置已存在异常
 * HTTP 200，业务码 409
 */
public class ConfigAlreadyExistsException extends BusinessException {

    @Serial
    private static final long serialVersionUID = -5558709306417077084L;

    public ConfigAlreadyExistsException(String message) {
        super(409, message);
    }
}
