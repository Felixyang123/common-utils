package com.lezai.threadpool.interceptor;

import com.lezai.threadpool.storage.ApiKeyStorage;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthInterceptorTest {

    @Mock
    private ApiKeyStorage apiKeyStorage;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RAtomicLong atomicLong;

    private ApiKeyAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ApiKeyAuthInterceptor(apiKeyStorage, true);
    }

    @Test
    @DisplayName("preHandle passes when auth is disabled")
    void preHandle_authDisabled_passes() throws Exception {
        interceptor = new ApiKeyAuthInterceptor(apiKeyStorage, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("preHandle rejects when X-App-Id header is missing")
    void preHandle_missingAppId_rejects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "some-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("preHandle rejects when X-API-Key header is missing")
    void preHandle_missingApiKey_rejects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-App-Id", "my-app");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("preHandle rejects when API key validation fails")
    void preHandle_invalidApiKey_rejects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-App-Id", "my-app");
        request.addHeader("X-API-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiKeyStorage.validateApiKey("my-app", "wrong-key")).thenReturn(false);

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("preHandle passes when API key validation succeeds")
    void preHandle_validApiKey_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-App-Id", "my-app");
        request.addHeader("X-API-Key", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiKeyStorage.validateApiKey("my-app", "valid-key")).thenReturn(true);

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("preHandle returns 429 after 10 consecutive invalid-key failures (11th blocked)")
    void preHandle_thenFailures_11thBlocked() throws Exception {
        AtomicLong counter = new AtomicLong(0);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);
        when(atomicLong.expireIfNotSet(any(java.time.Duration.class))).thenReturn(true);
        when(atomicLong.incrementAndGet()).thenAnswer(inv -> counter.incrementAndGet());
        when(atomicLong.get()).thenAnswer(inv -> counter.get());

        ApiKeyFailureRateLimiter limiter = new ApiKeyFailureRateLimiter(redissonClient);
        ReflectionTestUtils.setField(interceptor, "failureRateLimiter", limiter);

        when(apiKeyStorage.validateApiKey("my-app", "wrong-key")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-App-Id", "my-app");
        request.addHeader("X-API-Key", "wrong-key");

        // 前 10 次：验证失败返回 401，尚未触发限流
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            boolean result = interceptor.preHandle(request, response, null);
            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
        }

        // 第 11 次：失败计数达到阈值，触发限流返回 429
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        boolean result = interceptor.preHandle(request, blocked, null);
        assertThat(result).isFalse();
        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("preHandle rejects when appId is blank")
    void preHandle_blankAppId_rejects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-App-Id", "  ");
        request.addHeader("X-API-Key", "some-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
    }
}

