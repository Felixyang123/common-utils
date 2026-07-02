package com.lezai.threadpool;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin 账号密码认证 E2E 冒烟测试 —— 覆盖登录、带 token 访问、无/错误 token 拒绝的完整流程。
 * <p>
 * local-file 存储模式，独立 TestApplication，避免 MySQL / Redis 依赖（与 {@link AdminServerE2ETest} 同类隔离方式）。
 */
@SpringBootTest(
        classes = AdminAuthE2ETest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "threadpool.admin.auth-enabled=false",
                "threadpool.admin.auth.enabled=true",
                "threadpool.admin.auth.username=e2e-admin",
                "threadpool.admin.auth.password=e2e-secret-password",
                "threadpool.admin.auth.secret=e2e-test-jwt-signing-secret-32-bytes-min",
                "threadpool.admin.auth.token-expire-minutes=30",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                        "org.redisson.spring.starter.RedissonAutoConfigurationV2"
        }
)
@AutoConfigureMockMvc
class AdminAuthE2ETest {

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

    @Test
    @DisplayName("management endpoint without token is rejected with business code 401")
    void managementEndpoint_noToken_rejected() throws Exception {
        mockMvc.perform(get("/api/api-keys"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("login with wrong password fails with business code 401")
    void login_wrongPassword_fails() throws Exception {
        String body = """
                {"username": "e2e-admin", "password": "wrong-password"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("login succeeds and the returned token grants access to management endpoints")
    void login_thenAccessManagementEndpoint_succeeds() throws Exception {
        String loginBody = """
                {"username": "e2e-admin", "password": "e2e-secret-password"}
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value("e2e-admin"))
                .andReturn();

        String json = loginResult.getResponse().getContentAsString();
        String token = JSON.parseObject(json).getJSONObject("data").getString("token");
        assertNotNull(token, "login should return a JWT token");

        mockMvc.perform(get("/api/api-keys")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("management endpoint with a garbage token is rejected")
    void managementEndpoint_garbageToken_rejected() throws Exception {
        mockMvc.perform(get("/api/api-keys")
                        .header("Authorization", "Bearer garbage-token"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("open API endpoints are unaffected by admin auth")
    void openApiEndpoint_unaffectedByAdminAuth() throws Exception {
        // Open API 使用独立的 X-App-Id + X-API-Key 体系（此处仅验证请求未被 AdminAuthInterceptor 拦截）
        mockMvc.perform(get("/open/api/thread-pool/config/nonexistent-app/pull"))
                .andExpect(jsonPath("$.code").value(404));
    }
}
