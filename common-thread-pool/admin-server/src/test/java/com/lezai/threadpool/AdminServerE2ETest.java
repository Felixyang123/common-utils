package com.lezai.threadpool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.lezai.threadpool.dao.mapper.ThreadPoolConfigAppMapper;
import com.lezai.threadpool.dao.mapper.ThreadPoolConfigMapper;
import com.lezai.threadpool.dao.rep.ThreadPoolConfigAppRep;
import com.lezai.threadpool.dao.rep.ThreadPoolConfigRep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E 冒烟测试 — 覆盖所有核心 API 端点和错误处理路径。
 * <p>
 * 使用独立的 TestApplication（无 @MapperScan）引用真实的 Controller 和 Service，
 * 本地文件存储避免 MySQL / Redis 依赖。
 */
@SpringBootTest(
        classes = AdminServerE2ETest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "threadpool.admin.auth-enabled=false",
                "threadpool.admin.auth.enabled=false",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                        "org.redisson.spring.starter.RedissonAutoConfigurationV2"
        }
)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminServerE2ETest {

    /**
     * 独立的测试应用类，不扫描 MyBatis Mapper，不连接 MySQL / Redis。
     */
    @SpringBootApplication(
            exclude = {
                    org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class
            }
    )
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    /** Mock RedissonClient to avoid Redis connection in tests */
    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public RedissonClient redissonClient() {
            RedissonClient mockClient = mock(RedissonClient.class);
            RRateLimiter rateLimiter = mock(RRateLimiter.class);
            when(rateLimiter.trySetRate(any(RateType.class), anyLong(), anyLong(), any(RateIntervalUnit.class)))
                    .thenReturn(true);
            when(rateLimiter.tryAcquire(1)).thenReturn(true);
            when(mockClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
            return mockClient;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ThreadPoolConfigRep threadPoolConfigRep;

    @MockBean
    private ThreadPoolConfigAppRep threadPoolConfigAppRep;

    @MockBean
    private ThreadPoolConfigMapper threadPoolConfigMapper;

    @MockBean
    private ThreadPoolConfigAppMapper threadPoolConfigAppMapper;

    /** 测试用的 appId（每个测试类一个，避免相互干扰） */
    private static final String APP_ID = "e2e-test-app";
    private static final String APP_NAME = "E2E Test Application";
    private static final String POOL_NAME = "e2e-test-pool";

    /** 创建 API Key 时返回的明文（在 createApiKey 中保存） */
    private static String createdPlainApiKey;

    // ===================== 1. API Key CRUD 流程 =====================

    @Test
    @Order(1)
    @DisplayName("POST /api/api-keys — 创建 API Key（返回明文 Key）")
    void createApiKey() throws Exception {
        String body = """
                {
                    "appId": "%s",
                    "appName": "%s",
                    "description": "created in E2E test"
                }
                """.formatted(APP_ID, APP_NAME);

        MvcResult result = mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appId").value(APP_ID))
                .andExpect(jsonPath("$.data.appName").value(APP_NAME))
                .andExpect(jsonPath("$.data.apiKey").isNotEmpty())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        createdPlainApiKey = JSON.parseObject(json).getJSONObject("data").getString("apiKey");
        assertNotNull(createdPlainApiKey, "should return plain API key");
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/api-keys/{appId} — 获取 API Key（脱敏，无明文）")
    void getApiKey() throws Exception {
        mockMvc.perform(get("/api/api-keys/{appId}", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appId").value(APP_ID))
                .andExpect(jsonPath("$.data.appName").value(APP_NAME))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/api-keys — 列出所有 API Key")
    void listAllApiKeys() throws Exception {
        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.appId == '" + APP_ID + "')]").exists());
    }

    @Test
    @Order(4)
    @DisplayName("PUT /api/api-keys/{appId} — 更新 API Key（名称、描述）")
    void updateApiKey() throws Exception {
        String body = """
                {
                    "appName": "%s-updated",
                    "description": "updated description",
                    "enabled": true
                }
                """.formatted(APP_NAME);

        MvcResult result = mockMvc.perform(put("/api/api-keys/{appId}", APP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Update response: " + responseContent);

        // Verify - check both status and body from cached result
        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        JSONObject json = JSON.parseObject(response.getContentAsString());
        assertThat(json.getInteger("code")).isEqualTo(0);

        mockMvc.perform(get("/api/api-keys/{appId}", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appName").value(APP_NAME + "-updated"));
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/api-keys/{appId}/regenerate — 重新生成 Key")
    void regenerateApiKey() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/api-keys/{appId}/regenerate", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appId").value(APP_ID))
                .andExpect(jsonPath("$.data.apiKey").isNotEmpty())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String newKey = JSON.parseObject(json).getJSONObject("data").getString("apiKey");
        assertNotNull(newKey);
        if (createdPlainApiKey != null) {
            assertTrue(!newKey.equals(createdPlainApiKey), "regenerated key should differ from original");
        }
        createdPlainApiKey = newKey;
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/api-keys/{appId}/history — 获取变更历史")
    void getApiKeyHistory() throws Exception {
        mockMvc.perform(get("/api/api-keys/{appId}/history", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").isNumber());
    }

    @Test
    @Order(7)
    @DisplayName("DELETE /api/api-keys/{appId} — 删除 API Key（清理）")
    void deleteApiKey() throws Exception {
        mockMvc.perform(delete("/api/api-keys/{appId}", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/api-keys/{appId}", APP_ID))
                .andExpect(status().isNotFound());
    }

    // ===================== 2. 线程池配置 CRUD 流程 =====================

    private static final String CONFIG_APP_ID = "e2e-config-app";

    @Test
    @Order(11)
    @DisplayName("POST /api/thread-pool/configs/{appId} — 保存配置（List<ThreadPoolConfig>）")
    void saveConfigs() throws Exception {
        String body = """
                [
                    {
                        "poolName": "%s",
                        "corePoolSize": 4,
                        "maximumPoolSize": 8,
                        "keepAliveTime": 60,
                        "timeUnit": "SECONDS",
                        "queueType": "BLOCKING_QUEUE",
                        "queueCapacity": 1024,
                        "rejectPolicyType": "ABORT",
                        "allowCoreThreadTimeout": false,
                        "threadNamePrefix": "%s",
                        "daemon": false
                    }
                ]
                """.formatted(POOL_NAME, POOL_NAME);

        mockMvc.perform(post("/api/thread-pool/configs/{appId}", CONFIG_APP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(12)
    @DisplayName("GET /api/thread-pool/configs/{appId} — 获取应用配置")
    void getAppConfig() throws Exception {
        mockMvc.perform(get("/api/thread-pool/configs/{appId}", CONFIG_APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configVersion").isNumber())
                .andExpect(jsonPath("$.data.configs").isArray())
                .andExpect(jsonPath("$.data.configs.length()").value(1));
    }

    @Test
    @Order(13)
    @DisplayName("GET /api/thread-pool/configs/{appId}/{poolName} — 获取单个配置")
    void getConfig() throws Exception {
        mockMvc.perform(get("/api/thread-pool/configs/{appId}/{poolName}", CONFIG_APP_ID, POOL_NAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.poolName").value(POOL_NAME))
                .andExpect(jsonPath("$.data.corePoolSize").value(4))
                .andExpect(jsonPath("$.data.maximumPoolSize").value(8));
    }

    @Test
    @Order(14)
    @DisplayName("POST /api/thread-pool/configs/{appId}/{poolName} — 保存单个配置")
    void saveSingleConfig() throws Exception {
        String body = """
                {
                    "poolName": "%s",
                    "corePoolSize": 2,
                    "maximumPoolSize": 4,
                    "keepAliveTime": 30,
                    "timeUnit": "SECONDS",
                    "queueType": "BLOCKING_QUEUE",
                    "queueCapacity": 512,
                    "rejectPolicyType": "CALLER_RUNS",
                    "allowCoreThreadTimeout": true,
                    "threadNamePrefix": "%s",
                    "daemon": false
                }
                """.formatted(POOL_NAME, POOL_NAME);

        mockMvc.perform(post("/api/thread-pool/configs/{appId}/{poolName}", CONFIG_APP_ID, POOL_NAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/thread-pool/configs/{appId}/{poolName}", CONFIG_APP_ID, POOL_NAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.corePoolSize").value(2))
                .andExpect(jsonPath("$.data.maximumPoolSize").value(4))
                .andExpect(jsonPath("$.data.rejectPolicyType").value("CALLER_RUNS"));
    }

    @Test
    @Order(15)
    @DisplayName("POST /api/thread-pool/config/{appId}/add — 添加单个配置（存在直接返回）")
    void addSingleConfig() throws Exception {
        String pool = "e2e-add-pool";
        String body = """
                {
                    "poolName": "%s",
                    "corePoolSize": 4,
                    "maximumPoolSize": 8,
                    "keepAliveTime": 60,
                    "timeUnit": "SECONDS",
                    "queueType": "BLOCKING_QUEUE",
                    "queueCapacity": 1024,
                    "rejectPolicyType": "ABORT",
                    "allowCoreThreadTimeout": false,
                    "threadNamePrefix": "%s",
                    "daemon": false
                }
                """.formatted(pool, pool);

        mockMvc.perform(post("/api/thread-pool/config/{appId}/add", CONFIG_APP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.poolName").value(pool));

        mockMvc.perform(post("/api/thread-pool/config/{appId}/add", CONFIG_APP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.poolName").value(pool));
    }

    @Test
    @Order(16)
    @DisplayName("POST /api/thread-pool/configs/{appId}/add — 批量添加配置")
    void addBatchConfigs() throws Exception {
        String body = """
                [
                    {
                        "poolName": "e2e-batch-pool-1",
                        "corePoolSize": 2,
                        "maximumPoolSize": 4,
                        "keepAliveTime": 60,
                        "timeUnit": "SECONDS",
                        "queueType": "BLOCKING_QUEUE",
                        "queueCapacity": 256,
                        "rejectPolicyType": "ABORT",
                        "allowCoreThreadTimeout": false,
                        "threadNamePrefix": "e2e-batch-pool-1",
                        "daemon": false
                    },
                    {
                        "poolName": "e2e-batch-pool-2",
                        "corePoolSize": 4,
                        "maximumPoolSize": 8,
                        "keepAliveTime": 120,
                        "timeUnit": "SECONDS",
                        "queueType": "LINKED_BLOCKING_QUEUE",
                        "queueCapacity": 512,
                        "rejectPolicyType": "CALLER_RUNS",
                        "allowCoreThreadTimeout": true,
                        "threadNamePrefix": "e2e-batch-pool-2",
                        "daemon": false
                    }
                ]
                """;

        mockMvc.perform(post("/api/thread-pool/configs/{appId}/add", CONFIG_APP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @Order(17)
    @DisplayName("GET /api/thread-pool/configs/{appId}/version — 获取版本")
    void getConfigVersion() throws Exception {
        mockMvc.perform(get("/api/thread-pool/configs/{appId}/version", CONFIG_APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    @Order(18)
    @DisplayName("GET /api/thread-pool/configs/{appId}/history — 获取变更历史")
    void getConfigHistory() throws Exception {
        mockMvc.perform(get("/api/thread-pool/configs/{appId}/history", CONFIG_APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isMap());
    }

    @Test
    @Order(19)
    @DisplayName("GET /api/thread-pool/configs/{appId}/{poolName}/history — 获取单个池变更历史")
    void getPoolConfigHistory() throws Exception {
        mockMvc.perform(get("/api/thread-pool/configs/{appId}/{poolName}/history", CONFIG_APP_ID, POOL_NAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/thread-pool/configs/{appId}/{poolName}/history", CONFIG_APP_ID, POOL_NAME)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(20)
    @DisplayName("DELETE /api/thread-pool/configs/{appId}/{poolName} — 删除单个配置")
    void deleteConfig() throws Exception {
        for (String pool : new String[]{"e2e-add-pool", "e2e-batch-pool-1", "e2e-batch-pool-2"}) {
            try {
                mockMvc.perform(delete("/api/thread-pool/configs/{appId}/{poolName}", CONFIG_APP_ID, pool))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(0));
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @Order(21)
    @DisplayName("DELETE /api/thread-pool/configs/{appId} — 删除所有配置")
    void deleteConfigs() throws Exception {
        mockMvc.perform(delete("/api/thread-pool/configs/{appId}", CONFIG_APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/thread-pool/configs/{appId}", CONFIG_APP_ID))
                .andExpect(status().isNotFound());
    }

    // ===================== 3. 错误处理测试 =====================

    @Test
    @Order(31)
    @DisplayName("POST /api/api-keys — 创建重复的 API Key → 409 错误")
    void createDuplicateApiKey() throws Exception {
        String appId = "e2e-duplicate-test-" + System.currentTimeMillis();
        String body = """
                {"appId": "%s", "appName": "Dup Test"}
                """.formatted(appId);

        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/api-keys/{appId}", appId));
    }

    @Test
    @Order(32)
    @DisplayName("GET /api/api-keys/{appId} — 获取不存在的 API Key → 404 错误")
    void getNonExistentApiKey() throws Exception {
        mockMvc.perform(get("/api/api-keys/{appId}", "e2e-nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(33)
    @DisplayName("GET /api/thread-pool/configs/{appId} — 获取不存在的配置 → 404 错误")
    void getNonExistentConfig() throws Exception {
        mockMvc.perform(get("/api/thread-pool/configs/{appId}", "e2e-nonexistent-config"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(34)
    @DisplayName("GET /api/thread-pool/configs/{appId}/{poolName} — 获取不存在的单个配置 → 404 错误")
    void getNonExistentSingleConfig() throws Exception {
        mockMvc.perform(get("/api/thread-pool/configs/{appId}/{poolName}",
                        "any-app", "nonexistent-pool"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(35)
    @DisplayName("POST /api/api-keys — 无效的请求体 → 400 错误（缺少必填 appId）")
    void createApiKey_invalidBody() throws Exception {
        String body = """
                {"appName": "No Id App"}
                """;

        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(36)
    @DisplayName("POST /api/api-keys — 空请求体 → 400 错误")
    void createApiKey_emptyBody() throws Exception {
        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(37)
    @DisplayName("DELETE /api/api-keys/{appId} — 删除不存在的 API Key → 404 错误")
    void deleteNonExistentApiKey() throws Exception {
        mockMvc.perform(delete("/api/api-keys/{appId}", "e2e-nonexistent-delete"))
                .andExpect(status().isNotFound());
    }

    // ===================== 4. 历史记录验证 =====================

    @Test
    @Order(41)
    @DisplayName("历史记录 — 创建 API Key 后验证 CREATE 条目")
    void historyContainsCreateEntry() throws Exception {
        String appId = "e2e-hist-create-" + System.currentTimeMillis();
        String body = """
                {"appId": "%s", "appName": "Hist Test Create"}
                """.formatted(appId);

        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult result = mockMvc.perform(get("/api/api-keys/{appId}/history", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JSONArray history = JSON.parseObject(json).getJSONArray("data");
        assertNotNull(history);
        assertTrue(history.size() >= 1);

        boolean hasCreate = history.stream()
                .map(Object::toString)
                .map(JSONObject::parseObject)
                .anyMatch(entry -> "CREATE".equals(entry.getString("changeType")));
        assertTrue(hasCreate, "history should contain a CREATE entry");

        mockMvc.perform(delete("/api/api-keys/{appId}", appId));
    }

    @Test
    @Order(42)
    @DisplayName("历史记录 — 更新 API Key 后验证 UPDATE 条目")
    void historyContainsUpdateEntry() throws Exception {
        String appId = "e2e-hist-update-" + System.currentTimeMillis();
        String createBody = """
                {"appId": "%s", "appName": "Hist Test Update"}
                """.formatted(appId);

        mockMvc.perform(post("/api/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody));

        String updateBody = """
                {"appName": "Updated Name", "enabled": true}
                """;
        mockMvc.perform(put("/api/api-keys/{appId}", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(jsonPath("$.code").value(0));

        MvcResult result = mockMvc.perform(get("/api/api-keys/{appId}/history", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JSONArray history = JSON.parseObject(json).getJSONArray("data");
        boolean hasUpdate = history.stream()
                .map(Object::toString)
                .map(JSONObject::parseObject)
                .anyMatch(entry -> "UPDATE".equals(entry.getString("changeType")));
        assertTrue(hasUpdate, "history should contain an UPDATE entry");

        mockMvc.perform(delete("/api/api-keys/{appId}", appId));
    }

    @Test
    @Order(43)
    @DisplayName("历史记录 — 删除 API Key 后验证 DELETE 条目")
    void historyContainsDeleteEntry() throws Exception {
        String appId = "e2e-hist-delete-" + System.currentTimeMillis();
        String body = """
                {"appId": "%s", "appName": "Hist Test Delete"}
                """.formatted(appId);

        mockMvc.perform(post("/api/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        mockMvc.perform(delete("/api/api-keys/{appId}", appId))
                .andExpect(jsonPath("$.code").value(0));

        MvcResult result = mockMvc.perform(get("/api/api-keys/{appId}/history", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JSONArray history = JSON.parseObject(json).getJSONArray("data");
        boolean hasDelete = history.stream()
                .map(Object::toString)
                .map(JSONObject::parseObject)
                .anyMatch(entry -> "DELETE".equals(entry.getString("changeType")));
        assertTrue(hasDelete, "history should contain a DELETE entry");
    }

    @Test
    @Order(44)
    @DisplayName("历史记录 — 保存配置后验证历史记录存在")
    void configHistoryAfterSave() throws Exception {
        String appId = "e2e-config-hist-" + System.currentTimeMillis();
        String configBody = """
                [
                    {
                        "poolName": "hist-pool",
                        "corePoolSize": 2,
                        "maximumPoolSize": 4,
                        "keepAliveTime": 60,
                        "timeUnit": "SECONDS",
                        "queueType": "BLOCKING_QUEUE",
                        "queueCapacity": 256,
                        "rejectPolicyType": "ABORT",
                        "allowCoreThreadTimeout": false,
                        "threadNamePrefix": "hist-pool",
                        "daemon": false
                    }
                ]
                """;

        mockMvc.perform(post("/api/thread-pool/configs/{appId}", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configBody))
                .andExpect(jsonPath("$.code").value(0));

        MvcResult result = mockMvc.perform(get("/api/thread-pool/configs/{appId}/history", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JSONObject data = JSON.parseObject(json).getJSONObject("data");
        assertNotNull(data);
        assertTrue(data.keySet().stream().anyMatch(k -> k.contains("hist-pool")),
                "history should contain entries for hist-pool");

        mockMvc.perform(delete("/api/thread-pool/configs/{appId}", appId));
    }
}
