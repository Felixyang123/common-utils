package com.lezai.samples.cache.config;

import com.lezai.samples.cache.core.CacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SyncMessageAutoConfigurationNoRedisTest {

    // 不提供任何 RedisTemplate bean —— 模拟无 Redis 环境
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SyncMessageAutoConfiguration.class))
            .withBean(CacheManager.class, () -> mock(CacheManager.class));

    @Test
    void syncBeansSkipped_whenNoRedisTemplate() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean("redisCacheMessagePub");
            assertThat(context).doesNotHaveBean("redisCacheMessageSub");
            assertThat(context).doesNotHaveBean("cacheMessagePubSub");
            // executor 不依赖 Redis，可保留
        });
    }
}
