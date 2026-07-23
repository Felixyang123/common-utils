package com.lezai.samples.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.lezai.lock.annotation.EnableLock;
import com.lezai.samples.cache.core.*;
import com.lezai.samples.cache.impl.CaffeineCache;
import com.lezai.samples.cache.impl.CaffeineCacheManager;
import com.lezai.samples.cache.impl.HashMapCache;
import com.lezai.samples.cache.impl.HashMapCacheManager;
import com.lezai.samples.cache.impl.MultiCaffeineCache;
import com.lezai.samples.cache.impl.MultiHashMapCache;
import com.lezai.samples.cache.impl.MultiRemoteRedisCache;
import com.lezai.samples.cache.serializer.CachePayloadRedisSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties({CacheProperties.class, DegradationProperties.class, CacheSerializerProperties.class})
@EnableLock
public class CacheAutoConfiguration {
    @Autowired
    private CacheProperties cacheProperties;

    @Bean
    @ConditionalOnClass(Caffeine.class)
    @ConditionalOnMissingBean(Cache.class)
    public Cache<Object> globalCache() {
        CacheProperties.CaffeineCfg cfg = cacheProperties.getCaffeineCfg();
        return new CaffeineCache<>(cfg.getCacheSize(), cfg.getStaleGraceMs());
    }

    @Bean
    @ConditionalOnMissingBean(Cache.class)
    public Cache<Object> globalCacheFallback() {
        CacheProperties.GlobalCfg globalCfg = cacheProperties.getGlobalCfg();
        return new HashMapCache<>(globalCfg.getLocalCacheSize());
    }

    @Bean
    @ConditionalOnClass(Caffeine.class)
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(Cache<Object> globalCache) {
        CacheProperties.CaffeineCfg cfg = cacheProperties.getCaffeineCfg();
        return new CaffeineCacheManager(globalCache, cfg.getCacheSize(), cfg.getStaleGraceMs());
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManagerFallback(Cache<Object> globalCache) {
        CacheProperties.HashMapCacheCfg cfg = cacheProperties.getHashMapCacheCfg();
        return new HashMapCacheManager(globalCache, cfg.getCacheSize());
    }

    @Bean
    @ConditionalOnMissingBean(name = "cacheRedisTemplate")
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, Object> cacheRedisTemplate(RedisConnectionFactory factory,
                                                        CacheSerializerProperties serializerProperties) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setValueSerializer(new CachePayloadRedisSerializer(serializerProperties));
        template.setKeySerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "l1Cache")
    @ConditionalOnBean(name = "l2Cache")
    @ConditionalOnClass(Caffeine.class)
    public MultiCache<Object> multiCaffeineCache(@Qualifier(value = "l2Cache") MultiCache<Object> l2) {
        CacheProperties.CaffeineCfg cfg = cacheProperties.getCaffeineCfg();
        return new MultiCaffeineCache<>(cfg.getCacheSize(), cfg.getStaleGraceMs(), l2);
    }

    @Primary
    @Bean(name = "l2Cache")
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnBean(name = "cacheRedisTemplate")
    public MultiCache<Object> multiRemoteRedisCache(RedisTemplate<String, Object> cacheRedisTemplate) {
        return new MultiRemoteRedisCache<>(cacheRedisTemplate);
    }

    @Bean
    public MethodCacheAspect methodCacheAspect(CacheManager cacheManager) {
        return new MethodCacheAspect(cacheManager);
    }

    @Bean
    @ConditionalOnMissingBean(CacheTemplate.class)
    public CacheTemplate cacheTemplate(CacheManager cacheManager) {
        return new CacheTemplate(cacheManager);
    }

    @Bean
    public DegradationGuard degradationGuard(DegradationProperties degradationProperties) {
        DegradationGuard guard = degradationProperties.isEnabled()
                ? DegradationGuard.of(
                        degradationProperties.getPermitsPerSecond(),
                        degradationProperties.getBulkheadPermits(),
                        degradationProperties.getBulkheadWaitMs())
                : DegradationGuard.disabled();
        CacheDegradationSupport.init(guard, degradationProperties.getSingleFlightWaitMs());
        return guard;
    }
}
