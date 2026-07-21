package com.lezai.threadpool.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 默认凭据启动校验（决议 6c + 17）单元测试。
 * 直接构造 {@link AdminServerAutoConfiguration} 并调用 {@code validateDefaultCredentials()}，
 * 验证 db profile 下默认凭据触发 fail-fast，local profile 下放行。
 */
@ExtendWith(MockitoExtension.class)
class DefaultCredentialStartupTest {

    private static final String DEFAULT_PASSWORD = "changeme";
    private static final String DEFAULT_JWT_SECRET = "threadpool-admin-jwt-default-secret-please-change-in-production";
    private static final String DEFAULT_DB_PASSWORD = "lifan1994";
    private static final String DEFAULT_HMAC_SECRET = "threadpool-admin-apikey-hmac-default-secret-please-change";

    @Mock
    private Environment environment;

    private AdminAuthProperty authProperty;

    @BeforeEach
    void setUp() {
        authProperty = new AdminAuthProperty();
    }

    @Test
    @DisplayName("db profile + default credentials → startup fails with IllegalStateException")
    void dbProfile_defaultCredentials_fails() {
        when(environment.matchesProfiles("db")).thenReturn(true);
        when(environment.getProperty("spring.datasource.password")).thenReturn(DEFAULT_DB_PASSWORD);
        when(environment.getProperty("threadpool.admin.apikey.hmac-secret")).thenReturn(DEFAULT_HMAC_SECRET);
        authProperty.setPassword(DEFAULT_PASSWORD);
        authProperty.setSecret(DEFAULT_JWT_SECRET);

        AdminServerAutoConfiguration config = new AdminServerAutoConfiguration(authProperty, environment);

        assertThatThrownBy(config::validateDefaultCredentials)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default credentials");
    }

    @Test
    @DisplayName("db profile + changed credentials → startup succeeds")
    void dbProfile_changedCredentials_succeeds() {
        when(environment.matchesProfiles("db")).thenReturn(true);
        when(environment.getProperty("spring.datasource.password")).thenReturn("custom-db-password");
        when(environment.getProperty("threadpool.admin.apikey.hmac-secret")).thenReturn("custom-hmac-secret");
        authProperty.setPassword("custom-admin-password");
        authProperty.setSecret("custom-jwt-secret-at-least-32-bytes-long!");

        AdminServerAutoConfiguration config = new AdminServerAutoConfiguration(authProperty, environment);

        assertThatCode(config::validateDefaultCredentials).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("local profile + default credentials → startup succeeds (dev convenience)")
    void localProfile_defaultCredentials_succeeds() {
        // 非 db profile 下，matchesProfiles("db") 返回 false，校验直接放行
        when(environment.matchesProfiles("db")).thenReturn(false);
        // local profile 下 getProperty 不会被调用到默认值比对，使用 lenient 避免未桩告警
        lenient().when(environment.getProperty(anyString())).thenReturn(null);
        authProperty.setPassword(DEFAULT_PASSWORD);
        authProperty.setSecret(DEFAULT_JWT_SECRET);

        AdminServerAutoConfiguration config = new AdminServerAutoConfiguration(authProperty, environment);

        assertThatCode(config::validateDefaultCredentials).doesNotThrowAnyException();
    }
}
