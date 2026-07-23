package com.lezai.samples.cache.config;

import com.lezai.samples.cache.core.CacheManager;
import com.lezai.samples.cache.sync.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableConfigurationProperties({CacheMessageSyncProperties.class})
@ConditionalOnProperty(prefix = "cache.sync.redis-pub-sub", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SyncMessageAutoConfiguration {

    @Bean
    @ConditionalOnBean(name = "cacheRedisTemplate")
    public RedisCacheMessagePub redisCacheMessagePub(RedisTemplate<String, Object> redisTemplate,
                                                     CacheMessageSyncProperties props) {
        return new RedisCacheMessagePub(redisTemplate, props.getRedisPubSub().getChannel());
    }

    @Bean
    @ConditionalOnBean(name = "cacheRedisTemplate")
    public RedisCacheMessageSub redisCacheMessageSub(RedisTemplate<String, Object> redisTemplate,
                                                     CacheManager cacheManager) {
        return new RedisCacheMessageSub(redisTemplate, cacheManager);
    }

    @Bean
    @ConditionalOnBean(value = {RedisCacheMessageSub.class, RedisConnectionFactory.class})
    public RedisMessageListenerContainer cacheMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       RedisCacheMessageSub redisCacheMessageSub,
                                                                       ThreadPoolTaskExecutor cacheMessageSyncExecutor,
                                                                       CacheMessageSyncProperties props) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(cacheMessageSyncExecutor); // M6：消息在虚拟线程上异步处理
        container.addMessageListener(redisCacheMessageSub,
                new ChannelTopic(props.getRedisPubSub().getChannel()));
        return container;
    }

    @Bean
    @ConditionalOnBean(RedisCacheMessagePub.class)
    public CacheMessagePubSub cacheMessagePubSub(CacheMessagePub cacheMessagePub) {
        return new CacheMessagePubSub(cacheMessagePub);
    }

    /** 纯虚拟线程 executor（线程池参数对虚拟线程无意义，已移除）。 */
    @Bean
    public ThreadPoolTaskExecutor cacheMessageSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setVirtualThreads(true);
        executor.setThreadNamePrefix("cache-message-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnBean(RedisMessageListenerContainer.class)
    public CacheSyncHealthIndicator cacheSyncHealthIndicator(RedisMessageListenerContainer cacheMessageListenerContainer) {
        return new CacheSyncHealthIndicator(cacheMessageListenerContainer);
    }
}
