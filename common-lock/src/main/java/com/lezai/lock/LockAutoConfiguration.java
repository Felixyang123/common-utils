package com.lezai.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableAspectJAutoProxy
public class LockAutoConfiguration {

    // ---- Redis 路径：仅当 RedisConnectionFactory 存在时 ----
    // 嵌套 @Configuration + 类级 @ConditionalOnBean 是可靠的条件判断
    // （方法级 @ConditionalOnBean 不可靠，Spring 官方不推荐）

    @Configuration
    @ConditionalOnBean(RedisConnectionFactory.class)
    static class RedisLockConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "lockRedisTemplate")
        public StringRedisTemplate lockRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
            return new StringRedisTemplate(redisConnectionFactory);
        }

        @Bean
        public WatchDogExecutor watchDogExecutor(StringRedisTemplate lockRedisTemplate) {
            return new WatchDogExecutor(lockRedisTemplate);
        }

        @Bean
        @ConditionalOnMissingBean(Lock.class)
        public Lock redisLock(StringRedisTemplate lockRedisTemplate, WatchDogExecutor watchDogExecutor) {
            return new RedisDistributeLock(lockRedisTemplate, watchDogExecutor);
        }
    }

    // ---- 本地路径：仅当无任何 Lock bean 时 ----

    @Bean
    @ConditionalOnMissingBean(Lock.class)
    public Lock localLock() {
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
