package com.lezai.samples.cache.config;

import com.lezai.samples.cache.core.CacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SyncMessageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SyncMessageAutoConfiguration.class))
            .withBean("cacheRedisTemplate", RedisTemplate.class, () -> mock(RedisTemplate.class))
            .withBean(CacheManager.class, () -> mock(CacheManager.class));

    @Test
    void syncBeansCreated_byDefault() {
        runner.run(context -> {
            assertThat(context).hasBean("redisCacheMessagePub");
            assertThat(context).hasBean("redisCacheMessageSub");
            assertThat(context).hasBean("cacheMessagePubSub");
            assertThat(context).hasBean("cacheMessageSyncExecutor");
        });
    }

    @Test
    void syncBeansSkipped_whenDisabled() {
        runner.withPropertyValues("cache.sync.redis-pub-sub.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("redisCacheMessagePub");
                    assertThat(context).doesNotHaveBean("redisCacheMessageSub");
                    assertThat(context).doesNotHaveBean("cacheMessagePubSub");
                    assertThat(context).doesNotHaveBean("cacheMessageSyncExecutor");
                });
    }
}
