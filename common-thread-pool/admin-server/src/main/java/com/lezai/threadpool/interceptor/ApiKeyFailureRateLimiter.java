package com.lezai.threadpool.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * API Key 认证失败请求独立限流（决议第 5 题方向 Y）
 *
 * <p>per-appId 失败计数（Redisson RAtomicLong + 60s TTL）。连续失败 &gt; 10 次/60s → 后续请求直接 429，
 * 不再查缓存/跑 HMAC，避免 DoS 放大。失败请求不挤占合法请求的 100/60s 配额（见 RateLimitInterceptor）。
 */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyFailureRateLimiter {

    private static final String KEY_PREFIX = "thread-pool:apikey-failure:";
    private static final int MAX_FAILURES = 10;
    private static final Duration TTL = Duration.ofSeconds(60);

    private final RedissonClient redissonClient;

    /**
     * 是否已被限流（当前计数 &gt; 阈值）。用于在验证前快速拒绝，避免跑 HMAC。
     */
    public boolean isBlocked(String appId) {
        String key = KEY_PREFIX + appId;
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        return counter.get() >= MAX_FAILURES;
    }

    /**
     * 记录一次认证失败。
     *
     * @param appId 应用 ID
     */
    public void recordFailure(String appId) {
        String key = KEY_PREFIX + appId;
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        // 首次写入设定 TTL；已存在则仅自增（保留剩余 TTL）
        counter.expireIfNotSet(TTL);
        long count = counter.incrementAndGet();
        if (count == MAX_FAILURES + 1) {
            log.warn("API key failure rate limit triggered for appId: {} (count: {}/{}s)", appId, count, TTL.getSeconds());
        }
    }
}
