package com.lezai.anti.duplicate.config;

import com.lezai.anti.duplicate.interceptor.DuplicateSubmitFilter;
import com.lezai.anti.duplicate.interceptor.DuplicateSubmitInterceptor;
import com.lezai.anti.duplicate.resolver.*;
import com.lezai.anti.duplicate.strategy.DuplicateSubmitStrategy;
import com.lezai.anti.duplicate.strategy.JdbcDuplicateSubmitStrategy;
import com.lezai.anti.duplicate.strategy.LocalDuplicateSubmitStrategy;
import com.lezai.anti.duplicate.strategy.RedisDuplicateSubmitStrategy;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({AntiDupProperties.class})
public class AntiDupAutoConfiguration implements WebMvcConfigurer {
    @Bean
    public DuplicateSubmitResultResolver resultResolver() {
        return new DefaultDuplicateSubmitResultResolver();
    }

    @Bean
    public TokenResolver tokenResolver() {
        return new AuthTokenResolver();
    }

    @Bean
    public KeyResolver keyResolver(TokenResolver tokenResolver) {
        return new KeyResolver(tokenResolver);
    }

    @Bean
    public Filter duplicateSubmitFilter(
            AntiDupProperties properties,
            DuplicateSubmitStrategy strategy,
            KeyResolver keyResolver,
            DuplicateSubmitResultResolver resultResolver) {
        return new DuplicateSubmitFilter(properties, strategy, keyResolver, resultResolver);
    }

    @Bean
    public HandlerInterceptor duplicateSubmitInterceptor(
            AntiDupProperties properties,
            DuplicateSubmitStrategy strategy,
            KeyResolver keyResolver,
            DuplicateSubmitResultResolver resultResolver) {
        return new DuplicateSubmitInterceptor(properties, strategy, keyResolver, resultResolver);
    }

    @Bean
    @ConditionalOnMissingBean(RedisTemplate.class)
    public StringRedisTemplate redisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    @ConditionalOnProperty(name = "antidup.strategy", havingValue = "local")
    public DuplicateSubmitStrategy localDuplicateSubmitStrategy() {
        return new LocalDuplicateSubmitStrategy();
    }

    @Bean
    @ConditionalOnProperty(name = "antidup.strategy", havingValue = "jdbc")
    public DuplicateSubmitStrategy jdbcDuplicateSubmitStrategy(JdbcTemplate jdbcTemplate) {
        return new JdbcDuplicateSubmitStrategy(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(DuplicateSubmitStrategy.class)
    public DuplicateSubmitStrategy redisDuplicateSubmitStrategy(StringRedisTemplate redisTemplate) {
        return new RedisDuplicateSubmitStrategy(redisTemplate);
    }

    @Bean
    public WebConfig webConfig(DuplicateSubmitInterceptor duplicateSubmitInterceptor) {
        return new WebConfig(duplicateSubmitInterceptor);
    }

}