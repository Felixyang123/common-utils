package com.lezai.threadpool.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员账号实体类
 * 操作 admin-server 管理后台的人员身份，与客户端 app-id + api-key 体系互相独立（见 CONTEXT.md）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUser {

    /**
     * 用户名（唯一标识）
     */
    private String username;

    /**
     * 密码的 BCrypt 哈希值（不存储明文）
     */
    private String passwordHash;

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;
}
