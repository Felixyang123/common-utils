package com.lezai.threadpool.exception;

import java.io.Serial;

/**
 * 配置未修改异常 304
 */
public class ResourceNotModifiedException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 3598718608031603105L;

    public ResourceNotModifiedException(String message) {
        super(304, message);
    }
}
