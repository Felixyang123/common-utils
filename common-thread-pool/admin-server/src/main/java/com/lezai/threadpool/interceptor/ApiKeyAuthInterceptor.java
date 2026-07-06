package com.lezai.threadpool.interceptor;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.storage.ApiKeyStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 认证拦截器
 * 拦截 Open API 请求，验证 X-API-Key 和 X-App-Id
 */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private final ApiKeyStorage apiKeyStorage;
    private final boolean authEnabled;

    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_APP_ID = "X-App-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 如果认证未启用，直接放行
        if (!authEnabled) {
            return true;
        }

        String appId = request.getHeader(HEADER_APP_ID);
        String apiKey = request.getHeader(HEADER_API_KEY);

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

        // 验证 API Key
        boolean valid = apiKeyStorage.validateApiKey(appId, apiKey);
        if (!valid) {
            log.warn("API key validation failed - AppId: {}", appId);
            sendErrorResponse(response, 401, "Invalid API key or API key expired");
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("API key validation passed - AppId: {}", appId);
        }

        return true;
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code == 401 ? 401 : 400);
        response.setContentType("application/json;charset=UTF-8");

        ApiResponse<Void> errorResponse = ApiResponse.error(code, message);
        response.getWriter().write(JSON.toJSONString(errorResponse));
        response.getWriter().flush();
    }
}
