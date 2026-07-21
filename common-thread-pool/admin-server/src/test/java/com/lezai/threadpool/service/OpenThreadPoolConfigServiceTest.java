package com.lezai.threadpool.service;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.exception.ResourceNotModifiedException;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.StatsStorage;
import com.lezai.threadpool.storage.listener.ConfigChangeListener;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class OpenThreadPoolConfigServiceTest {

    @Mock
    private ConfigStorage configStorage;

    @Mock
    private StatsStorage statsStorage;

    @Mock
    private ScheduledExecutorService subscriptionExecutor;

    @Mock
    private ConfigChangeListenerManager listenerManager;

    private OpenThreadPoolConfigService service;

    @BeforeEach
    void setUp() {
        service = new OpenThreadPoolConfigService(configStorage, statsStorage, subscriptionExecutor, listenerManager);
    }

    @Test
    @DisplayName("addConfig delegates to storage and returns the config")
    void addConfig() {
    }

    @Test
    @DisplayName("addConfigs delegates to storage")
    void addConfigs() {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");
        AddConfigAppResult result = new AddConfigAppResult();
        result.setAddedConfigs(configs);
        when(configStorage.addConfigs("app1", configs)).thenReturn(result);

        AddConfigAppResult serviceResult = service.addConfigs("app1", configs);

        assertThat(serviceResult.getAddedConfigs()).hasSize(2);
        verify(configStorage).addConfigs("app1", configs);
    }

    @Test
    @DisplayName("pullConfigs returns config when version is greater")
    void pullConfigs_newerVersion() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        ThreadPoolAppConfig result = service.pullConfigs("app1", 3L);

        assertThat(result.getConfigVersion()).isEqualTo(5);
    }

    @Test
    @DisplayName("pullConfigs returns config when version is null")
    void pullConfigs_noVersion() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5).configs(List.of()).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        ThreadPoolAppConfig result = service.pullConfigs("app1", null);

        assertThat(result.getConfigVersion()).isEqualTo(5);
    }

    @Test
    @DisplayName("pullConfigs throws ConfigNotModifiedException when version is same")
    void pullConfigs_notModified() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5).configs(List.of()).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        assertThatThrownBy(() -> service.pullConfigs("app1", 5L))
                .isInstanceOf(ResourceNotModifiedException.class);
    }

    @Test
    @DisplayName("pullConfigs throws ConfigNotFoundException when app not found")
    void pullConfigs_notFound() {
        when(configStorage.getAppConfig("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pullConfigs("unknown", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("reportStats saves stats list")
    void reportStats() {
        ThreadPoolStatsReport report = new ThreadPoolStatsReport();
        report.setAppId("app1");
        report.setStatsList(List.of(TestDataFactory.defaultStats().build()));

        service.reportStats(report);

        verify(statsStorage).saveStats(anyString(), any());
    }

    @Test
    @DisplayName("reportStats with empty stats list does nothing")
    void reportStats_emptyList() {
        ThreadPoolStatsReport report = new ThreadPoolStatsReport();
        report.setAppId("app1");
        report.setStatsList(List.of());

        service.reportStats(report);
    }

    @Test
    @DisplayName("reportStats with null throws exception")
    void reportStats_null() {
        assertThatThrownBy(() -> service.reportStats(null))
                .isInstanceOf(com.lezai.threadpool.exception.ValidationException.class);
    }

    @Test
    @DisplayName("reportStats with null appId throws exception")
    void reportStats_nullAppId() {
        ThreadPoolStatsReport report = new ThreadPoolStatsReport();
        assertThatThrownBy(() -> service.reportStats(report))
                .isInstanceOf(com.lezai.threadpool.exception.ValidationException.class);
    }

    @Test
    @DisplayName("subscribe returns CompletableFuture and registers listener when version matches current")
    void subscribe_returnsCompletableFuture() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        CompletableFuture<ConfigChangeNotification> future = service.subscribe("app1", 5L, 30000L);

        assertThat(future).isNotNull();
        assertThat(future.isDone()).isFalse();
        verify(listenerManager).register(eq("app1"), any(ConfigChangeListener.class));
        // 补偿定时器移到超时临近：delay = max(timeoutMs - 500, 1000) = 29500
        verify(subscriptionExecutor).schedule(any(Runnable.class), eq(29500L), eq(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("subscribe returns immediately when client version is behind (register-first-then-compare)")
    void subscribe_immediateReturn_whenNewerVersionExists() {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(10L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        CompletableFuture<ConfigChangeNotification> future = service.subscribe("app1", 5L, 30000L);

        assertThat(future.isDone()).isTrue();
        ConfigChangeNotification notification = future.join();
        assertThat(notification.getAppId()).isEqualTo("app1");
        assertThat(notification.getVersion()).isEqualTo(10L);
        // 先注册后比较：register 会被调用，检测到新版本后立即 unregister
        verify(listenerManager).register(eq("app1"), any(ConfigChangeListener.class));
        verify(listenerManager).unregister(eq("app1"), any(ConfigChangeListener.class));
        // 立即返回路径不调度补偿定时器
        verify(subscriptionExecutor, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    @DisplayName("subscribe does not lose notification when config changes after registration (TOCTOU regression)")
    void subscribe_noLostNotification_whenChangeArrivesAfterRegister() {
        // version == currentVersion：不会立即返回，依赖监听器捕获后续变更
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(5L)
                .configs(TestDataFactory.buildConfigList("pool-a")).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        ArgumentCaptor<ConfigChangeListener> listenerCaptor = ArgumentCaptor.forClass(ConfigChangeListener.class);

        CompletableFuture<ConfigChangeNotification> future = service.subscribe("app1", 5L, 30000L);

        // 未立即返回，进入监听等待
        assertThat(future.isDone()).isFalse();
        verify(listenerManager).register(eq("app1"), listenerCaptor.capture());

        // 模拟 register 与 version 检查之后发生 config change（触发 listener）
        ConfigChangeListener listener = listenerCaptor.getValue();
        listener.onConfigChanged("app1", 10L);

        // 监听器已注册，通知不应丢失
        assertThat(future.isDone()).isTrue();
        assertThat(future.join().getAppId()).isEqualTo("app1");
        assertThat(future.join().getVersion()).isEqualTo(10L);
    }

    @Test
    @DisplayName("subscribe throws ResourceNotFoundException when app not found")
    void subscribe_appNotFound() {
        when(configStorage.getAppConfig("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.subscribe("unknown", 1L, 30000L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");
    }
}



