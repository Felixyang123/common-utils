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
 * 拦截管理后台接口（/api/**，排除 /api/auth/**），校验 Authorization: Bearer 头中的 JWT token
 */
@Slf4j
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminAuthService adminAuthService;
    private final boolean authEnabled;

    public AdminAuthInterceptor(AdminAuthService adminAuthService, boolean authEnabled) {
        this.adminAuthService = adminAuthService;
        this.authEnabled = authEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!authEnabled) {
            return true;
        }

        String header = request.getHeader(HEADER_AUTHORIZATION);
        if (StringUtils.isBlank(header) || !header.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or malformed Authorization header for admin request: {}", request.getRequestURI());
            sendErrorResponse(response, 401, "Missing or invalid Authorization header");
            return false;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            adminAuthService.validateToken(token);
        } catch (AuthenticationException e) {
            log.warn("Admin token validation failed for request: {}", request.getRequestURI());
            sendErrorResponse(response, 401, e.getMessage());
            return false;
        }

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
