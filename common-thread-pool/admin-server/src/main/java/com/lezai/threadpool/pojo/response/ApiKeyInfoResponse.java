package com.lezai.threadpool.pojo.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 信息响应（脱敏）
 */
@Data
public class ApiKeyInfoResponse {

    private String appId;

    private String appName;

    private boolean enabled;

    private boolean expired;

    private boolean valid;

    private LocalDateTime createTime;

    private LocalDateTime expireTime;

    private LocalDateTime updateTime;

    private String description;
}
