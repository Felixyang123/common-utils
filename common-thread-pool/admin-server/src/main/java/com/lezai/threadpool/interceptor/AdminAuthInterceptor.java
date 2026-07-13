package com.lezai.threadpool.interceptor;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.pojo.bean.AdminUserContext;
import com.lezai.threadpool.context.AdminUserContextHolder;
import com.lezai.threadpool.exception.AuthenticationException;
import com.lezai.threadpool.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员认证拦截器
 * 拦截管理后台接口（/api/**，排除 /api/auth/**），校验 Authorization: Bearer 头中的 JWT token。
 * <p>
 * 校验通过后将当前登录用户上下文构建为 {@link AdminUserContext}，
 * 通过 {@link AdminUserContextHolder} 向当前线程提供。
 * 任何层（Controller、Service 等）均可直接 {@code AdminUserContextHolder.get()} 获取。
 * <p>
 * 滑动续期：若 token 剩余有效期低于续期阈值，签发新 token 写入响应头 {@code X-New-Token}。
 * 方案 C：对比 JWT iat 与用户 passwordChangedAt，改密码后旧 token 立即失效。
 */
@Slf4j
public class AdminAuthInterceptor implements HandlerInterceptor {

    static final String HEADER_AUTHORIZATION = "Authorization";
    static final String HEADER_NEW_TOKEN = "X-New-Token";
    static final String BEARER_PREFIX = "Bearer ";

    private final AdminAuthService adminAuthService;
    private final boolean authEnabled;
    private final long renewThresholdMinutes;

    public AdminAuthInterceptor(AdminAuthService adminAuthService, boolean authEnabled, long renewThresholdMinutes) {
        this.adminAuthService = adminAuthService;
        this.authEnabled = authEnabled;
        this.renewThresholdMinutes = renewThresholdMinutes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!authEnabled) {
            AdminUserContextHolder.set(AdminUserContext.builder()
                    .username("system").role("SUPER_ADMIN").nickname("system").build());
            return true;
        }

        String header = request.getHeader(HEADER_AUTHORIZATION);
        if (StringUtils.isBlank(header) || !header.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or malformed Authorization header for admin request: {}", request.getRequestURI());
            sendErrorResponse(response, 401, "Missing or invalid Authorization header");
            return false;
        }

        String token = header.substring(BEARER_PREFIX.length());
        AdminUserContext ctx;
        try {
            ctx = adminAuthService.validateToken(token);
        } catch (AuthenticationException e) {
            log.warn("Admin token validation failed for request: {}", request.getRequestURI());
            sendErrorResponse(response, e.getCode(), e.getMessage());
            return false;
        }

        AdminUserContextHolder.set(ctx);

        adminAuthService.getRemainingMinutes(token).ifPresent(remaining -> {
            if (remaining <= renewThresholdMinutes) {
                String newToken = adminAuthService.renew(ctx.getUsername(), ctx.getRole(), ctx.getNickname());
                response.setHeader(HEADER_NEW_TOKEN, newToken);
            }
        });

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        AdminUserContextHolder.clear();
    }

    private void sendErrorResponse(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");

        ApiResponse<Void> errorResponse = ApiResponse.error(code, message);
        response.getWriter().write(JSON.toJSONString(errorResponse));
        response.getWriter().flush();
    }
}