package com.lezai.samples.cache.sync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheSyncHealthIndicatorTest {

    @Test
    void up_whenContainerRunning() {
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        when(container.isRunning()).thenReturn(true);
        Health h = new CacheSyncHealthIndicator(container).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsEntry("state", "active");
    }

    @Test
    void down_whenContainerNotRunning() {
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        when(container.isRunning()).thenReturn(false);
        Health h = new CacheSyncHealthIndicator(container).health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsEntry("state", "inactive");
    }
}
