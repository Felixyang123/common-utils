package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.bean.AdminUser;
import com.lezai.threadpool.utils.PasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileAdminUserStorageTest {

    private LocalFileAdminUserStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileAdminUserStorage("admin", PasswordUtils.hash("secret123"));
    }

    @Test
    @DisplayName("getByUsername returns the configured account for matching username")
    void getByUsername_matching_returnsUser() {
        Optional<AdminUser> result = storage.getByUsername("admin");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("admin");
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("getByUsername returns empty for non-matching username")
    void getByUsername_nonMatching_returnsEmpty() {
        assertThat(storage.getByUsername("someone-else")).isEmpty();
    }

    @Test
    @DisplayName("validateCredentials succeeds with correct username and password")
    void validateCredentials_correct_succeeds() {
        assertThat(storage.validateCredentials("admin", "secret123")).isTrue();
    }

    @Test
    @DisplayName("validateCredentials fails with wrong password")
    void validateCredentials_wrongPassword_fails() {
        assertThat(storage.validateCredentials("admin", "wrong")).isFalse();
    }

    @Test
    @DisplayName("validateCredentials fails with unknown username")
    void validateCredentials_unknownUsername_fails() {
        assertThat(storage.validateCredentials("ghost", "secret123")).isFalse();
    }

    @Test
    @DisplayName("validateCredentials fails with blank inputs")
    void validateCredentials_blankInputs_fails() {
        assertThat(storage.validateCredentials("", "secret123")).isFalse();
        assertThat(storage.validateCredentials("admin", "")).isFalse();
        assertThat(storage.validateCredentials(null, null)).isFalse();
    }
}
