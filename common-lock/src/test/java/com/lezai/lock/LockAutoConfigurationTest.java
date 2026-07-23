package com.lezai.lock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LockAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LockAutoConfiguration.class));

    @Test
    void fallsBackToSingleLocalLock_whenNoRedis() {
        runner.run(context -> {
            // 无 Redis：恰好一个 Lock bean，且为本地锁
            assertThat(context).hasSingleBean(Lock.class);
            assertThat(context.getBean(Lock.class))
                    .isInstanceOfAny(LocalLock.class, LocalReentrantLock.class);
            assertThat(context).doesNotHaveBean(RedisDistributeLock.class);
            assertThat(context).hasSingleBean(LockSupport.class);
        });
    }

    @Test
    void usesRedisLock_whenRedisAvailable() {
        runner.withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(Lock.class);
                    assertThat(context.getBean(Lock.class)).isInstanceOf(RedisDistributeLock.class);
                });
    }
}
