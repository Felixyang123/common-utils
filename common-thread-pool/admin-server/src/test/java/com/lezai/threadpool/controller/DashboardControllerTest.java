package com.lezai.threadpool.controller;

import com.lezai.threadpool.pojo.bean.AppConfigSummary;
import com.lezai.threadpool.pojo.bean.PoolAlert;
import com.lezai.threadpool.pojo.response.ApiKeyInfoResponse;
import com.lezai.threadpool.pojo.response.OperateLogResponse;
import com.lezai.threadpool.service.ApiKeyAdminService;
import com.lezai.threadpool.service.ConfigAdminService;
import com.lezai.threadpool.service.OperateLogService;
import com.lezai.threadpool.service.StatsAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock private ConfigAdminService configAdminService;
    @Mock private ApiKeyAdminService apiKeyAdminService;
    @Mock private OperateLogService operateLogService;
    @Mock private StatsAlertService statsAlertService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(
                configAdminService, apiKeyAdminService, operateLogService, statsAlertService)).build();
    }

    @Test
    @DisplayName("summary returns alertCount, alerts, logs and hasMore")
    void summaryReturnsExtendedFields() throws Exception {
        when(configAdminService.listApps()).thenReturn(List.of(
                AppConfigSummary.builder().appId("app1").poolCount(2).configVersion(1).build()));
        when(apiKeyAdminService.allApiKeys()).thenReturn(List.of(new ApiKeyInfoResponse()));
        OperateLogResponse log1 = new OperateLogResponse();
        OperateLogResponse log2 = new OperateLogResponse();
        when(operateLogService.getRecentLogs(2, null, null)).thenReturn(List.of(log1, log2));
        when(statsAlertService.checkAlerts()).thenReturn(List.of(PoolAlert.builder()
                .appId("app1").poolName("pool-a").metric("rejectionRate")
                .value(0.06d).threshold(0.05d).detectedAt(LocalDateTime.now()).build()));

        mockMvc.perform(get("/api/dashboard/summary").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appCount").value(1))
                .andExpect(jsonPath("$.data.apiKeyCount").value(1))
                .andExpect(jsonPath("$.data.configCount").value(2))
                .andExpect(jsonPath("$.data.alertCount").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.recentLogs.length()").value(1))
                .andExpect(jsonPath("$.data.alerts[0].poolName").value("pool-a"));

        verify(operateLogService).getRecentLogs(2, null, null);
    }
}
