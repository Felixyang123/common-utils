package com.lezai.taskflow.idgen;

import com.lezai.taskflow.storage.mysql.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

@RequiredArgsConstructor
public class RedisIdGenerator implements IdGenerator, InitializingBean {
    private static final String ID_KEY = "taskflow:id";
    public static RedisIdGenerator INSTANCE;

    private final StringRedisTemplate redisTemplate;

    private final TaskMapper taskMapper;


    @Override
    public Long getIdNum() {
        String id = redisTemplate.opsForValue().get(ID_KEY);
        if (id == null) {
            Long maxTaskId = Optional.ofNullable(taskMapper.getMaxTaskId()).orElse(0L);
            redisTemplate.opsForValue().setIfAbsent(ID_KEY, String.valueOf(maxTaskId));
        }
        return redisTemplate.opsForValue().increment(ID_KEY, 1);
    }

    @Override
    public String getIdStr() {
        return String.valueOf(getIdNum());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        RedisIdGenerator.INSTANCE = this;
    }
}
