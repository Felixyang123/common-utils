package com.lezai.samples.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cache.sync")
@Data
public class CacheMessageSyncProperties {
    private RedisPubSub redisPubSub = new RedisPubSub();
    private ExecutorCfg executor = new ExecutorCfg();


    @Data
    public static class RedisPubSub {
        /**
         * Enable Redis Pub/Sub for cache synchronization
         */
        private boolean enabled = true;

        private String channel = "cache-sync-channel";
    }

    @Data
    public static class ExecutorCfg {
        private int corePoolSize = 4;
        private int maxPoolSize = 20;
        private int queueCapacity = 1000;
    }
}
