package com.lezai.samples.cache.config;

import com.lezai.samples.cache.core.CacheManager;
import com.lezai.samples.cache.sync.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties({CacheMessageSyncProperties.class})
public class SyncMessageAutoConfiguration {
    @Autowired
    private CacheMessageSyncProperties cacheMessageSyncProperties;

    @Bean
    public RedisCacheMessagePub redisCacheMessagePub(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheMessagePub(redisTemplate, cacheMessageSyncProperties.getRedisPubSub().getChannel());
    }

    @Bean
    public RedisCacheMessageSub redisCacheMessageSub(RedisTemplate<String, Object> redisTemplate, CacheManager cacheManager) {
        return new RedisCacheMessageSub(redisTemplate, cacheManager, cacheMessageSyncProperties.getRedisPubSub().getChannel());
    }

    @Bean
    public CacheMessagePubSub cacheMessagePubSub(@Autowired(required = false) CacheMessagePub cacheMessagePub,
                                                 @Autowired(required = false) CacheMessageSub cacheMessageSub) {
        return new CacheMessagePubSub(cacheMessagePub, cacheMessageSub);
    }

    @Bean
    public ThreadPoolTaskExecutor cacheMessageSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        CacheMessageSyncProperties.ExecutorCfg cfg = cacheMessageSyncProperties.getExecutor();
        executor.setCorePoolSize(cfg.getCorePoolSize());
        executor.setMaxPoolSize(cfg.getMaxPoolSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setThreadNamePrefix("cache-message-sync-");
        executor.setVirtualThreads(true);
        executor.initialize();
        return executor;
    }

}
