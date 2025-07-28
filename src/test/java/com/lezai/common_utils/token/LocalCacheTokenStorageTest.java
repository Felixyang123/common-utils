package com.lezai.common_utils.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalCacheTokenStorageTest {

    private LocalCacheTokenStorage tokenStorage;

    @BeforeEach
    void setUp() {
        tokenStorage = new LocalCacheTokenStorage();
    }

    @Test
    void getToken_ReturnsNull_WhenKeyNotExists() {
        // When
        String result = tokenStorage.getToken("non-existent-key");

        // Then
        assertNull(result);
    }

    @Test
    void setToken_Success_WhenKeyNotExists() {
        // Given
        String key = "test-key";
        String value = "test-value";

        // When
        boolean result = tokenStorage.setToken(key, value);

        // Then
        assertTrue(result);
        assertEquals(value, tokenStorage.getToken(key));
    }

    @Test
    void setToken_Failure_WhenKeyAlreadyExists() {
        // Given
        String key = "test-key";
        String value1 = "test-value-1";
        String value2 = "test-value-2";
        tokenStorage.setToken(key, value1);

        // When
        boolean result = tokenStorage.setToken(key, value2);

        // Then
        assertFalse(result);
        assertEquals(value1, tokenStorage.getToken(key));
        assertNotEquals(value2, tokenStorage.getToken(key));
    }

    @Test
    void deleteToken_ReturnsValue_WhenKeyExists() {
        // Given
        String key = "test-key";
        String value = "test-value";
        tokenStorage.setToken(key, value);

        // When
        String result = tokenStorage.deleteToken(key);

        // Then
        assertEquals(value, result);
        assertNull(tokenStorage.getToken(key));
    }

    @Test
    void deleteToken_ReturnsNull_WhenKeyNotExists() {
        // When
        String result = tokenStorage.deleteToken("non-existent-key");

        // Then
        assertNull(result);
    }
}
