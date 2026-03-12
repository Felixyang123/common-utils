package com.lezai.ratelimit.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RateLimiterStrategyEnum {
    TOKEN_BUCKET("tokenBucket"),
    LEAKY_BUCKET("leakyBucket"),
    SLIDING_WINDOW("slidingWindow"),
    REDIS_TOKEN_BUCKET("redisTokenBucket"),
    REDIS_LEAKY_BUCKET("redisLeakyBucket"),
    REDIS_SLIDING_WINDOW("redisSlidingWindow"),
    LEAKY_BUCKET_PLUS("leakyBucketPlus");

    private final String name;
}
