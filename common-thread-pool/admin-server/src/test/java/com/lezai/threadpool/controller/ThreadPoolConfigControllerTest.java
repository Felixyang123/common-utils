package com.lezai.threadpool.controller;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.GlobalExceptionHandler;
import com.lezai.threadpool.interceptor.AdminAuthInterceptor;
import com.lezai.threadpool.service.ConfigAdminService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ThreadPoolConfigControllerTest {

    @Mock
    private ConfigAdminService configAdminService;

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
        ThreadPoolConfigResp resp = ThreadPoolConfigResp.builder()
                .configVersion(3)
                .configs(TestDataFactory.buildConfigList("pool-a", "pool-b"))
                .build();
        when(configAdminService.getAppConfig("app1")).thenReturn(resp);

        mockMvc.perform(get("/api/thread-pool/configs/app1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configVersion").value(3))
                .andExpect(jsonPath("$.data.configs").isArray());
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId} returns 404 when not found")
    void getAppConfig_notFound() throws Exception {
        when(configAdminService.getAppConfig("unknown"))
                .thenThrow(new ConfigNotFoundException("Config not found for appId: unknown"));

        mockMvc.perform(get("/api/thread-pool/configs/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId}/{poolName} returns single config")
    void getConfig_success() throws Exception {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configAdminService.getConfig("app1", "test-pool")).thenReturn(config);

        mockMvc.perform(get("/api/thread-pool/configs/app1/test-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.poolName").value("test-pool"));
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId}/{poolName} returns 404 when not found")
    void getConfig_notFound() throws Exception {
        when(configAdminService.getConfig("app1", "unknown-pool"))
                .thenThrow(new ConfigNotFoundException("Config not found for pool: unknown-pool"));

        mockMvc.perform(get("/api/thread-pool/configs/app1/unknown-pool"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/thread-pool/configs/{appId} saves all configs")
    void saveConfigs() throws Exception {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");

        mockMvc.perform(post("/api/thread-pool/configs/app1")
                        .requestAttr(AdminAuthInterceptor.ATTR_CURRENT_USER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(configs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(configAdminService).saveConfigs(anyString(), any(List.class), eq("admin"));
    }

    @Test
    @DisplayName("POST /api/thread-pool/configs/{appId}/{poolName} saves single config")
    void saveConfig() throws Exception {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();

        mockMvc.perform(post("/api/thread-pool/configs/app1/test-pool")
                        .requestAttr(AdminAuthInterceptor.ATTR_CURRENT_USER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(configAdminService).saveConfig(anyString(), any(ThreadPoolConfig.class), eq("admin"));
    }

    @Test
    @DisplayName("DELETE /api/thread-pool/configs/{appId} deletes all configs")
    void deleteConfigs() throws Exception {
        mockMvc.perform(delete("/api/thread-pool/configs/app1")
                        .requestAttr(AdminAuthInterceptor.ATTR_CURRENT_USER, "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(configAdminService).deleteConfigs("app1", "admin");
    }

    @Test
    @DisplayName("DELETE /api/thread-pool/configs/{appId}/{poolName} deletes single config")
    void deleteConfig() throws Exception {
        mockMvc.perform(delete("/api/thread-pool/configs/app1/test-pool")
                        .requestAttr(AdminAuthInterceptor.ATTR_CURRENT_USER, "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(configAdminService).deleteConfig("app1", "test-pool", "admin");
    }

    @Test
    @DisplayName("GET /api/thread-pool/configs/{appId}/version returns version")
    void getConfigVersion() throws Exception {
        when(configAdminService.getConfigVersion("app1")).thenReturn(5L);

        mockMvc.perform(get("/api/thread-pool/configs/app1/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("POST /api/thread-pool/config/{appId}/add adds config")
    void addConfig() throws Exception {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        when(configAdminService.addConfig(anyString(), any(ThreadPoolConfig.class), eq("admin"))).thenReturn(config);

        mockMvc.perform(post("/api/thread-pool/config/app1/add")
                        .requestAttr(AdminAuthInterceptor.ATTR_CURRENT_USER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("POST /api/thread-pool/configs/{appId}/add adds batch configs")
    void addConfigs() throws Exception {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a");
        when(configAdminService.addConfigs(anyString(), any(List.class), eq("admin"))).thenReturn(configs);

        mockMvc.perform(post("/api/thread-pool/configs/app1/add")
                        .requestAttr(AdminAuthInterceptor.ATTR_CURRENT_USER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(configs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
