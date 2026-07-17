package com.lezai.samples.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cache")
@Data
public class CacheProperties {
    private HashMapCacheCfg hashMapCacheCfg = new HashMapCacheCfg();
    private GlobalCfg globalCfg = new GlobalCfg();
    private NodeCfg node = new NodeCfg();

    @Data
    public static class HashMapCacheCfg {
        private int cacheSize = 1000;

        private long ttl = 60 * 1000;
    }

    @Data
    public static class GlobalCfg {
        private int localCacheSize = 1000;

        private long localCacheTtl = 60 * 1000;

        private long redisCacheTtl = 60 * 60 * 1000;
    }

    @Data
    public static class NodeCfg {
        /**
         * 当前节点地址，用于分布式缓存同步注册
         */
        private String address = "127.0.0.1:8080";
    }
}
