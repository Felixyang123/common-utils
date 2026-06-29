package com.lezai.threadpool.pojo.cmd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 实体类
 * 用于存储和管理应用访问 admin-server Open API 的认证信息
 */
@Data
public class ApiKeyUpsertCmd {

    private Long id; // 更新时需要传入 ID

    /**
     * 应用ID（唯一标识）
     */
    private String appId;

    /**
     * API 密钥的 SHA-256 hash 值（不存储明文）
     */
    private String apiKeyHash;

    /**
     * 应用名称（用于展示）
     */
    private String appName;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 过期时间（null 表示永不过期）
     */
    private LocalDateTime expireTime;

    /**
     * 描述信息
     */
    private String description;
}
