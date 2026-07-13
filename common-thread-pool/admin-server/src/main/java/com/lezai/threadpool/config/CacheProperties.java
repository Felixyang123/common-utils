package com.lezai.threadpool.config;

import com.lezai.threadpool.storage.cache.CacheConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "threadpool.cache")
public class CacheProperties {

    private CacheConfig defaultConfig = CacheConfig.builder()
            .ttl(Duration.ofMinutes(10))
            .maxSize(5000)
            .build();

    private Duration nullValueTtl = Duration.ofMinutes(1);

    private Map<String, CacheConfig> configs = new HashMap<>();
}
