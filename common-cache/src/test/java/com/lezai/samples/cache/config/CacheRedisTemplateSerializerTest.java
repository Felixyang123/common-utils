package com.lezai.samples.cache.config;

import com.lezai.samples.cache.serializer.CachePayloadRedisSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CacheRedisTemplateSerializerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    @Test
    @SuppressWarnings("unchecked")
    void cacheRedisTemplate_usesCustomSerializer() {
        runner.run(context -> {
            RedisTemplate<String, Object> t = context.getBean("cacheRedisTemplate", RedisTemplate.class);
            assertThat(t.getValueSerializer()).isInstanceOf(CachePayloadRedisSerializer.class);
        });
    }
}
