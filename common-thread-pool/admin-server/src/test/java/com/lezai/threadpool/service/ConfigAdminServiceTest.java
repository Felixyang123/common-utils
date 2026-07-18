package com.lezai.threadpool.service;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.pojo.bean.ConfigSnapshot;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
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

    @Mock
    private ThreadPoolConfigConverter configConverter;

    @Mock
    private ApiKeyConverter apiKeyConverter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ConfigAdminService service;

    @BeforeEach
    void setUp() {
        service = new ConfigAdminService(configStorage, snapshotStorage, apiKeyStorage, persistenceService, configConverter, apiKeyConverter, eventPublisher);
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

    @Test
    @DisplayName("write methods have rollbackFor Exception transactional boundary")
    void writeMethodsHaveRollbackForException() throws Exception {
        assertTransactional("saveConfig", String.class, ThreadPoolConfig.class);
        assertTransactional("saveConfigs", String.class, List.class);
        assertTransactional("deleteConfigs", String.class);
        assertTransactional("deleteConfig", String.class, String.class);
        assertTransactional("rollback", String.class, String.class, long.class);
        assertTransactional("createApp", com.lezai.threadpool.pojo.request.CreateAppRequest.class);
        assertTransactional("deleteApp", String.class);
        assertTransactional("restoreConfig", String.class, String.class);
        assertTransactional("restoreConfigs", String.class);
    }

    @Test
    @DisplayName("createApp writes api key before app entry and propagates exception on app entry failure")
    void createAppFailurePropagatesForTransactionRollback() {
        var request = new com.lezai.threadpool.pojo.request.CreateAppRequest();
        request.setAppId("app1");
        request.setAppName("App 1");
        when(apiKeyStorage.putIfAbsent(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("app entry failed"))
                .when(persistenceService).createAppEntry("app1");

        assertThatThrownBy(() -> service.createApp(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app entry failed");

        verify(apiKeyStorage).putIfAbsent(org.mockito.ArgumentMatchers.any());
        verify(persistenceService).createAppEntry("app1");
    }

    private void assertTransactional(String methodName, Class<?>... paramTypes) throws Exception {
        var annotation = ConfigAdminService.class.getMethod(methodName, paramTypes)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.rollbackFor()).contains(Exception.class);
    }
}




