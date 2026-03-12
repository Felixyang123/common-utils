package com.lezai.idempotent.storage;

import com.alibaba.fastjson2.JSON;
import com.lezai.idempotent.core.IdempotentRecord;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Redis 存储实现
 */
@Slf4j
@AllArgsConstructor
public class RedisIdempotentStorage implements IdempotentStorage {
    private static final String DEFAULT_KEY_PREFIX = "idempotent:";

    private final String keyPrefix;
    private final StringRedisTemplate redisTemplate;

    public RedisIdempotentStorage(StringRedisTemplate redisTemplate) {
        this(DEFAULT_KEY_PREFIX, redisTemplate);
    }


    @Override
    public IdempotentRecord get(String key) {
        String redisKey = keyPrefix + key;
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            return null;
        }
        return JSON.parseObject(json, IdempotentRecord.class);
    }

    @Override
    public void save(IdempotentRecord record, long expireSeconds) {
        String redisKey = keyPrefix + record.getKey();
        redisTemplate.opsForValue().set(redisKey, JSON.toJSONString(record), expireSeconds, TimeUnit.SECONDS);
        log.debug("Record saved to Redis, key: {}, expire: {}s", redisKey, expireSeconds);
    }

    @Override
    public void remove(String key) {
        String redisKey = keyPrefix + key;
        redisTemplate.delete(redisKey);
        log.debug("Record removed from Redis, key: {}", redisKey);
    }

    @Override
    public boolean exists(String key) {
        String redisKey = keyPrefix + key;
        return redisTemplate.hasKey(redisKey);
    }
}
