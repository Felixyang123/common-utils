package com.lezai.threadpool.config;

import com.lezai.threadpool.manager.ThreadPoolManager;
import com.lezai.threadpool.metrics.ThreadPoolMetricsBinder;
import com.lezai.threadpool.properties.ThreadPoolProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 appId 解析顺序：CS 模式 remote.appId → spring.application.name → "unknown"。
 * LOCAL 模式下不应采用 remote.appId 的占位默认值（否则所有 LOCAL 应用的指标都会被误标为 "default-app"）。
 */
@DisplayName("ThreadPoolMetricsAutoConfiguration appId resolution")
class ThreadPoolMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestSupportConfig.class, ThreadPoolMetricsAutoConfiguration.class)
            .withPropertyValues("thread.pool.enabled=true");

    @Configuration
    @EnableConfigurationProperties(ThreadPoolProperties.class)
    static class TestSupportConfig {
        @Bean
        ThreadPoolManager threadPoolManager() {
            return new ThreadPoolManager();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private String boundAppId(ApplicationContextRunner runner) {
        java.util.concurrent.atomic.AtomicReference<String> result = new java.util.concurrent.atomic.AtomicReference<>();
        runner.run(context -> {
            ThreadPoolMetricsBinder binder = context.getBean(ThreadPoolMetricsBinder.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            ThreadPoolManager manager = context.getBean(ThreadPoolManager.class);
            manager.registerPool(com.lezai.threadpool.bean.ThreadPoolConfig.builder()
                    .poolName("probe-pool").corePoolSize(1).maximumPoolSize(1)
                    .keepAliveTime(1).timeUnit(java.util.concurrent.TimeUnit.SECONDS).queueCapacity(10).build());
            binder.bindTo(registry);
            result.set(registry.get("threadpool.threads.core").meter().getId().getTag("app"));
            manager.shutdownNow();
        });
        return result.get();
    }

    @Test
    @DisplayName("LOCAL mode with spring.application.name set uses the application name, not remote.appId placeholder")
    void localMode_withApplicationName_usesApplicationName() {
        String appId = boundAppId(contextRunner
                .withPropertyValues("spring.application.name=order-service"));

        assertThat(appId).isEqualTo("order-service");
    }

    @Test
    @DisplayName("LOCAL mode without spring.application.name falls back to \"unknown\"")
    void localMode_noApplicationName_fallsBackToUnknown() {
        String appId = boundAppId(contextRunner);

        assertThat(appId).isEqualTo("unknown");
    }

    @Test
    @DisplayName("CS mode uses thread.pool.remote.app-id even when spring.application.name is set")
    void csMode_usesRemoteAppId() {
        String appId = boundAppId(contextRunner
                .withPropertyValues(
                        "thread.pool.remote.enabled=true",
                        "thread.pool.remote.app-id=cs-client-app",
                        "thread.pool.remote.api-key=test-key",
                        "spring.application.name=order-service"));

        assertThat(appId).isEqualTo("cs-client-app");
    }
}
