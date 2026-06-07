package com.lezai.threadpool.bean;

import com.lezai.threadpool.enums.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigHistoryFileTest {

    @Test
    @DisplayName("constructor initializes with zero version")
    void constructor() {
        ConfigHistoryFile file = new ConfigHistoryFile("app1");

        assertThat(file.getAppId()).isEqualTo("app1");
        assertThat(file.getCurrentVersion()).isZero();
        assertThat(file.getPoolHistory()).isNull();
        assertThat(file.getLastUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("builder creates file correctly")
    void builder() {
        Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> history = new HashMap<>();
        history.put("pool-a", List.of(
                ChangeLogEntry.of(1, ChangeType.CREATE, null, ThreadPoolConfig.builder().poolName("pool-a").build())
        ));

        ConfigHistoryFile file = ConfigHistoryFile.builder()
                .appId("app1")
                .currentVersion(5)
                .poolHistory(history)
                .build();

        assertThat(file.getAppId()).isEqualTo("app1");
        assertThat(file.getCurrentVersion()).isEqualTo(5);
        assertThat(file.getPoolHistory()).containsKey("pool-a");
    }
}
