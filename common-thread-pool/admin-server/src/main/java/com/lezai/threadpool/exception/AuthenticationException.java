package com.lezai.threadpool.exception;

import java.io.Serial;

/**
 * 管理员认证失败异常（用户名密码错误、token 无效或过期）
 * HTTP 200，业务码 401
 */
public class AuthenticationException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 8123904561237890451L;

    public AuthenticationException(String message) {
        super(401, message);
    }
}
