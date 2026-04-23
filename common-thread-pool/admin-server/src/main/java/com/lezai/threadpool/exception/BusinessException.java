package com.lezai.threadpool.exception;

import lombok.Getter;

/**
 * 业务异常基类
 * 所有业务异常继承此类，通过 code 区分错误类型
 * HTTP 状态码始终返回 200，错误类型通过 ApiResponse.code 传递
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
