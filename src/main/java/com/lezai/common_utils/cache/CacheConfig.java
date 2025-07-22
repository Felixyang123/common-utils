package com.lezai.common_utils.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    @ConditionalOnMissingBean(EnhanceCache.class)
    public EnhanceCache<Object> enhanceCache() {
        return new HashMapCache<>(1000);
    }
}
