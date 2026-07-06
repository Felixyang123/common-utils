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
 * 基于 Redisson 的 RRateLimiter 实现按 appId 限流
 * 可通过 threadpool.admin.rate-limit.enabled=false 关闭
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedissonClient redissonClient;

    private static final String RATE_LIMITER_KEY_PREFIX = "thread-pool:rate-limit:";
    private static final int RATE_LIMIT = 100;
    private static final int RATE_LIMIT_INTERVAL = 60;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String appId = request.getHeader("X-App-Id");
        if (StringUtils.isBlank(appId)) {
            return true;
        }

        RRateLimiter rateLimiter = redissonClient.getRateLimiter(RATE_LIMITER_KEY_PREFIX + appId);
        rateLimiter.trySetRate(RateType.OVERALL, RATE_LIMIT, RATE_LIMIT_INTERVAL, RateIntervalUnit.SECONDS);

        if (rateLimiter.tryAcquire(1)) {
            return true;
        }

        log.warn("Rate limit exceeded for appId: {}", appId);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Object> apiResponse = ApiResponse.error(429, "Too many requests, please try again later");
        response.getWriter().write(JSON.toJSONString(apiResponse));
        return false;
    }
}
