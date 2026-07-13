package com.lezai.threadpool.bean;

import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadPoolAppConfigTest {

    @Test
    @DisplayName("builder creates config correctly")
    void builder() {
        ThreadPoolConfig config = ThreadPoolConfig.builder().poolName("test").corePoolSize(4).build();
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1")
                .configVersion(3)
                .configs(List.of(config))
                .build();

        assertThat(appConfig.getAppId()).isEqualTo("app1");
        assertThat(appConfig.getConfigVersion()).isEqualTo(3);
        assertThat(appConfig.getConfigs()).hasSize(1);
        assertThat(appConfig.getConfigs().get(0).getPoolName()).isEqualTo("test");
    }

    @Test
    @DisplayName("default values")
    void defaults() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder().appId("app1").build();

        assertThat(appConfig.getAppId()).isEqualTo("app1");
        assertThat(appConfig.getConfigVersion()).isZero();
        assertThat(appConfig.getConfigs()).isNull();
    }
}

