package com.lezai.threadpool.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilsTest {

    @Test
    @DisplayName("hash should return BCrypt hash")
    void hash_returnsBcryptHash() {
        String hash = PasswordUtils.hash("my-password");

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2"));
        assertEquals(60, hash.length());
    }

    @Test
    @DisplayName("hash should produce different hashes for same input (due to salt)")
    void hash_salted() {
        String hash1 = PasswordUtils.hash("same-password");
        String hash2 = PasswordUtils.hash("same-password");

        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("matches should return true for correct password")
    void matches_correct() {
        String hash = PasswordUtils.hash("correct-password");
        assertTrue(PasswordUtils.matches("correct-password", hash));
    }

    @Test
    @DisplayName("matches should return false for incorrect password")
    void matches_incorrect() {
        String hash = PasswordUtils.hash("correct-password");
        assertFalse(PasswordUtils.matches("wrong-password", hash));
    }

    @Test
    @DisplayName("matches should handle null inputs gracefully")
    void matches_nullInputs() {
        String hash = PasswordUtils.hash("some-password");

        assertFalse(PasswordUtils.matches(null, hash));
        assertFalse(PasswordUtils.matches("password", null));
        assertFalse(PasswordUtils.matches(null, null));
    }
}
