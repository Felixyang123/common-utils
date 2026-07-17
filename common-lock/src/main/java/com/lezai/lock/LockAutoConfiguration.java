package com.lezai.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableAspectJAutoProxy
public class LockAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "lockRedisTemplate")
    public StringRedisTemplate lockRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    public RedisDistributeLock redisDistributeLock(StringRedisTemplate lockRedisTemplate, WatchDogExecutor watchDogExecutor) {
        return new RedisDistributeLock(lockRedisTemplate, watchDogExecutor);
    }

    @Bean
    @ConditionalOnMissingBean(Lock.class)
    public Lock reentrantLock() {
        return new LocalReentrantLock();
    }

    @Bean
    public LockSupport lockSupport(Lock lock) {
        return new LockSupport(lock);
    }

    @Bean
    @ConditionalOnMissingBean(Lock.class)
    public Lock lock() {
        return new LocalLock();
    }

    @Bean
    public LockAspect lockAspect() {
        return new LockAspect();
    }

    @Bean
    public WatchDogExecutor watchDogExecutor(StringRedisTemplate lockRedisTemplate) {
        return new WatchDogExecutor(lockRedisTemplate);
    }

}
