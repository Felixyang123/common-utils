package com.lezai.threadpool.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyUtilsTest {

    // 测试用 HMAC 密钥（通过反射注入静态字段，避免依赖 Spring 上下文）
    private static final String TEST_SECRET = "test-hmac-secret-at-least-some-bytes-long-1234567890";

    private static void initHmacSecret(String secret) {
        try {
            var field = ApiKeyUtils.class.getDeclaredField("HMAC_SECRET");
            field.setAccessible(true);
            field.set(null, secret);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject HMAC secret for test", e);
        }
    }

    @BeforeEach
    void setUp() {
        // 每个测试前重置为默认测试密钥，保证测试隔离（HMAC_SECRET 是静态可变状态）
        initHmacSecret(TEST_SECRET);
    }

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
    @DisplayName("hashApiKey should return Base64URL HMAC-SHA256 hash")
    void hashApiKey_returnsHmacSha256Hash() {
        String key = "test-api-key-12345";
        String hash = ApiKeyUtils.hashApiKey(key);

        assertNotNull(hash);
        // HMAC-SHA256 = 32 bytes -> Base64URL no padding = 43 chars
        assertEquals(43, hash.length());
        // Base64URL 无填充，不应含 '=' '+' '/'
        assertFalse(hash.contains("="));
        assertFalse(hash.contains("+"));
        assertFalse(hash.contains("/"));
    }

    @Test
    @DisplayName("hashApiKey should produce deterministic hash for same key+secret (HMAC is not salted)")
    void hashApiKey_deterministic() {
        String key = "same-key";
        String hash1 = ApiKeyUtils.hashApiKey(key);
        String hash2 = ApiKeyUtils.hashApiKey(key);

        // HMAC-SHA256 是确定性的：相同 key+secret 产生相同 hash（与 BCrypt 不同）
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("different secrets should produce different hashes for same key")
    void hashApiKey_differentSecrets() {
        String key = "same-key";
        String hash1 = ApiKeyUtils.hashApiKey(key);

        initHmacSecret("a-different-secret-key-for-testing-123456789012");
        String hash2 = ApiKeyUtils.hashApiKey(key);

        assertNotEquals(hash1, hash2);
        // 还原测试密钥，避免影响其他测试
        initHmacSecret(TEST_SECRET);
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
