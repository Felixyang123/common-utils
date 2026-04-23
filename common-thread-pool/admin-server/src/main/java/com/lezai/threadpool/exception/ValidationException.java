package com.lezai.threadpool.exception;

import java.io.Serial;

/**
 * 参数校验异常
 * HTTP 200，业务码 400
 */
public class ValidationException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 6823208574062154321L;

    public ValidationException(String message) {
        super(400, message);
    }
}
