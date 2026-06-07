package com.lezai.threadpool.controller.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新 API Key 请求
 */
@Data
public class UpdateApiKeyRequest {

    private String appName;
    private boolean enabled;
    private String description;
    private LocalDateTime expireTime;
}
