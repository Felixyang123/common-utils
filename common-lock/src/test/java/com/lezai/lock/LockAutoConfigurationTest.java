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
            assertThat(context).hasSingleBean(Lock.class);
            assertThat(context.getBean(Lock.class)).isInstanceOf(LocalLock.class);
            assertThat(context).doesNotHaveBean(RedisDistributeLock.class);
            assertThat(context).doesNotHaveBean(WatchDogExecutor.class);
            assertThat(context).hasSingleBean(LockSupport.class);
        });
    }

    @Test
    void usesRedisLockAndWatchDog_whenRedisAvailable() {
        runner.withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(Lock.class);
                    assertThat(context.getBean(Lock.class)).isInstanceOf(RedisDistributeLock.class);
                    assertThat(context).hasSingleBean(WatchDogExecutor.class);
                    // WatchDogExecutor 实现 SmartLifecycle，Spring 会调 start()
                    assertThat(context.getBean(WatchDogExecutor.class)).isInstanceOf(WatchDogExecutor.class);
                });
    }
}
