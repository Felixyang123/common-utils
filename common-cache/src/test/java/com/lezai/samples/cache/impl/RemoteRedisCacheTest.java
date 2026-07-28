package com.lezai.samples.cache.impl;

import com.lezai.samples.cache.core.CacheWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteRedisCacheTest {

    @Test
    @SuppressWarnings("unchecked")
    void innerSet_writesRedisTtl_whenExpireTimePresent() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        RemoteRedisCache<Object> cache = new RemoteRedisCache<>(template);

        CacheWrapper<Object> wrapper = CacheWrapper.of("v", 60_000L);
        cache.innerSet("k", wrapper);

        // 必须带 Duration 写入（TTL ≈ 60s）
        verify(ops).set(eq("k"), eq(wrapper), any(Duration.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void innerSet_writesWithoutTtl_whenExpireTimeNull() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        RemoteRedisCache<Object> cache = new RemoteRedisCache<>(template);

        CacheWrapper<Object> wrapper = CacheWrapper.of("v", null);
        cache.innerSet("k2", wrapper);

        // 无 expireTime 时走无 TTL 的重载
        verify(ops).set(eq("k2"), eq(wrapper));
    }

    @Test
    @SuppressWarnings("unchecked")
    void innerSet_deletesKey_whenAlreadyExpired() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        RemoteRedisCache<Object> cache = new RemoteRedisCache<>(template);

        CacheWrapper<Object> wrapper = CacheWrapper.of("v", -1L); // 已过期
        cache.innerSet("k3", wrapper);

        verify(template).delete("k3");
    }
}