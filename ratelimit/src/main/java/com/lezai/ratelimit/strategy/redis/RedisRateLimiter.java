package com.lezai.ratelimit.strategy.redis;

import com.lezai.ratelimit.exception.RateLimitExceededException;
import com.lezai.ratelimit.strategy.RateLimiter;
import com.lezai.ratelimit.strategy.RateLimiterFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.ResourceUtils;

import java.nio.file.Files;
import java.nio.file.Paths;

public abstract class RedisRateLimiter implements RateLimiter {
    protected static final String REDIS_KEY_PREFIX = "ratelimit:";
    protected final RedisTemplate<String, String> redisTemplate;
    protected final RedisScript<Boolean> script;
    protected final int capacity;
    protected final int rate;

    public RedisRateLimiter(RedisTemplate<String, String> redisTemplate, String scriptPath, int cap, int rate) {
        this.redisTemplate = redisTemplate;
        this.script = loadScript(scriptPath);
        this.capacity = cap;
        this.rate = rate;
        RateLimiterFactory.register(this);
    }

    @Override
    public boolean tryAcquire(String key, int permits) throws RateLimitExceededException {
        return tryAcquire(key, this.capacity, this.rate, permits);
    }

    private RedisScript<Boolean> loadScript(String scriptPath) {
        try {
            String script = Files.readString(Paths.get(ResourceUtils.getFile(scriptPath).getAbsolutePath()));
            return new DefaultRedisScript<>(script, Boolean.class);
        } catch (Exception e) {
            throw new RateLimitExceededException("Failed to load Redis script: " + scriptPath, e);
        }
    }
}