package com.lezai.threadpool.config;

import com.lezai.threadpool.health.ThreadPoolHealthIndicator;
import com.lezai.threadpool.health.ThreadPoolHealthProperties;
import com.lezai.threadpool.manager.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 线程池健康检查自动配置。
 * <p>
 * 仅当 Spring Boot Actuator 在 classpath 上时才加载此配置类——
 * {@code @ConditionalOnClass} 在类级别确保没有 Actuator 时整个类被跳过，
 * 从而不会触发 {@link ThreadPoolHealthIndicator}（实现了 {@code HealthIndicator}）的类加载。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ThreadPoolHealthProperties.class)
@ConditionalOnClass(HealthIndicator.class)
public class ThreadPoolHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ThreadPoolManager.class)
    public ThreadPoolHealthIndicator threadPoolHealthIndicator(ThreadPoolManager threadPoolManager,
                                                                ThreadPoolHealthProperties healthProperties) {
        log.info("Actuator detected, registering ThreadPoolHealthIndicator: activeThreadThreshold={}, queueUsageThreshold={}",
                healthProperties.getActiveThreadThreshold(), healthProperties.getQueueUsageThreshold());
        return new ThreadPoolHealthIndicator(threadPoolManager, healthProperties.getActiveThreadThreshold(), healthProperties.getQueueUsageThreshold());
    }
}
