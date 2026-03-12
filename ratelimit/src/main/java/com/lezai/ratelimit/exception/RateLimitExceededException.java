package com.lezai.ratelimit.exception;

import java.io.Serial;

public class RateLimitExceededException extends RuntimeException{
    @Serial
    private static final long serialVersionUID = -3009452662938974343L;

    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
