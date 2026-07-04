package com.lezai.threadpool.interceptor;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.bean.ApiResponse;
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
 * 校验通过后将当前管理员用户名写入 request attribute {@code currentAdminUser}，
 * 供下游 Controller 通过 {@code @RequestAttribute} 获取操作人。
 * <p>
 * 滑动续期：若 token 川余有效期低于续期阈值，签发新 token 写入响应头 {@code X-New-Token}。
 */
@Slf4j
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_CURRENT_USER = "currentAdminUser";
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
            request.setAttribute(ATTR_CURRENT_USER, "system");
            return true;
        }

        String header = request.getHeader(HEADER_AUTHORIZATION);
        if (StringUtils.isBlank(header) || !header.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or malformed Authorization header for admin request: {}", request.getRequestURI());
            sendErrorResponse(response, 401, "Missing or invalid Authorization header");
            return false;
        }

        String token = header.substring(BEARER_PREFIX.length());
        String username;
        try {
            username = adminAuthService.validateToken(token);
        } catch (AuthenticationException e) {
            log.warn("Admin token validation failed for request: {}", request.getRequestURI());
            sendErrorResponse(response, 401, e.getMessage());
            return false;
        }

        request.setAttribute(ATTR_CURRENT_USER, username);

        adminAuthService.getRemainingMinutes(token).ifPresent(remaining -> {
            if (remaining <= renewThresholdMinutes) {
                String newToken = adminAuthService.renew(username);
                response.setHeader(HEADER_NEW_TOKEN, newToken);
            }
        });

        return true;
    }

    private void sendErrorResponse(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");

        ApiResponse<Void> errorResponse = ApiResponse.error(code, message);
        response.getWriter().write(JSON.toJSONString(errorResponse));
        response.getWriter().flush();
    }
}
