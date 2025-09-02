package com.lezai.samples.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cache")
@Data
public class CacheProperties {
    private HashMapCacheCfg hashMapCacheCfg = new HashMapCacheCfg();
    private GlobalCfg globalCfg = new GlobalCfg();

    @Data
    public static class HashMapCacheCfg {
        private int cacheSize = 1000;
    }

    @Data
    public static class GlobalCfg {
        private int localCacheSize = 1000;
    }
}
