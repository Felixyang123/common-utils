package com.lezai.samples.cache.core;

public class CacheDegradedException extends RuntimeException {

    public CacheDegradedException(String message) {
        super(message);
    }

    public CacheDegradedException(String message, Throwable cause) {
        super(message, cause);
    }
}
