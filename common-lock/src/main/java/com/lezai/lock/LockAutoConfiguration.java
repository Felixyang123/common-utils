package com.lezai.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
@EnableAspectJAutoProxy
public class LockAutoConfiguration {

    @Bean
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
    public WatchDogExecutor watchDogExecutor(RedisTemplate<String, Object> redisTemplate) {
        return new WatchDogExecutor(redisTemplate);
    }

    @Bean
    public RedisDistributeLock redisDistributeLock(RedisTemplate<String, Object> redisTemplate, WatchDogExecutor watchDogExecutor) {
        return new RedisDistributeLock(redisTemplate, watchDogExecutor);
    }
}
