package com.lezai.samples.cache.config;

import com.lezai.lock.LockAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NoRedisBootSmokeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CacheAutoConfiguration.class,
                    SyncMessageAutoConfiguration.class,
                    LockAutoConfiguration.class));

    @Test
    void contextRefreshes_withoutRedis() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("globalCache");
            assertThat(context).hasBean("cacheManager");
            assertThat(context).hasBean("cacheTemplate");
            assertThat(context).doesNotHaveBean("cacheRedisTemplate");
            assertThat(context).doesNotHaveBean("redisCacheMessagePub");
        });
    }
}
