package com.lezai.samples.cache.sync;

import com.lezai.samples.cache.core.Cache;
import com.lezai.samples.cache.core.CacheManager;
import com.lezai.samples.cache.core.CacheWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 缓存同步消息监听器。仅负责消息处理；订阅生命周期（连接、重连、停止）由
 * RedisMessageListenerContainer 托管（见 SyncMessageAutoConfiguration）。
 */
@Slf4j
public class RedisCacheMessageSub implements MessageListener {
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    public RedisCacheMessageSub(RedisTemplate<String, Object> redisTemplate, CacheManager cacheManager) {
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RedisSerializer<?> valueSerializer = redisTemplate.getValueSerializer();
            Object messageObj = valueSerializer.deserialize(message.getBody());
            if (messageObj instanceof CacheSyncMessageImpl m) {
                process(m);
            } else {
                log.warn("unexpected cache sync message type: {}", messageObj == null ? "null" : messageObj.getClass());
            }
        } catch (Exception e) {
            // 错误隔离：单条坏消息不杀死监听循环
            log.error("failed to handle cache sync message", e);
        }
    }

    private void process(CacheSyncMessageImpl message) {
        if (StringUtils.equalsIgnoreCase(message.uniqueId(), message.getSourceId())) {
            log.debug("ignoring self cache sync message: {}", message);
            return;
        }
        Object data = redisTemplate.opsForValue().get(message.getKey());
        Cache<Object> cache = cacheManager.getCache(message.getCategory());
        if (data == null) {
            cache.remove(message.getKey());
            return;
        }
        CacheWrapper<Object> cacheWrapper = data instanceof CacheWrapper cw
                ? cw
                : new CacheWrapper<>(data, message.getExpireTime());
        cache.innerSet(message.getKey(), cacheWrapper);
    }
}
