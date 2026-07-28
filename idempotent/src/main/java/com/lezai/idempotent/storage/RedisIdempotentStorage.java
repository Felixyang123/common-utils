package com.lezai.idempotent.storage;

import com.alibaba.fastjson2.JSON;
import com.lezai.idempotent.core.IdempotentRecord;
import com.lezai.idempotent.exception.IdempotentStorageException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Redis 存储实现。
 *
 * <p>遵循 IdempotentStorage 接口契约 (ADR-0002):get/remove/exists 在访问
 * Redis 失败或反序列化失败时上抛异常,由调用方决定是否 fail-fast。返回
 * {@code null} 唯一语义 = "幂等键不存在"。</p>
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
        // 不吞异常:Redis 连接失败 / JSON 异常上抛,由调用方 fail-fast + retry
        String json = redisTemplate.opsForValue().get(keyPrefix + key);
        if (json == null) {
            return null;
        }
        return JSON.parseObject(json, IdempotentRecord.class);
    }

    @Override
    public void save(IdempotentRecord record, long expireSeconds) {
        if (expireSeconds <= 0) {
            throw new IllegalArgumentException("expireSeconds must be positive, got " + expireSeconds);
        }
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
        // 不吞异常:Redis 连接失败上抛,由调用方 fail-fast
        Boolean result = redisTemplate.hasKey(keyPrefix + key);
        if (result == null) {
            throw new IdempotentStorageException(
                    "Redis hasKey returned null (pipeline/connection issue), key=" + key);
        }
        return result;
    }
}
