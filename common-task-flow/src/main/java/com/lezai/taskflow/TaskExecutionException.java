package com.lezai.taskflow;

import java.io.Serial;

public class TaskExecutionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 2126461110021912327L;

    public TaskExecutionException(Throwable cause) {
        super(cause.getMessage(), cause);
    }

    public TaskExecutionException(String message) {
        super(message);
    }
}
