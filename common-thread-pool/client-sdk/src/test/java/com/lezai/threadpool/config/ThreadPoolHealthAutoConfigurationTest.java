package com.lezai.threadpool.config;

import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.health.ThreadPoolHealthIndicator;
import com.lezai.threadpool.manager.ThreadPoolManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ThreadPoolHealthAutoConfiguration")
class ThreadPoolHealthAutoConfigurationTest {

    @Configuration
    static class TestSupportConfig {
        @Bean
        ThreadPoolManager threadPoolManager() {
            return new ThreadPoolManager();
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestSupportConfig.class, ThreadPoolHealthAutoConfiguration.class);

    @Test
    @DisplayName("registers ThreadPoolHealthIndicator when Actuator and ThreadPoolManager are present")
    void registersHealthIndicator() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ThreadPoolHealthIndicator.class));
    }

    @Test
    @DisplayName("thread.pool.health.* properties override the default 0.9 thresholds")
    void configuredThresholds_takeEffect() throws Exception {
        contextRunner
                .withPropertyValues("thread.pool.health.active-thread-threshold=0.1")
                .run(context -> {
                    ThreadPoolManager manager = context.getBean(ThreadPoolManager.class);
                    manager.registerPool(ThreadPoolConfig.builder()
                            .poolName("probe").corePoolSize(2).maximumPoolSize(4)
                            .keepAliveTime(1).timeUnit(TimeUnit.SECONDS).queueCapacity(10).build());
                    var pool = manager.getRequiredPool("probe");
                    var block = new java.util.concurrent.CountDownLatch(1);
                    pool.execute(() -> {
                        try { block.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                    });
                    Thread.sleep(100);

                    ThreadPoolHealthIndicator indicator = context.getBean(ThreadPoolHealthIndicator.class);
                    // active ratio = 1/4 = 0.25, which is < the 0.9 default but >= the configured 0.1 override
                    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);

                    block.countDown();
                    manager.shutdownNow();
                });
    }
}
