package com.lezai.threadpool.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * API Key 实体类
 * 用于存储和管理应用访问 admin-server Open API 的认证信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName(value = "api_key", autoResultMap = true)
public class ApiKeyEntity extends BaseEntity {
    @Serial
    private static final long serialVersionUID = -8427733888707075983L;

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
    private Boolean enabled = Boolean.TRUE;

    /**
     * 过期时间（null 表示永不过期）
     */
    private LocalDateTime expireTime;

    /**
     * 描述信息
     */
    private String description;
}
