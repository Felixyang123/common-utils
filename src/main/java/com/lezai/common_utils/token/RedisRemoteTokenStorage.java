package com.lezai.common_utils.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

@RequiredArgsConstructor
public class RedisRemoteTokenStorage implements TokenStorage<String, String> {
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public String getToken(String key) {
        return (String) redisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean setToken(String key, String value) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value));
    }

    @Override
    public String deleteToken(String key) {
        String token = getToken(key);
        if (redisTemplate.delete(key)) {
            return token;
        }
        return null;
    }
}
