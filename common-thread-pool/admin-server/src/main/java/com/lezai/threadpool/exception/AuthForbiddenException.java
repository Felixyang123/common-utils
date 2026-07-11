package com.lezai.threadpool.exception;

import java.io.Serial;

/**
 * HTTP 200，业务码 403
 */
public class AuthForbiddenException extends BusinessException {

    @Serial
    private static final long serialVersionUID = -3252049904115724154L;

    public AuthForbiddenException(String message) {
        super(403, message);
    }
}
