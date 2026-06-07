package com.lezai.threadpool.pojo.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建 API Key 响应
 */
@Data
public class CreateApiKeyResponse {

    private String appId;

    private String apiKey; // 明文 API Key，仅创建时返回

    private String appName;

    private boolean enabled;

    private LocalDateTime createTime;

    private LocalDateTime expireTime;

    private String description;
}
