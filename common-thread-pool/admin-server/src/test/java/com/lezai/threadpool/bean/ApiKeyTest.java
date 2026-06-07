package com.lezai.threadpool.bean;

import com.lezai.threadpool.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyTest {

    @Test
    @DisplayName("isExpired returns false when expireTime is null")
    void isExpired_nullExpireTime_false() {
        ApiKey key = TestDataFactory.defaultApiKey().expireTime(null).build();
        assertThat(key.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isExpired returns true when expireTime is in the past")
    void isExpired_pastTime_true() {
        ApiKey key = TestDataFactory.expiredApiKey().build();
        assertThat(key.isExpired()).isTrue();
    }

    @Test
    @DisplayName("isExpired returns false when expireTime is in the future")
    void isExpired_futureTime_false() {
        ApiKey key = TestDataFactory.defaultApiKey()
                .expireTime(LocalDateTime.now().plusDays(30))
                .build();
        assertThat(key.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isValid returns true when enabled and not expired")
    void isValid_enabledAndNotExpired_true() {
        ApiKey key = TestDataFactory.defaultApiKey()
                .enabled(true)
                .expireTime(LocalDateTime.now().plusDays(1))
                .build();
        assertThat(key.isValid()).isTrue();
    }

    @Test
    @DisplayName("isValid returns false when disabled")
    void isValid_disabled_false() {
        ApiKey key = TestDataFactory.disabledApiKey().build();
        assertThat(key.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid returns false when expired")
    void isValid_expired_false() {
        ApiKey key = TestDataFactory.expiredApiKey().build();
        assertThat(key.isValid()).isFalse();
    }

    @Test
    @DisplayName("ApiKey builder default enabled is true")
    void builder_defaultEnabled() {
        ApiKey key = ApiKey.builder().appId("app").apiKeyHash("hash").build();
        assertThat(key.isEnabled()).isTrue();
    }
}
