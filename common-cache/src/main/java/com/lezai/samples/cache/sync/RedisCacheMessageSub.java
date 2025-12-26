package com.lezai.samples.cache.sync;

import com.lezai.samples.cache.CacheManager;
import com.lezai.samples.cache.CacheWrapper;
import com.lezai.samples.cache.EnhanceCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.util.Assert;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RedisCacheMessageSub implements CacheMessageSub {
    private final RedisTemplate<String, Object> redisTemplate;
    private final String channel;
    private final CacheManager<Object> cacheManager;
    private final MessageListener messageListener;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public RedisCacheMessageSub(RedisTemplate<String, Object> redisTemplate, CacheManager<Object> cacheManager, String channel) {
        this.redisTemplate = redisTemplate;
        this.channel = channel;
        this.cacheManager = cacheManager;
        this.messageListener = (message, pattern) -> {
            try {
                // 获取消息序列化器
                RedisSerializer<?> valueSerializer = redisTemplate.getValueSerializer();
                // 反序列化消息
                Object messageObj = valueSerializer.deserialize(message.getBody());
                Assert.notNull(messageObj, "Cache sync message cannot be null");
                log.debug("Received cache sync message: {}", messageObj);
                if (messageObj instanceof CacheSyncMessage cacheSyncMessage) {
                    try {
                        process(cacheSyncMessage);
                        log.debug("Processed cache sync message: {}", cacheSyncMessage);
                    } catch (Exception e) {
                        log.error("Failed to process cache sync message: {}", cacheSyncMessage, e);
                    }
                } else {
                    log.warn("Received unexpected message type: {}", messageObj.getClass());
                }
            } catch (Exception e) {
                log.error("Failed to handle redis message", e);
            }
        };
        running.set(true);
        log.info("RedisCacheMessageSub init");
    }

    @Override
    public void subscribe() {
        try {
            redisTemplate.getConnectionFactory()
                    .getConnection()
                    .subscribe(messageListener, channel.getBytes());
            log.info("Subscribed to channel: {}", channel);
        } catch (Exception e) {
            log.error("Failed to subscribe to channel: {}", channel, e);
        }
    }

    @Override
    public void stop() {
        this.running.set(false);
    }

    private void process(CacheSyncMessage message) {
        if (CacheSyncMessage.uniqueId.equalsIgnoreCase(message.getSourceId())) {
            log.debug("Ignoring cache sync message from self: {}", message);
            return;
        }
        Object data = redisTemplate.opsForValue().get(message.getKey());
        EnhanceCache<Object> enhanceCache = cacheManager.getCache(message.getCategory());
        if (data == null) {
            enhanceCache.delete(message.getKey());
            return;
        }

        CacheWrapper<Object> cacheWrapper;
        if (data instanceof CacheWrapper cache) {
            cacheWrapper = cache;
        } else {
            cacheWrapper = new CacheWrapper<>(message.getKey(), message.getCategory(), data, enhanceCache.ttl() + System.currentTimeMillis());
        }
        enhanceCache.put(message.getKey(), cacheWrapper);
    }
}
