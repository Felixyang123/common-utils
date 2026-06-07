package com.lezai.threadpool.controller.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建 API Key 请求
 */
@Data
public class CreateApiKeyRequest {

    private String appId;
    private String appName;
    private String description;
    private LocalDateTime expireTime;
}
