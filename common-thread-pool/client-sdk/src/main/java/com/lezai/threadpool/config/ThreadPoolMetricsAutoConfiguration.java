package com.lezai.threadpool.config;

import com.lezai.threadpool.manager.ThreadPoolManager;
import com.lezai.threadpool.metrics.ThreadPoolMetricsBinder;
import com.lezai.threadpool.properties.ThreadPoolProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${spring.application.name:}")
    private String applicationName;

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    public ThreadPoolMetricsBinder threadPoolMetricsBinder(
            ThreadPoolManager threadPoolManager,
            ThreadPoolProperties properties) {
        log.info("Micrometer detected, registering ThreadPoolMetricsBinder");
        return new ThreadPoolMetricsBinder(threadPoolManager, resolveAppId(properties));
    }

    /**
     * appId 解析顺序：CS 模式下用 {@code thread.pool.remote.app-id}（客户端身份标识）→
     * {@code spring.application.name} → {@code "unknown"} 兜底。
     * <p>
     * LOCAL 模式下 {@code remote.appId} 只是一个未被使用的占位默认值，直接拿来当 metrics
     * tag 会产生误导性的 "default-app" 标签，因此仅 CS 模式启用时才采用它。
     */
    private String resolveAppId(ThreadPoolProperties properties) {
        ThreadPoolProperties.RemoteConfig remote = properties.getRemote();
        if (remote.isEnabled() && StringUtils.isNotBlank(remote.getAppId())) {
            return remote.getAppId();
        }
        if (StringUtils.isNotBlank(applicationName)) {
            return applicationName;
        }
        return "unknown";
    }
}
