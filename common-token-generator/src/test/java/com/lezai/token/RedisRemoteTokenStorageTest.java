package com.lezai.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRemoteTokenStorageTest {

    private RedisRemoteTokenStorage tokenStorage;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenStorage = new RedisRemoteTokenStorage(redisTemplate);
    }

    @Test
    void getToken_ReturnsValue() {
        // Given
        String key = "test-key";
        String value = "test-value";
        when(valueOperations.get(key)).thenReturn(value);

        // When
        String result = tokenStorage.getToken(key);

        // Then
        assertEquals(value, result);
        verify(valueOperations).get(key);
    }

    @Test
    void setToken_ReturnsTrue_WhenKeyNotExists() {
        // Given
        String key = "test-key";
        String value = "test-value";
        when(valueOperations.setIfAbsent(key, value)).thenReturn(true);

        // When
        boolean result = tokenStorage.setToken(key, value);

        // Then
        assertTrue(result);
        verify(valueOperations).setIfAbsent(key, value);
    }

    @Test
    void setToken_ReturnsFalse_WhenKeyExists() {
        // Given
        String key = "test-key";
        String value = "test-value";
        when(valueOperations.setIfAbsent(key, value)).thenReturn(false);

        // When
        boolean result = tokenStorage.setToken(key, value);

        // Then
        assertFalse(result);
        verify(valueOperations).setIfAbsent(key, value);
    }

    @Test
    void deleteToken_ReturnsValue_WhenDeleteSuccess() {
        // Given
        String key = "test-key";
        String value = "test-value";
        when(valueOperations.get(key)).thenReturn(value);
        when(redisTemplate.delete(key)).thenReturn(true);

        // When
        String result = tokenStorage.deleteToken(key);

        // Then
        assertEquals(value, result);
        verify(valueOperations).get(key);
        verify(redisTemplate).delete(key);
    }

    @Test
    void deleteToken_ReturnsNull_WhenDeleteFailure() {
        // Given
        String key = "test-key";
        String value = "test-value";
        when(valueOperations.get(key)).thenReturn(value);
        when(redisTemplate.delete(key)).thenReturn(false);

        // When
        String result = tokenStorage.deleteToken(key);

        // Then
        assertNull(result);
        verify(valueOperations).get(key);
        verify(redisTemplate).delete(key);
    }
}
