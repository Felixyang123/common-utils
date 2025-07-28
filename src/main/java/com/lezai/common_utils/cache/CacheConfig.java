package com.lezai.common_utils.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableAspectJAutoProxy
public class CacheConfig {

    @Bean
    @ConditionalOnMissingBean(EnhanceCache.class)
    public EnhanceCache<Object> enhanceCache() {
        return new HashMapCache<>(1000);
    }

    @ConditionalOnMissingBean(RedisTemplate.class)
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 使用Jackson2JsonRedisSerializer来序列化和反序列化redis的value值
        ObjectMapper mapper = new ObjectMapper();
        // 取消Javabean转换
//        mapper.deactivateDefaultTyping();
        // 打开Javabean转换
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);

        // 设置value的序列化规则和key的序列化规则
        template.setValueSerializer(serializer);
        template.setKeySerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "firstLevelCache")
    public MultiCache<Object> multiHashMapCache(@Qualifier(value = "secondLevelCache") MultiCache<Object> multiCache) {
        return new MultiHashMapCache<>(1000, multiCache);
    }

    @Primary
    @Bean(name = "secondLevelCache")
    public MultiCache<Object> multiRemoteRedisCache(RedisTemplate<String, Object> redisTemplate) {
        return new MultiRemoteRedisCache<>(redisTemplate);
    }

    @Bean
    public MethodCacheAspect methodCacheAspect() {
        return new MethodCacheAspect();
    }
}
