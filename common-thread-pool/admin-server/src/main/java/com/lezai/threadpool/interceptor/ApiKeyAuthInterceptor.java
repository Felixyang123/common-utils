package com.lezai.threadpool.interceptor;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.context.ClientContextHolder;
import com.lezai.threadpool.storage.ApiKeyStorage;
import com.lezai.threadpool.utils.LogContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 认证拦截器
 * 拦截 Open API 请求，验证 X-API-Key 和 X-App-Id
 */
@Slf4j
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private final ApiKeyStorage apiKeyStorage;
    private final boolean authEnabled;

    /**
     * API Key 失败限流（依赖 Redisson，local profile 下为 null）。
     * 独立于 RateLimitInterceptor 的 100/60s 合法配额，仅计数失败请求。
     */
    @Autowired(required = false)
    private ApiKeyFailureRateLimiter failureRateLimiter;

    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_APP_ID = "X-App-Id";

    public ApiKeyAuthInterceptor(ApiKeyStorage apiKeyStorage, boolean authEnabled) {
        this.apiKeyStorage = apiKeyStorage;
        this.authEnabled = authEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 如果认证未启用，直接放行
        if (!authEnabled) {
            return true;
        }

        String appId = request.getHeader(HEADER_APP_ID);
        String apiKey = request.getHeader(HEADER_API_KEY);

        // 注入 MDC，后续日志自动携带 appId
        if (StringUtils.isNotBlank(appId)) {
            LogContext.setAppId(appId);
        }

        // 记录请求信息
        if (log.isDebugEnabled()) {
            log.debug("API Key auth check - URI: {}, Method: {}, AppId: {}",
                    request.getRequestURI(), request.getMethod(), appId);
        }

        // 检查请求头是否存在
        if (StringUtils.isBlank(appId)) {
            log.warn("Missing required header: {}", HEADER_APP_ID);
            sendErrorResponse(response, 400, "Missing required header: " + HEADER_APP_ID);
            return false;
        }

        if (StringUtils.isBlank(apiKey)) {
            log.warn("Missing required header: {}", HEADER_API_KEY);
            sendErrorResponse(response, 400, "Missing required header: " + HEADER_API_KEY);
            return false;
        }

        // 失败限流：已被封锁的 appId 直接 429，不再查缓存/跑 HMAC（防 DoS 放大，决议第 5 题方向 Y）
        if (failureRateLimiter != null && failureRateLimiter.isBlocked(appId)) {
            log.warn("API key failure rate limit blocked - AppId: {}", appId);
            sendErrorResponse(response, 429, "Too many failed authentication attempts, please try again later");
            return false;
        }

        // 验证 API Key
        boolean valid = apiKeyStorage.validateApiKey(appId, apiKey);
        if (!valid) {
            log.warn("API key validation failed - AppId: {}", appId);
            // 记录失败（独立计数器，不挤占合法 100/60s 配额）
            if (failureRateLimiter != null) {
                failureRateLimiter.recordFailure(appId);
            }
            sendErrorResponse(response, 401, "Invalid API key or API key expired");
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("API key validation passed - AppId: {}", appId);
        }

        ClientContextHolder.set(appId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        LogContext.clear();
        ClientContextHolder.clear();
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");

        ApiResponse<Void> errorResponse = ApiResponse.error(code, message);
        response.getWriter().write(JSON.toJSONString(errorResponse));
        response.getWriter().flush();
    }
}
