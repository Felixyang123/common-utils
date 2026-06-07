package com.lezai.threadpool.bean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHistoryFileTest {

    @Test
    @DisplayName("constructor initializes with zero version")
    void constructor() {
        ApiKeyHistoryFile file = new ApiKeyHistoryFile("app1");

        assertThat(file.getAppId()).isEqualTo("app1");
        assertThat(file.getCurrentVersion()).isZero();
        assertThat(file.getHistory()).isNull();
        assertThat(file.getLastUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("builder creates file correctly")
    void builder() {
        ApiKeyHistoryFile file = ApiKeyHistoryFile.builder()
                .appId("app1")
                .currentVersion(3)
                .build();

        assertThat(file.getAppId()).isEqualTo("app1");
        assertThat(file.getCurrentVersion()).isEqualTo(3);
    }
}
