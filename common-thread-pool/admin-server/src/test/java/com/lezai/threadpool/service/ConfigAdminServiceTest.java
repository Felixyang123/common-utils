package com.lezai.threadpool.service;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ConfigSnapshot;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.storage.ApiKeyStorage;
import com.lezai.threadpool.storage.ConfigSnapshotStorage;
import com.lezai.threadpool.storage.ConfigStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigAdminServiceTest {

    @Mock
    private ConfigStorage configStorage;

    @Mock
    private ConfigSnapshotStorage snapshotStorage;

    @Mock
    private ApiKeyStorage apiKeyStorage;

    @Mock
    private ThreadPoolConfigPersistenceService persistenceService;

    private ConfigAdminService service;

    @BeforeEach
    void setUp() {
        service = new ConfigAdminService(configStorage, snapshotStorage, apiKeyStorage, persistenceService);
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
                .isInstanceOf(ResourceNotFoundException.class)
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
                .isInstanceOf(ResourceNotFoundException.class)
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

    // ==================== getSnapshots ====================

    @Test
    @DisplayName("getSnapshots returns snapshots without limit")
    void getSnapshots_noLimit() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.getConfig("app1", "test-pool")).thenReturn(Optional.of(config));

        List<ConfigSnapshot> snapshots = List.of(
                ConfigSnapshot.builder().appId("app1").poolName("test-pool").version(1).build(),
                ConfigSnapshot.builder().appId("app1").poolName("test-pool").version(2).build()
        );
        when(snapshotStorage.getSnapshots("app1", "test-pool")).thenReturn(snapshots);

        List<ConfigSnapshot> result = service.getSnapshots("app1", "test-pool", null);

        assertThat(result).hasSize(2);
        verify(snapshotStorage).getSnapshots("app1", "test-pool");
    }

    @Test
    @DisplayName("getSnapshots returns snapshots with limit")
    void getSnapshots_withLimit() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.getConfig("app1", "test-pool")).thenReturn(Optional.of(config));

        List<ConfigSnapshot> snapshots = List.of(
                ConfigSnapshot.builder().appId("app1").poolName("test-pool").version(2).build()
        );
        when(snapshotStorage.getSnapshots("app1", "test-pool", 1)).thenReturn(snapshots);

        List<ConfigSnapshot> result = service.getSnapshots("app1", "test-pool", 1);

        assertThat(result).hasSize(1);
        verify(snapshotStorage).getSnapshots("app1", "test-pool", 1);
    }

    @Test
    @DisplayName("getSnapshots throws ConfigNotFoundException when pool not found")
    void getSnapshots_poolNotFound() {
        when(configStorage.getConfig("app1", "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSnapshots("app1", "unknown", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");
    }
}
