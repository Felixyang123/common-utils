package com.lezai.samples.cache.config;

import com.lezai.samples.cache.core.CacheManager;
import com.lezai.samples.cache.sync.CacheMessagePubSub;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyncMessageContainerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SyncMessageAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> {
                RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
                RedisConnection conn = mock(RedisConnection.class);
                when(factory.getConnection()).thenReturn(conn);
                when(conn.isSubscribed()).thenReturn(false);
                // 模拟真实连接：订阅完成后回调 onChannelSubscribed / onPatternSubscribed，让容器启动 future 结束。
                // 注意：Mockito 对 varargs 会展开 byte[][] 为多个 byte[]，需兼容两种形态收集通道。
                doAnswer(invocation -> {
                    SubscriptionListener listener = (SubscriptionListener) invocation.getArgument(0);
                    java.util.List<byte[]> channels = new java.util.ArrayList<>();
                    for (int i = 1; i < invocation.getArguments().length; i++) {
                        Object arg = invocation.getArguments()[i];
                        if (arg instanceof byte[][]) {
                            channels.addAll(java.util.Arrays.asList((byte[][]) arg));
                        } else if (arg instanceof byte[]) {
                            channels.add((byte[]) arg);
                        }
                    }
                    for (byte[] channel : channels) {
                        listener.onChannelSubscribed(channel, channels.size());
                    }
                    return null;
                }).when(conn).subscribe(any(MessageListener.class), any(byte[][].class));
                doAnswer(invocation -> {
                    SubscriptionListener listener = (SubscriptionListener) invocation.getArgument(0);
                    java.util.List<byte[]> patterns = new java.util.ArrayList<>();
                    for (int i = 1; i < invocation.getArguments().length; i++) {
                        Object arg = invocation.getArguments()[i];
                        if (arg instanceof byte[][]) {
                            patterns.addAll(java.util.Arrays.asList((byte[][]) arg));
                        } else if (arg instanceof byte[]) {
                            patterns.add((byte[]) arg);
                        }
                    }
                    for (byte[] pattern : patterns) {
                        listener.onPatternSubscribed(pattern, patterns.size());
                    }
                    return null;
                }).when(conn).pSubscribe(any(MessageListener.class), any(byte[][].class));
                return factory;
            })
            .withBean("cacheRedisTemplate", RedisTemplate.class, () -> mock(RedisTemplate.class))
            .withBean(CacheManager.class, () -> mock(CacheManager.class));

    @Test
    void listenerContainerIsCreated_andUsesExecutor() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RedisMessageListenerContainer.class);
            assertThat(context).hasBean("cacheMessageSyncExecutor");
            assertThat(context).hasBean("cacheMessagePubSub");
            assertThat(context.getBean("cacheMessagePubSub")).isInstanceOf(CacheMessagePubSub.class);
        });
    }
}
