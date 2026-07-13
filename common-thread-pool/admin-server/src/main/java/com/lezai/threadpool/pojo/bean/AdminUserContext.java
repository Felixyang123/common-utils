package com.lezai.threadpool.pojo.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 当前登录管理员上下文。
 * <p>
 * 由 {@link com.lezai.threadpool.interceptor.AdminAuthInterceptor} 在 token 校验后构建，
 * 通过 {@link com.lezai.threadpool.context.AdminUserContextHolder} 向当前线程提供。
 * <p>
 * 任何层需要当前登录用户信息时，直接 {@code AdminUserContextHolder.get()} 获取。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserContext {
    private String username;
    private String role;
    private String nickname;
    private LocalDateTime passwordChangedAt;

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role);
    }
}