package com.lezai.threadpool.service;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.ConfigNotModifiedException;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.StatsStorage;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenThreadPoolConfigServiceTest {

    @Mock
    private ConfigStorage configStorage;

    @Mock
    private StatsStorage statsStorage;

    private OpenThreadPoolConfigService service;

    @BeforeEach
    void setUp() {
        service = new OpenThreadPoolConfigService(configStorage, statsStorage);
    }

    @Test
    @DisplayName("addConfig delegates to storage and returns the config")
    void addConfig() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.addConfig("app1", config)).thenReturn(config);

        ThreadPoolConfig result = service.addConfig("app1", config);

        assertThat(result).isEqualTo(config);
        verify(configStorage).addConfig("app1", config);
    }

    @Test
    @DisplayName("addConfigs delegates to storage")
    void addConfigs() {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");
        when(configStorage.addConfigs("app1", configs)).thenReturn(configs);

        List<ThreadPoolConfig> result = service.addConfigs("app1", configs);

        assertThat(result).hasSize(2);
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
                .isInstanceOf(ConfigNotModifiedException.class);
    }

    @Test
    @DisplayName("pullConfigs throws ConfigNotFoundException when app not found")
    void pullConfigs_notFound() {
        when(configStorage.getAppConfig("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pullConfigs("unknown", null))
                .isInstanceOf(ConfigNotFoundException.class);
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
}
