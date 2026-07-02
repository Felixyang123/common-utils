package com.lezai.threadpool.controller.dto.response;

import lombok.Data;

/**
 * 管理员登录响应
 */
@Data
public class AdminLoginResponse {

    private String token;

    private String username;

    private long expiresInSeconds;
}
