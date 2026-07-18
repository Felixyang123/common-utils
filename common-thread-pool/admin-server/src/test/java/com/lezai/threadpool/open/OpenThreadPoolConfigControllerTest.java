package com.lezai.threadpool.open;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.pojo.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.exception.GlobalExceptionHandler;
import com.lezai.threadpool.exception.ResourceNotModifiedException;
import com.lezai.threadpool.service.OpenThreadPoolConfigService;
import com.lezai.threadpool.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OpenThreadPoolConfigControllerTest {

    @Mock
    private OpenThreadPoolConfigService openThreadPoolConfigService;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private OpenThreadPoolConfigController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /open/api/thread-pool/configs/{appId}/add adds batch configs")
    void addConfigs() throws Exception {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a");
        AddConfigAppResult result = new AddConfigAppResult();
        result.setAddedConfigs(configs);
        when(openThreadPoolConfigService.addConfigs("app1", configs)).thenReturn(result);

        mockMvc.perform(post("/open/api/thread-pool/configs/app1/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(configs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("GET /open/api/thread-pool/configs/{appId}/pull returns config")
    void pullConfigs_noVersion() throws Exception {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1")
                .configVersion(5)
                .configs(TestDataFactory.buildConfigList("pool-a"))
                .build();
        when(openThreadPoolConfigService.pullConfigs("app1", null)).thenReturn(appConfig);

        mockMvc.perform(get("/open/api/thread-pool/configs/app1/pull"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configVersion").value(5));
    }

    @Test
    @DisplayName("GET /open/api/thread-pool/configs/{appId}/pull with up-to-date version returns 304")
    void pullConfigs_notModified() throws Exception {
        when(openThreadPoolConfigService.pullConfigs("app1", 5L))
                .thenThrow(new ResourceNotModifiedException("Config not modified"));

        mockMvc.perform(get("/open/api/thread-pool/configs/app1/pull")
                        .param("version", "5"))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("GET /open/api/thread-pool/configs/{appId}/pull with old version returns updated config")
    void pullConfigs_outdatedVersion() throws Exception {
        ThreadPoolAppConfig appConfig = ThreadPoolAppConfig.builder()
                .appId("app1")
                .configVersion(5)
                .configs(TestDataFactory.buildConfigList("pool-a"))
                .build();
        when(openThreadPoolConfigService.pullConfigs("app1", 3L)).thenReturn(appConfig);

        mockMvc.perform(get("/open/api/thread-pool/configs/app1/pull")
                        .param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configVersion").value(5));
    }

    @Test
    @DisplayName("GET legacy /open/api/thread-pool/config/{appId}/pull returns 410")
    void legacyPullReturnsGone() throws Exception {
        mockMvc.perform(get("/open/api/thread-pool/config/app1/pull"))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("POST /open/api/thread-pool/stats/report saves stats")
    void reportStats() throws Exception {
        ThreadPoolStats stats = TestDataFactory.defaultStats().build();
        ThreadPoolStatsReport report = ThreadPoolStatsReport.builder()
                .appId("app1")
                .reportTime(System.currentTimeMillis())
                .statsList(List.of(stats))
                .build();

        mockMvc.perform(post("/open/api/thread-pool/stats/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(report)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(openThreadPoolConfigService).reportStats(any(ThreadPoolStatsReport.class));
    }

    @Test
    @DisplayName("POST /open/api/thread-pool/stats/report with null report returns 400")
    void reportStats_nullBody() throws Exception {
        mockMvc.perform(post("/open/api/thread-pool/stats/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /open/api/thread-pool/stats/report with empty statsList still succeeds")
    void reportStats_emptyStats() throws Exception {
        ThreadPoolStatsReport report = ThreadPoolStatsReport.builder()
                .appId("app1")
                .reportTime(System.currentTimeMillis())
                .statsList(List.of())
                .build();

        mockMvc.perform(post("/open/api/thread-pool/stats/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(report)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("GET /open/api/thread-pool/configs/{appId}/subscribe returns a lightweight notification when version is outdated")
    void subscribe_immediateReturnWhenOutdated() throws Exception {
        ConfigChangeNotification notification = ConfigChangeNotification.builder()
                .appId("app1")
                .version(5L)
                .build();

        CompletableFuture<ConfigChangeNotification> future = CompletableFuture.completedFuture(notification);
        when(subscriptionService.subscribe(eq("app1"), eq(3L), anyLong())).thenReturn(future);

        MvcResult result = mockMvc.perform(get("/open/api/thread-pool/configs/app1/subscribe")
                        .param("version", "3"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appId").value("app1"))
                .andExpect(jsonPath("$.data.version").value(5))
                .andExpect(jsonPath("$.data.configs").doesNotExist());
    }

    @Test
    @DisplayName("GET /open/api/thread-pool/configs/{appId}/subscribe returns a real HTTP 304 on timeout, not a 200-wrapped code")
    void subscribe_timeoutReturnsRealHttp304() throws Exception {
        CompletableFuture<ConfigChangeNotification> future = new CompletableFuture<>();
        // 模拟超时：异步完成异常
        when(subscriptionService.subscribe(eq("app1"), eq(5L), anyLong())).thenReturn(future);

        MvcResult result = mockMvc.perform(get("/open/api/thread-pool/configs/app1/subscribe")
                        .param("version", "5"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 模拟超时异常
        future.completeExceptionally(new java.util.concurrent.TimeoutException("timeout"));

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isNotModified());
    }
}



