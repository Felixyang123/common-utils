package com.lezai.threadpool.config;

import com.lezai.threadpool.manager.ThreadPoolManager;
import com.lezai.threadpool.metrics.ThreadPoolMetricsBinder;
import com.lezai.threadpool.properties.ThreadPoolProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer 可观测性自动配置。
 * <p>
 * 仅当 Micrometer 在 classpath 上时才加载此配置类——
 * {@code @ConditionalOnClass} 在类级别确保没有 Micrometer 时整个类被跳过，
 * 从而不会触发 {@link ThreadPoolMetricsBinder}（实现了 {@code MeterBinder}）的类加载。
 */
@Slf4j
@Configuration
@ConditionalOnClass(MeterRegistry.class)
public class ThreadPoolMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    public ThreadPoolMetricsBinder threadPoolMetricsBinder(
            ThreadPoolManager threadPoolManager,
            ThreadPoolProperties properties) {
        log.info("Micrometer detected, registering ThreadPoolMetricsBinder");
        return new ThreadPoolMetricsBinder(threadPoolManager, properties.getRemote().getAppId());
    }
}
