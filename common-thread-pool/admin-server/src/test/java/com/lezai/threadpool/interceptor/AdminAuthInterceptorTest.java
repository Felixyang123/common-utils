package com.lezai.threadpool.interceptor;

import com.lezai.threadpool.pojo.bean.AdminUserContext;
import com.lezai.threadpool.exception.AuthenticationException;
import com.lezai.threadpool.service.AdminAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    @Mock
    private AdminAuthService adminAuthService;

    private AdminAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminAuthInterceptor(adminAuthService, true, 30L);
    }

    @Test
    @DisplayName("preHandle passes when auth is disabled")
    void preHandle_authDisabled_passes() throws Exception {
        interceptor = new AdminAuthInterceptor(adminAuthService, false, 30L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("preHandle rejects when Authorization header is missing")
    void preHandle_missingHeader_rejects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("preHandle rejects when Authorization header lacks Bearer prefix")
    void preHandle_malformedHeader_rejects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("preHandle rejects when token validation fails")
    void preHandle_invalidToken_rejects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doThrow(new AuthenticationException("Invalid or expired token"))
                .when(adminAuthService).validateToken("bad-token");

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("preHandle passes when token is valid")
    void preHandle_validToken_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(adminAuthService.validateToken("good-token")).thenReturn(AdminUserContext.builder().username("admin").build());

        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isTrue();
        verify(adminAuthService).validateToken("good-token");
    }
}

