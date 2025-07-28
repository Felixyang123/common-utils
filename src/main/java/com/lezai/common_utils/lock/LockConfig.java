package com.lezai.common_utils.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class LockConfig {

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
}
