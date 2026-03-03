package com.lezai.anti.duplicate.exception;

import java.io.Serial;

public class AntiDuplicateException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -4909115107936081078L;

    public AntiDuplicateException(String message) {
        super(message);
    }
}
