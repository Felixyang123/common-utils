package com.lezai.threadpool.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API Key 实体类
 * 用于存储和管理应用访问 admin-server Open API 的认证信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

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
    @Builder.Default
    private boolean enabled = true;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 过期时间（null 表示永不过期）
     */
    private LocalDateTime expireTime;

    /**
     * 最后更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 检查 API Key 是否已过期
     *
     * @return true 如果已过期
     */
    public boolean isExpired() {
        if (expireTime == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 检查 API Key 是否有效（启用且未过期）
     *
     * @return true 如果有效
     */
    public boolean isValid() {
        return enabled && !isExpired();
    }
}
