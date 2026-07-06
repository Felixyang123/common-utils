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
 * 管理员账号实体类
 * 用于存储操作 admin-server 管理后台的人员身份，与 {@link ApiKeyEntity}（客户端身份）互相独立
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName(value = "admin_user")
public class AdminUserEntity extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 5169823456091827431L;

    /**
     * 用户名（唯一标识）
     */
    private String username;

    /**
     * 密码的 BCrypt 哈希值
     */
    private String passwordHash;

    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = Boolean.TRUE;

    /**
     * 角色（SUPER_ADMIN / ADMIN）
     */
    @Builder.Default
    private String role = "ADMIN";

    /**
     * 昵称（默认同 username）
     */
    private String nickname;

    /**
     * 密码最近修改时间（方案 C：用于 JWT iat 对比，改密码后旧 token 失效）
     */
    private LocalDateTime passwordChangedAt;
}
