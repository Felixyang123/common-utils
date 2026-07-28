package com.lezai.samples.cache.sync;

import com.lezai.samples.cache.core.Cache;
import com.lezai.samples.cache.core.CacheManager;
import com.lezai.samples.cache.core.CacheWrapper;
import com.lezai.samples.cache.impl.HashMapCache;
import com.lezai.samples.cache.impl.HashMapCacheManager;
import com.lezai.samples.cache.serializer.CachePayloadRedisSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.RedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisCacheMessageSubTest {

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_updatesL1FromRedisLatestValue() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        when(template.getValueSerializer()).thenReturn((RedisSerializer) new CachePayloadRedisSerializer(java.util.List.of("com.lezai")));
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);

        HashMapCache<Object> backing = new HashMapCache<>(100);
        CacheManager manager = new HashMapCacheManager(backing, 100);
        RedisCacheMessageSub sub = new RedisCacheMessageSub(template, manager);

        // 其他节点发来的失效消息：Redis 里有新值 newValue
        CacheSyncMessageImpl m = new CacheSyncMessageImpl("CAT", "CAT:k1", 60_000L);
        // 模拟非自身消息：把 sourceId 改成别的
        m.setSourceId("other-node");
        when(ops.get("CAT:k1")).thenReturn(CacheWrapper.of("newValue", 60_000L));

        byte[] body = new CachePayloadRedisSerializer(java.util.List.of("com.lezai")).serialize(m);
        Message msg = mock(Message.class);
        when(msg.getBody()).thenReturn(body);
        sub.onMessage(msg, new byte[0]);

        Cache<Object> cat = manager.getCache("CAT");
        CacheWrapper<Object> cached = cat.innerGet("CAT:k1");
        assertThat(cached).isNotNull();
        assertThat(cached.getData()).isEqualTo("newValue");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_ignoresSelfMessage() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        when(template.getValueSerializer()).thenReturn((RedisSerializer) new CachePayloadRedisSerializer(java.util.List.of("com.lezai")));
        HashMapCache<Object> backing = new HashMapCache<>(100);
        CacheManager manager = new HashMapCacheManager(backing, 100);
        RedisCacheMessageSub sub = new RedisCacheMessageSub(template, manager);

        // 自身消息：sourceId == 本 JVM uniqueId
        CacheSyncMessageImpl m = new CacheSyncMessageImpl("CAT", "CAT:k1", 60_000L);
        byte[] body = new CachePayloadRedisSerializer(java.util.List.of("com.lezai")).serialize(m);
        Message msg = mock(Message.class);
        when(msg.getBody()).thenReturn(body);
        sub.onMessage(msg, new byte[0]);

        assertThat(manager.getCache("CAT").innerGet("CAT:k1")).isNull(); // 被忽略，未写入
    }
}
