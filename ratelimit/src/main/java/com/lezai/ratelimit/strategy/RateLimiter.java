package com.lezai.ratelimit.strategy;


import com.lezai.ratelimit.exception.RateLimitExceededException;

public interface RateLimiter {
    boolean tryAcquire(String key, int permits) throws RateLimitExceededException;

    /**
     * 获取限流策略名称
     * @see com.lezai.ratelimit.enumeration.RateLimiterStrategyEnum
     * @return
     */
    String name();
}