package com.lezai.threadpool.controller;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.enums.ChangeType;
import com.lezai.threadpool.exception.GlobalExceptionHandler;
import com.lezai.threadpool.storage.ConfigHistoryStorage;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.StatsStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ThreadPoolConfigControllerTest {

    @Mock
    private ConfigStorage configStorage;

    @Mock
    private StatsStorage statsStorage;

    @Mock
    private ConfigHistoryStorage historyStorage;

    @InjectMocks
    private ThreadPoolConfigController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId} returns app config")
    void getAppConfig_success() throws Exception {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1")
                .configVersion(3)
                .configs(TestDataFactory.buildConfigList("pool-a", "pool-b"))
                .build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        mockMvc.perform(get("/api/thread-pool/configs/app1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configVersion").value(3))
                .andExpect(jsonPath("$.data.configs").isArray());
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId} returns 404 when not found")
    void getAppConfig_notFound() throws Exception {
        when(configStorage.getAppConfig("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/thread-pool/configs/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId}/{poolName} returns single config")
    void getConfig_success() throws Exception {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.getConfig("app1", "test-pool")).thenReturn(Optional.of(config));

        mockMvc.perform(get("/api/thread-pool/configs/app1/test-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.poolName").value("test-pool"));
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId}/{poolName} returns 404 when not found")
    void getConfig_notFound() throws Exception {
        when(configStorage.getConfig("app1", "unknown-pool")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/thread-pool/configs/app1/unknown-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("POST /api/thread-pool/configs/{appId} saves all configs")
    void saveConfigs() throws Exception {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");

        mockMvc.perform(post("/api/thread-pool/configs/app1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(configs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(configStorage).saveConfigs(anyString(), any(List.class));
    }

    @Test
    @DisplayName("POST /api/thread-pool/configs/{appId}/{poolName} saves single config")
    void saveConfig() throws Exception {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();

        mockMvc.perform(post("/api/thread-pool/configs/app1/test-pool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(configStorage).saveConfig(anyString(), any(ThreadPoolConfig.class));
    }

    @Test
    @DisplayName("DELETE /api/thread-pool/configs/{appId} deletes all configs")
    void deleteConfigs() throws Exception {
        mockMvc.perform(delete("/api/thread-pool/configs/app1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(configStorage).deleteConfigs("app1");
    }

    @Test
    @DisplayName("DELETE /api/thread-pool/configs/{appId}/{poolName} deletes single config")
    void deleteConfig() throws Exception {
        mockMvc.perform(delete("/api/thread-pool/configs/app1/test-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(configStorage).deleteConfig("app1", "test-pool");
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId}/version returns version")
    void getConfigVersion() throws Exception {
        when(configStorage.getConfigVersion("app1")).thenReturn(5L);

        mockMvc.perform(get("/api/thread-pool/configs/app1/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("POST /api/thread-pool/config/{appId}/add adds config")
    void addConfig() throws Exception {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.addConfig("app1", config)).thenReturn(config);

        mockMvc.perform(post("/api/thread-pool/config/app1/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("POST /api/thread-pool/configs/{appId}/add adds batch configs")
    void addConfigs() throws Exception {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a");
        when(configStorage.addConfigs("app1", configs)).thenReturn(configs);

        mockMvc.perform(post("/api/thread-pool/configs/app1/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(configs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId}/history returns config history")
    void getConfigHistory() throws Exception {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1").configVersion(1).configs(List.of()).build();
        when(configStorage.getAppConfig("app1")).thenReturn(Optional.of(appConfig));

        ChangeLogEntry<ThreadPoolConfig> entry = ChangeLogEntry.of(1, ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        when(historyStorage.getAllHistory("app1")).thenReturn(Map.of("pool-a", List.of(entry)));

        mockMvc.perform(get("/api/thread-pool/configs/app1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isMap());
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId}/{poolName}/history returns pool history")
    void getPoolConfigHistory() throws Exception {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.getConfig("app1", "test-pool")).thenReturn(Optional.of(config));

        ChangeLogEntry<ThreadPoolConfig> entry = ChangeLogEntry.of(1, ChangeType.CREATE, null, config);
        when(historyStorage.getHistory("app1", "test-pool")).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/thread-pool/configs/app1/test-pool/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId}/{poolName}/history with limit")
    void getPoolConfigHistory_withLimit() throws Exception {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configStorage.getConfig("app1", "test-pool")).thenReturn(Optional.of(config));

        ChangeLogEntry<ThreadPoolConfig> entry = ChangeLogEntry.of(1, ChangeType.CREATE, null, config);
        when(historyStorage.getHistory("app1", "test-pool", 5)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/thread-pool/configs/app1/test-pool/history")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
