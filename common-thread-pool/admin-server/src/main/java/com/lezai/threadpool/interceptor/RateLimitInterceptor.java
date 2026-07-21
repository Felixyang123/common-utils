package com.lezai.threadpool.interceptor;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.bean.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Open API 限流拦截器
 * 基于 Redisson 的 RRateLimiter 实现按 appId 限流。
 * appId 为空时按客户端 IP 限流（独立 key 前缀），防止无 appId 无效请求洪流（决议第 5 题方向 Y）。
 * 可通过 threadpool.admin.rate-limit.enabled=false 关闭。
 *
 * <p>本拦截器仅计合法配额（100/60s）；API Key 认证失败请求由 {@link ApiKeyFailureRateLimiter}
 * 独立计数，不挤占本拦截器配额。
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedissonClient redissonClient;

    private static final String RATE_LIMITER_KEY_PREFIX = "thread-pool:rate-limit:";
    private static final String IP_RATE_LIMITER_KEY_PREFIX = "thread-pool:rate-limit-ip:";
    private static final int RATE_LIMIT = 100;
    private static final int RATE_LIMIT_INTERVAL = 60;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String appId = request.getHeader("X-App-Id");

        if (StringUtils.isBlank(appId)) {
            // appId 为空：按客户端 IP 限流，防止无 appId 无效请求洪流
            String clientIp = request.getRemoteAddr();
            if (StringUtils.isBlank(clientIp)) {
                clientIp = "unknown";
            }
            return tryAcquire(response, IP_RATE_LIMITER_KEY_PREFIX + clientIp, clientIp);
        }

        return tryAcquire(response, RATE_LIMITER_KEY_PREFIX + appId, appId);
    }

    private boolean tryAcquire(HttpServletResponse response, String key, String label) throws Exception {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, RATE_LIMIT, RATE_LIMIT_INTERVAL, RateIntervalUnit.SECONDS);

        if (rateLimiter.tryAcquire(1)) {
            return true;
        }

        log.warn("Rate limit exceeded for {}: {}", key.contains("rate-limit-ip:") ? "ip" : "appId", label);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Object> apiResponse = ApiResponse.error(429, "Too many requests, please try again later");
        response.getWriter().write(JSON.toJSONString(apiResponse));
        return false;
    }
}
