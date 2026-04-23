package com.lezai.threadpool.exception;

import java.io.Serial;

/**
 * 配置不存在异常
 * HTTP 200，业务码 404
 */
public class ConfigNotFoundException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 3598718608031603105L;

    public ConfigNotFoundException(String message) {
        super(404, message);
    }
}
