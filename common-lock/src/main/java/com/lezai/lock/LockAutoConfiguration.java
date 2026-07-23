package com.lezai.lock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableAspectJAutoProxy
public class LockAutoConfiguration {

    // ---- Lock bean：有 Redis 用分布式锁，否则单个本地锁 ----
    // 用 ObjectProvider 在方法内判定，避免 @ConditionalOnBean 放在 @Bean 方法上时
    // 因 bean 处理顺序导致的不可靠判定（Spring 官方不推荐在 @Bean 方法上用 @ConditionalOnBean）。

    @Bean
    @ConditionalOnMissingBean(Lock.class)
    public Lock lock(ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider) {
        RedisConnectionFactory redisConnectionFactory = redisConnectionFactoryProvider.getIfAvailable();
        if (redisConnectionFactory != null) {
            StringRedisTemplate template = new StringRedisTemplate(redisConnectionFactory);
            WatchDogExecutor executor = new WatchDogExecutor(template);
            return new RedisDistributeLock(template, executor);
        }
        return new LocalLock();
    }

    // ---- 公共 ----

    @Bean
    public LockSupport lockSupport(Lock lock) {
        return new LockSupport(lock);
    }

    @Bean
    public LockAspect lockAspect() {
        return new LockAspect();
    }
}
