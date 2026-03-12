package com.lezai.samples.cache;

import com.lezai.samples.cache.config.CacheProperties;
import com.lezai.samples.cache.core.MultiCache;
import com.lezai.samples.cache.impl.MultiHashMapCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CacheConfig {
    @Autowired
    private CacheProperties cacheProperties;

    @Bean(name = {"lockRedisTemplate", "idempotentRedisTemplate", "dupRedisTemplate", "rateLimitRedisTemplate"})
    public StringRedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(redisConnectionFactory);
        return template;
    }

    @Bean
    public MultiHashMapCacheManager cacheManager(@Qualifier(value = "l1Cache") MultiCache<Object> l1Cache,
                                                 @Qualifier(value = "l2Cache") MultiCache<Object> l2Cache) {
        CacheProperties.GlobalCfg globalCfg = cacheProperties.getGlobalCfg();
        return new MultiHashMapCacheManager(l1Cache, l2Cache, globalCfg.getLocalCacheSize());
    }
}
