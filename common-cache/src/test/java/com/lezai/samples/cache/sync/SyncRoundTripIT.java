package com.lezai.samples.cache.sync;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyncRoundTripIT {

    @Test
    @SuppressWarnings("unchecked")
    void publishThenReceive_roundTripsThroughCustomSerializer() {
        CachePayloadRedisSerializer serializer = new CachePayloadRedisSerializer(List.of("com.lezai"));

        // 发布端
        CacheSyncMessageImpl out = new CacheSyncMessageImpl("CAT", "CAT:k1", 60_000L);
        out.setSourceId("node-B");
        byte[] wire = serializer.serialize(out);

        // 接收端：用同一序列化器反序列化（模拟跨节点传输）
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        when(template.getValueSerializer()).thenReturn((RedisSerializer) serializer);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.get("CAT:k1")).thenReturn(CacheWrapper.of("fresh", 60_000L));

        HashMapCache<Object> backing = new HashMapCache<>(100);
        CacheManager manager = new HashMapCacheManager(backing, 100);
        RedisCacheMessageSub sub = new RedisCacheMessageSub(template, manager);

        Message msg = mock(Message.class);
        when(msg.getBody()).thenReturn(wire);
        sub.onMessage(msg, new byte[0]);

        // node-B 的消息（非自身）应被处理并回填 L1
        assertThat(manager.getCache("CAT").innerGet("CAT:k1").getData()).isEqualTo("fresh");
    }

    @Test
    void cacheWrapperValue_roundTripsThroughRedisTemplateSerializer() {
        CachePayloadRedisSerializer serializer = new CachePayloadRedisSerializer(List.of("com.lezai"));
        CacheWrapper<Object> w = CacheWrapper.of("payload", 60_000L);
        byte[] bytes = serializer.serialize(w);
        CacheWrapper<?> back = (CacheWrapper<?>) serializer.deserialize(bytes);
        assertThat(back.getData()).isEqualTo("payload");
        assertThat(back.getExpireTime()).isEqualTo(w.getExpireTime());
    }
}
