package com.lezai.threadpool.service;

import com.lezai.threadpool.converter.AdminAuthConverter;
import com.lezai.threadpool.interceptor.AdminLoginRateLimiter;
import com.lezai.threadpool.pojo.bean.AdminUser;
import com.lezai.threadpool.pojo.request.AdminLoginRequest;
import com.lezai.threadpool.pojo.response.AdminLoginResponse;
import com.lezai.threadpool.exception.AuthenticationException;
import com.lezai.threadpool.storage.AdminUserStorage;
import com.lezai.threadpool.utils.PasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-for-hs256";

    @Mock
    private AdminUserStorage adminUserStorage;

    @Mock
    private AdminAuthConverter adminAuthConverter;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RAtomicLong atomicLong;

    private AdminAuthService authService;

    private AdminUser adminUser;

    @BeforeEach
    void setUp() {
        lenient().when(adminAuthConverter.toLoginResponse(any(), any(), any()))
                .thenAnswer(invocation -> {
                    AdminLoginResponse resp = new AdminLoginResponse();
                    resp.setToken(invocation.getArgument(0));
                    resp.setUsername(invocation.getArgument(1));
                    resp.setExpiresInSeconds(((java.time.Duration) invocation.getArgument(2)).getSeconds());
                    return resp;
                });
        authService = new AdminAuthService(adminUserStorage, adminAuthConverter, SECRET, 30L);
        adminUser = AdminUser.builder()
                .username("admin")
                .passwordHash(PasswordUtils.hash("secret123"))
                .enabled(true)
                .role("SUPER_ADMIN")
                .nickname("admin")
                .build();
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
        when(adminUserStorage.getByUsername("admin")).thenReturn(Optional.of(adminUser));

        AdminLoginResponse response = authService.login(loginRequest("admin", "secret123"));

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getExpiresInSeconds()).isEqualTo(30L * 60);
        assertThat(authService.validateToken(response.getToken()).getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("login fails with wrong password — unified error message (no user enumeration)")
    void login_wrongPassword_throws() {
        when(adminUserStorage.getByUsername("admin")).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> authService.login(loginRequest("admin", "wrong")))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    @DisplayName("login fails when user does not exist — unified error message (no user enumeration)")
    void login_unknownUser_throws() {
        when(adminUserStorage.getByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("ghost", "whatever")))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("用户名或密码错误");
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
        when(adminUserStorage.getByUsername("admin")).thenReturn(Optional.of(adminUser));
        String token = authService.login(loginRequest("admin", "secret123")).getToken();

        AdminAuthService otherService = new AdminAuthService(adminUserStorage, adminAuthConverter, "a-completely-different-secret-32-bytes!!", 30L);

        assertThatThrownBy(() -> otherService.validateToken(token))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("login locked after 5 consecutive failures — 6th attempt rejected")
    void login_fiveFailures_6thLocked() {
        AtomicLong counter = new AtomicLong(0);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);
        when(atomicLong.expireIfNotSet(any(Duration.class))).thenReturn(true);
        when(atomicLong.incrementAndGet()).thenAnswer(inv -> counter.incrementAndGet());
        when(atomicLong.get()).thenAnswer(inv -> counter.get());

        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(redissonClient);
        ReflectionTestUtils.setField(authService, "loginRateLimiter", limiter);

        when(adminUserStorage.getByUsername("admin")).thenReturn(Optional.of(adminUser));

        // 连续 5 次密码错误，均抛出"用户名或密码错误"（未锁定）
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(loginRequest("admin", "wrong")))
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("用户名或密码错误");
        }

        // 第 6 次：已被锁定，抛出锁定提示
        assertThatThrownBy(() -> authService.login(loginRequest("admin", "wrong")))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("登录尝试次数过多，账号已锁定 15 分钟");
    }

    @Test
    @DisplayName("successful login resets the failure counter")
    void login_success_resetsFailureCounter() {
        AtomicLong counter = new AtomicLong(0);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(atomicLong);
        when(atomicLong.expireIfNotSet(any(Duration.class))).thenReturn(true);
        when(atomicLong.incrementAndGet()).thenAnswer(inv -> counter.incrementAndGet());
        when(atomicLong.get()).thenAnswer(inv -> counter.get());
        when(atomicLong.delete()).thenAnswer(inv -> {
            counter.set(0);
            return true;
        });

        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(redissonClient);
        ReflectionTestUtils.setField(authService, "loginRateLimiter", limiter);

        when(adminUserStorage.getByUsername("admin")).thenReturn(Optional.of(adminUser));

        // 4 次失败（未达到 5 次锁定阈值）
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(loginRequest("admin", "wrong")))
                    .isInstanceOf(AuthenticationException.class);
        }

        // 成功登录，清零失败计数
        authService.login(loginRequest("admin", "secret123"));
        assertThat(counter.get()).isZero();

        // 再次错误不会立即被锁定（计数已从零开始）
        assertThatThrownBy(() -> authService.login(loginRequest("admin", "wrong")))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    @DisplayName("validateToken rejects an expired token")
    void validateToken_expiredToken_throws() throws InterruptedException {
        when(adminUserStorage.getByUsername("admin")).thenReturn(Optional.of(adminUser));
        AdminAuthService shortLivedService = new AdminAuthService(adminUserStorage, adminAuthConverter, SECRET, 0L);

        String token = shortLivedService.login(loginRequest("admin", "secret123")).getToken();

        Thread.sleep(50);

        assertThatThrownBy(() -> shortLivedService.validateToken(token))
                .isInstanceOf(AuthenticationException.class);
    }
}


