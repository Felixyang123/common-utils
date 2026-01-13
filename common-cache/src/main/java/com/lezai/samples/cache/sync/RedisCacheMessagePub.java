package com.lezai.samples.cache.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

@Slf4j
public class RedisCacheMessagePub implements CacheMessagePub {
    private final RedisTemplate<String, Object> redisTemplate;
    private final String channel;

    public RedisCacheMessagePub(RedisTemplate<String, Object> redisTemplate, String channel) {
        this.redisTemplate = redisTemplate;
        this.channel = channel;
        log.info("RedisCacheMessagePub init");
    }

    @Override
    public void publish(CacheSyncMessageImpl message) {
        try {
            redisTemplate.convertAndSend(channel, message);
            log.debug("Published cache sync message: {}", message);
        } catch (Exception e) {
            log.error("Failed to publish cache sync message: {}", message, e);
        }
    }

    @Override
    public void registerNodeInfo(CacheNodeRegisterInfo nodeInfo) {
        //TODO
    }
}
