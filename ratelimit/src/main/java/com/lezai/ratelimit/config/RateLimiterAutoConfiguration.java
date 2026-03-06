package com.lezai.ratelimit.config;

import com.lezai.ratelimit.aspect.RateLimiterAspect;
import com.lezai.ratelimit.service.RateLimitService;
import com.lezai.ratelimit.strategy.LeakyBucketRateLimiter;
import com.lezai.ratelimit.strategy.LeakyBucketRateLimiterPlus;
import com.lezai.ratelimit.strategy.SlidingWindowRateLimiter;
import com.lezai.ratelimit.strategy.TokenBucketRateLimiter;
import com.lezai.ratelimit.strategy.redis.RedisLeakyBucketRateLimiter;
import com.lezai.ratelimit.strategy.redis.RedisSlidingWindowRateLimiter;
import com.lezai.ratelimit.strategy.redis.RedisTokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(RateLimiterPropConfig.class)
@Import(RateLimiterAspect.class)
public class RateLimiterAutoConfiguration {
    @Autowired
    private RateLimiterPropConfig propConfig;

    @Bean
    public TokenBucketRateLimiter tokenBucketRateLimiter() {
        return new TokenBucketRateLimiter(propConfig.getTokenBucket().getRate());
    }

    @Bean
    public SlidingWindowRateLimiter slidingWindowRateLimiter() {
        return new SlidingWindowRateLimiter(
                propConfig.getSlidingWindow().getWindowSize(),
                propConfig.getSlidingWindow().getRate());
    }

    @Bean
    public LeakyBucketRateLimiter leakyBucketRateLimiter() {
        return new LeakyBucketRateLimiter(
                propConfig.getLeakyBucket().getCapacity(),
                propConfig.getLeakyBucket().getRate());
    }

    @Bean
    @ConditionalOnMissingBean(RedisTemplate.class)
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    @ConditionalOnBean(RedisTemplate.class)
    public RedisTokenBucketRateLimiter redisTokenBucketRateLimiter(RedisTemplate<String, String> redisTemplate) {
        return new RedisTokenBucketRateLimiter(
                redisTemplate,
                propConfig.getRedisTokenBucket().getRate(),
                propConfig.getRedisTokenBucket().getCapacity());
    }

    @Bean
    @ConditionalOnBean(RedisTemplate.class)
    public RedisSlidingWindowRateLimiter redisSlidingWindowRateLimiter(RedisTemplate<String, String> redisTemplate) {
        return new RedisSlidingWindowRateLimiter(
                redisTemplate,
                propConfig.getRedisSlidingWindow().getRate(),
                propConfig.getRedisSlidingWindow().getWindowSize());
    }

    @Bean
    @ConditionalOnBean(RedisTemplate.class)
    public RedisLeakyBucketRateLimiter redisLeakyBucketRateLimiter(RedisTemplate<String, String> redisTemplate) {
        return new RedisLeakyBucketRateLimiter(
                redisTemplate,
                propConfig.getRedisLeakyBucket().getRate(),
                propConfig.getRedisLeakyBucket().getCapacity());
    }

    @Bean
    public RateLimitService rateLimitService() {
        return new RateLimitService();
    }

    @Bean
    public LeakyBucketRateLimiterPlus leakyBucketRateLimiterPlus() {
        return new LeakyBucketRateLimiterPlus(
                propConfig.getLeakyBucket().getCapacity(),
                propConfig.getLeakyBucket().getRate());
    }
}