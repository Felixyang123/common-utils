package com.lezai.threadpool.service;

import com.lezai.threadpool.controller.dto.request.AdminLoginRequest;
import com.lezai.threadpool.controller.dto.response.AdminLoginResponse;
import com.lezai.threadpool.exception.AuthenticationException;
import com.lezai.threadpool.storage.AdminUserStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-for-hs256";

    @Mock
    private AdminUserStorage adminUserStorage;

    private AdminAuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AdminAuthService(adminUserStorage, SECRET, 30L);
    }

    private AdminLoginRequest loginRequest(String username, String password) {
        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    @Test
    @DisplayName("login succeeds with correct credentials and returns a validatable token")
    void login_correctCredentials_returnsToken() {
        when(adminUserStorage.validateCredentials("admin", "secret123")).thenReturn(true);

        AdminLoginResponse response = authService.login(loginRequest("admin", "secret123"));

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getExpiresInSeconds()).isEqualTo(30L * 60);
        assertThat(authService.validateToken(response.getToken())).isEqualTo("admin");
    }

    @Test
    @DisplayName("login fails with wrong password")
    void login_wrongPassword_throws() {
        when(adminUserStorage.validateCredentials("admin", "wrong")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("admin", "wrong")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("login fails when user does not exist")
    void login_unknownUser_throws() {
        when(adminUserStorage.validateCredentials("ghost", "whatever")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("ghost", "whatever")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("validateToken rejects a garbage token")
    void validateToken_garbageToken_throws() {
        assertThatThrownBy(() -> authService.validateToken("not-a-jwt"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("validateToken rejects a token signed with a different secret")
    void validateToken_wrongSigningKey_throws() {
        when(adminUserStorage.validateCredentials("admin", "secret123")).thenReturn(true);
        String token = authService.login(loginRequest("admin", "secret123")).getToken();

        AdminAuthService otherService = new AdminAuthService(adminUserStorage, "a-completely-different-secret-32-bytes!!", 30L);

        assertThatThrownBy(() -> otherService.validateToken(token))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("validateToken rejects an expired token")
    void validateToken_expiredToken_throws() throws InterruptedException {
        when(adminUserStorage.validateCredentials("admin", "secret123")).thenReturn(true);
        AdminAuthService shortLivedService = new AdminAuthService(adminUserStorage, SECRET, 0L);

        String token = shortLivedService.login(loginRequest("admin", "secret123")).getToken();

        Thread.sleep(50);

        assertThatThrownBy(() -> shortLivedService.validateToken(token))
                .isInstanceOf(AuthenticationException.class);
    }
}
