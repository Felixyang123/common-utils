package com.lezai.taskflow.config;

import com.lezai.taskflow.idgen.RedisIdGenerator;
import com.lezai.taskflow.storage.mysql.MysqlTaskStorageAutoConfiguration;
import com.lezai.taskflow.storage.mysql.TaskMapper;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ImportAutoConfiguration(MysqlTaskStorageAutoConfiguration.class)
public class TaskFlowAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "flowRedisTemplate")
    public StringRedisTemplate flowRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    public RedisIdGenerator idGenerator(StringRedisTemplate flowRedisTemplate, TaskMapper taskMapper) {
        return new RedisIdGenerator(flowRedisTemplate, taskMapper);
    }
}
