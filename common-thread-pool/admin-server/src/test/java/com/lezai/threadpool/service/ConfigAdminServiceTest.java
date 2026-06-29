package com.lezai.threadpool.service;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.enums.ChangeType;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.storage.ConfigHistoryStorage;
import com.lezai.threadpool.storage.ConfigStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigAdminServiceTest {

    @Mock
    private ConfigStorage configStorage;

    @Mock
    private ConfigHistoryStorage historyStorage;

    private ConfigAdminService service;

    @BeforeEach
    void setUp() {
        service = new ConfigAdminService(configStorage, historyStorage);
    }

    // ==================== getAppConfig ====================

    @Test
    @DisplayName("getAppConfig returns ThreadPoolConfigResp when config exists")
    void getAppConfig_found() {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L).configs(configs).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        ThreadPoolConfigResp result = service.getAppConfig("app1");

        assertThat(result.getConfigVersion()).isEqualTo(5);
        assertThat(result.getConfigs()).hasSize(2);
    }

    @Test
    @DisplayName("getAppConfig throws ConfigNotFoundException when app not found")
    void getAppConfig_notFound() {
        when(configStorage.getAppConfig("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAppConfig("unknown"))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    // ==================== getConfig ====================

    @Test
    @DisplayName("getConfig returns config when found")
    void getConfig_found() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.getConfig("app1", "test-pool")).thenReturn(Optional.of(config));

        ThreadPoolConfig result = service.getConfig("app1", "test-pool");

        assertThat(result.getPoolName()).isEqualTo("test-pool");
    }

    @Test
    @DisplayName("getConfig throws ConfigNotFoundException when not found")
    void getConfig_notFound() {
        when(configStorage.getConfig("app1", "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConfig("app1", "unknown"))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    // ==================== saveConfigs ====================

    @Test
    @DisplayName("saveConfigs delegates to storage")
    void saveConfigs() {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");

        service.saveConfigs("app1", configs);

        verify(configStorage).saveConfigs("app1", configs);
    }

    // ==================== saveConfig ====================

    @Test
    @DisplayName("saveConfig delegates to storage")
    void saveConfig() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();

        service.saveConfig("app1", config);

        verify(configStorage).saveConfig("app1", config);
    }

    // ==================== deleteConfigs ====================

    @Test
    @DisplayName("deleteConfigs delegates to storage")
    void deleteConfigs() {
        service.deleteConfigs("app1");

        verify(configStorage).deleteConfigs("app1");
    }

    // ==================== deleteConfig ====================

    @Test
    @DisplayName("deleteConfig delegates to storage")
    void deleteConfig() {
        service.deleteConfig("app1", "test-pool");

        verify(configStorage).deleteConfig("app1", "test-pool");
    }

    // ==================== addConfig ====================

    @Test
    @DisplayName("addConfig delegates to storage and returns config")
    void addConfig() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.addConfig("app1", config)).thenReturn(config);

        ThreadPoolConfig result = service.addConfig("app1", config);

        assertThat(result).isEqualTo(config);
        verify(configStorage).addConfig("app1", config);
    }

    // ==================== addConfigs ====================

    @Test
    @DisplayName("addConfigs delegates to storage")
    void addConfigs() {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");
        when(configStorage.addConfigs("app1", configs)).thenReturn(configs);

        List<ThreadPoolConfig> result = service.addConfigs("app1", configs);

        assertThat(result).hasSize(2);
        verify(configStorage).addConfigs("app1", configs);
    }

    // ==================== getConfigVersion ====================

    @Test
    @DisplayName("getConfigVersion returns version number")
    void getConfigVersion() {
        when(configStorage.getConfigVersion("app1")).thenReturn(42L);

        long version = service.getConfigVersion("app1");

        assertThat(version).isEqualTo(42L);
    }

    // ==================== getConfigHistory ====================

    @Test
    @DisplayName("getConfigHistory returns history grouped by pool when app exists")
    void getConfigHistory_found() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L).configs(List.of()).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        List<ChangeLogEntry<ThreadPoolConfig>> poolHistory = List.of(
                ChangeLogEntry.<ThreadPoolConfig>builder()
                        .version(1).changeType(ChangeType.CREATE).timestamp(LocalDateTime.now()).build()
        );
        Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> historyMap = Map.of("pool-a", poolHistory);
        when(historyStorage.getAllHistory("app1")).thenReturn(historyMap);

        Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> result = service.getConfigHistory("app1");

        assertThat(result).containsKey("pool-a");
        assertThat(result.get("pool-a")).hasSize(1);
    }

    @Test
    @DisplayName("getConfigHistory throws ConfigNotFoundException when app not found")
    void getConfigHistory_notFound() {
        when(configStorage.getAppConfig("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConfigHistory("unknown"))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    // ==================== getPoolConfigHistory ====================

    @Test
    @DisplayName("getPoolConfigHistory returns history without limit")
    void getPoolConfigHistory_noLimit() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.getConfig("app1", "test-pool")).thenReturn(Optional.of(config));

        List<ChangeLogEntry<ThreadPoolConfig>> history = List.of(
                ChangeLogEntry.<ThreadPoolConfig>builder().version(1).changeType(ChangeType.CREATE).build(),
                ChangeLogEntry.<ThreadPoolConfig>builder().version(2).changeType(ChangeType.UPDATE).build()
        );
        when(historyStorage.getHistory("app1", "test-pool")).thenReturn(history);

        List<ChangeLogEntry<ThreadPoolConfig>> result = service.getPoolConfigHistory("app1", "test-pool", null);

        assertThat(result).hasSize(2);
        verify(historyStorage).getHistory("app1", "test-pool");
    }

    @Test
    @DisplayName("getPoolConfigHistory returns history with limit")
    void getPoolConfigHistory_withLimit() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.getConfig("app1", "test-pool")).thenReturn(Optional.of(config));

        List<ChangeLogEntry<ThreadPoolConfig>> history = List.of(
                ChangeLogEntry.<ThreadPoolConfig>builder().version(3).changeType(ChangeType.UPDATE).build()
        );
        when(historyStorage.getHistory("app1", "test-pool", 1)).thenReturn(history);

        List<ChangeLogEntry<ThreadPoolConfig>> result = service.getPoolConfigHistory("app1", "test-pool", 1);

        assertThat(result).hasSize(1);
        verify(historyStorage).getHistory("app1", "test-pool", 1);
    }

    @Test
    @DisplayName("getPoolConfigHistory throws ConfigNotFoundException when pool not found")
    void getPoolConfigHistory_poolNotFound() {
        when(configStorage.getConfig("app1", "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPoolConfigHistory("app1", "unknown", null))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("unknown");
    }
}
