package com.lezai.ratelimit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limit")
@Data
public class RateLimiterPropConfig {
    private Properties tokenBucket = new Properties();

    private Properties slidingWindow = new Properties();

    private Properties leakyBucket = new Properties();

    private Properties redisTokenBucket = new Properties();

    private Properties redisSlidingWindow = new Properties();

    private Properties redisLeakyBucket = new Properties();

    @Data
    public static class Properties {

        private int rate = 10;

        private int capacity = 10;

        private int windowSize = 1;

    }
}
