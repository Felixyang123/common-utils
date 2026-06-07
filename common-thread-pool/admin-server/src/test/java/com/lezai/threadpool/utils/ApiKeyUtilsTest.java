package com.lezai.threadpool.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyUtilsTest {

    @Test
    @DisplayName("generateRandomApiKey should return 32 character string")
    void generateRandomApiKey_length() {
        String key = ApiKeyUtils.generateRandomApiKey();
        assertNotNull(key);
        assertEquals(32, key.length());
    }

    @Test
    @DisplayName("generateRandomApiKey should return different keys on each call")
    void generateRandomApiKey_unique() {
        String key1 = ApiKeyUtils.generateRandomApiKey();
        String key2 = ApiKeyUtils.generateRandomApiKey();
        assertNotEquals(key1, key2);
    }

    @Test
    @DisplayName("hashApiKey should return BCrypt hash with salt")
    void hashApiKey_returnsBcryptHash() {
        String key = "test-api-key-12345";
        String hash = ApiKeyUtils.hashApiKey(key);

        assertNotNull(hash);
        // BCrypt hashes start with $2a$, $2b$, $2y$ etc.
        assertTrue(hash.startsWith("$2"));
        // BCrypt hashes are typically 60 characters
        assertEquals(60, hash.length());
    }

    @Test
    @DisplayName("hashApiKey should produce different hashes for same input (due to salt)")
    void hashApiKey_salted() {
        String key = "same-key";
        String hash1 = ApiKeyUtils.hashApiKey(key);
        String hash2 = ApiKeyUtils.hashApiKey(key);

        // Same key should produce different hashes due to random salt
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("validateApiKey should return true for correct key")
    void validateApiKey_correct() {
        String plainKey = "my-secret-api-key";
        String hash = ApiKeyUtils.hashApiKey(plainKey);

        assertTrue(ApiKeyUtils.validateApiKey(plainKey, hash));
    }

    @Test
    @DisplayName("validateApiKey should return false for incorrect key")
    void validateApiKey_incorrect() {
        String plainKey = "correct-key";
        String wrongKey = "wrong-key";
        String hash = ApiKeyUtils.hashApiKey(plainKey);

        assertFalse(ApiKeyUtils.validateApiKey(wrongKey, hash));
    }

    @Test
    @DisplayName("validateApiKey should handle null inputs gracefully")
    void validateApiKey_nullInputs() {
        String hash = ApiKeyUtils.hashApiKey("some-key");

        assertFalse(ApiKeyUtils.validateApiKey(null, hash));
        assertFalse(ApiKeyUtils.validateApiKey("key", null));
        assertFalse(ApiKeyUtils.validateApiKey(null, null));
    }
}
