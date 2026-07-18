package com.lezai.threadpool.controller;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.exception.GlobalExceptionHandler;
import com.lezai.threadpool.service.ConfigAdminService;
import com.lezai.threadpool.service.ConfigApplicationService;
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

    @Mock
    private ConfigApplicationService configApplicationService;

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
                .thenThrow(new ResourceNotFoundException("Config not found for appId: unknown"));
        mockMvc.perform(get("/api/thread-pool/configs/unknown")).andExpect(status().isNotFound());
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
    @DisplayName("DELETE /api/thread-pool/configs/{appId} deletes all configs")
    void deleteConfigs() throws Exception {
        mockMvc.perform(delete("/api/thread-pool/configs/app1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(configApplicationService).deleteConfigs("app1");
    }

    @Test
    @DisplayName("DELETE /api/thread-pool/configs/{appId}/{poolName} deletes single config")
    void deleteConfig() throws Exception {
        mockMvc.perform(delete("/api/thread-pool/configs/app1/test-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(configApplicationService).deleteConfig("app1", "test-pool");
    }
}


