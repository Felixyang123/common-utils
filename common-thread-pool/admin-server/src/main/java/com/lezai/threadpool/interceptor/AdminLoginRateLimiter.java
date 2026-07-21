package com.lezai.threadpool.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * 管理员登录暴力破解防护（决议第 5 题方向 Y / 登录加固）
 *
 * <p>per-username 失败计数（Redisson RAtomicLong + 15min TTL）。连续失败 &ge; 5 次 → 锁定该 username 15min，
 * 期间直接拒绝（401 + 锁定提示）。成功登录清零计数。
 */
@Slf4j
@RequiredArgsConstructor
public class AdminLoginRateLimiter {

    private static final String KEY_PREFIX = "thread-pool:admin-login-failure:";
    private static final int MAX_FAILURES = 5;
    private static final Duration TTL = Duration.ofMinutes(15);

    private final RedissonClient redissonClient;

    /**
     * 记录一次登录失败。
     *
     * @param username 用户名
     * @return 当前连续失败次数
     */
    public long recordFailure(String username) {
        String key = KEY_PREFIX + username;
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        counter.expireIfNotSet(TTL);
        long count = counter.incrementAndGet();
        if (count == MAX_FAILURES) {
            log.warn("Admin login locked due to {} consecutive failures for username: {}", MAX_FAILURES, username);
        }
        return count;
    }

    /**
     * 是否已被锁定（当前失败次数 &ge; 阈值）。
     */
    public boolean isLocked(String username) {
        String key = KEY_PREFIX + username;
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        return counter.get() >= MAX_FAILURES;
    }

    /**
     * 成功登录后清零失败计数。
     */
    public void clear(String username) {
        String key = KEY_PREFIX + username;
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        counter.delete();
    }
}
