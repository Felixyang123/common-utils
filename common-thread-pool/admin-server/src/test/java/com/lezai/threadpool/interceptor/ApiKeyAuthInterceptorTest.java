package com.lezai.threadpool.interceptor;

import com.lezai.threadpool.pojo.bean.ApiKey;
import com.lezai.threadpool.storage.ApiKeyStorage;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthInterceptorTest {

    @Mock
    private ApiKeyStorage apiKeyStorage;

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

