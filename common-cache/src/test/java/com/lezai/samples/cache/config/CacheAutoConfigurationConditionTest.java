package com.lezai.samples.cache.config;

import com.lezai.samples.cache.core.CacheManager;
import com.lezai.samples.cache.core.CacheTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAutoConfigurationConditionTest {

    // 不引入 LockAutoConfiguration，仅评估 CacheAutoConfiguration 自身的条件 bean
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

    @Test
    void redisBeansAreSkipped_whenNoRedisConnectionFactory() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean("cacheRedisTemplate");
            assertThat(context).doesNotHaveBean("l2Cache");
            assertThat(context).doesNotHaveBean("l1Cache");
            assertThat(context).doesNotHaveBean(RedisTemplate.class);
        });
    }

    @Test
    void localBeansArePresent_evenWithoutRedis() {
        runner.run(context -> {
            assertThat(context).hasBean("globalCache");
            assertThat(context).hasBean("cacheManager");
            assertThat(context).hasBean("cacheTemplate");
            assertThat(context).hasBean("methodCacheAspect");
            assertThat(context).hasBean("degradationGuard");
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context).hasSingleBean(CacheTemplate.class);
        });
    }
}
