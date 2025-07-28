package com.lezai.common_utils.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultTokenManagerTest {

    private DefaultTokenManager tokenManager;
    
    @Mock
    private TokenStorage<String, String> tokenStorage;
    
    @Mock
    private TokenGenerator<DefaultTokenContext> tokenGenerator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tokenManager = new DefaultTokenManager();
        tokenManager.setTokenStorage(tokenStorage);
        tokenManager.setTokenGenerator(tokenGenerator);
    }

    @Test
    void generateToken_Success() {
        // Given
        DefaultTokenContext context = new DefaultTokenContext();
        String generatedToken = "test-token";
        when(tokenGenerator.generateToken(context)).thenReturn(generatedToken);
        when(tokenStorage.setToken(generatedToken, generatedToken)).thenReturn(true);

        // When
        String token = tokenManager.generateToken(context);

        // Then
        assertEquals(generatedToken, token);
        assertEquals(generatedToken, context.getToken());
        verify(tokenGenerator).generateToken(context);
        verify(tokenStorage).setToken(generatedToken, generatedToken);
    }

    @Test
    void generateToken_Failure_WhenGeneratorReturnsNull() {
        // Given
        DefaultTokenContext context = new DefaultTokenContext();
        when(tokenGenerator.generateToken(context)).thenReturn(null);

        // When
        String token = tokenManager.generateToken(context);

        // Then
        assertNull(token);
        verify(tokenGenerator).generateToken(context);
        verify(tokenStorage, never()).setToken(anyString(), anyString());
    }

    @Test
    void generateToken_Failure_WhenStorageReturnsFalse() {
        // Given
        DefaultTokenContext context = new DefaultTokenContext();
        String generatedToken = "test-token";
        when(tokenGenerator.generateToken(context)).thenReturn(generatedToken);
        when(tokenStorage.setToken(generatedToken, generatedToken)).thenReturn(false);

        // When
        String token = tokenManager.generateToken(context);

        // Then
        assertNull(token);
        verify(tokenGenerator).generateToken(context);
        verify(tokenStorage).setToken(generatedToken, generatedToken);
    }

    @Test
    void checkToken_Success() {
        // Given
        DefaultTokenContext context = new DefaultTokenContext();
        context.setToken("test-token");
        when(tokenStorage.deleteToken("test-token")).thenReturn("test-token");

        // When
        boolean result = tokenManager.checkToken(context);

        // Then
        assertTrue(result);
        verify(tokenStorage).deleteToken("test-token");
    }

    @Test
    void checkToken_Failure() {
        // Given
        DefaultTokenContext context = new DefaultTokenContext();
        context.setToken("invalid-token");
        when(tokenStorage.deleteToken("invalid-token")).thenReturn(null);

        // When
        boolean result = tokenManager.checkToken(context);

        // Then
        assertFalse(result);

        verify(tokenStorage).deleteToken("invalid-token");

        context = new DefaultTokenContext();
        context.setToken("invalid-token-new");
        when(tokenStorage.deleteToken("invalid-token")).thenReturn("invalid-token-dif");
        result = tokenManager.checkToken(context);

        // Then
        assertFalse(result);

        verify(tokenStorage).deleteToken("invalid-token");
    }

    @Test
    void deleteToken_Success() {
        // Given
        DefaultTokenContext context = new DefaultTokenContext();
        context.setToken("test-token");
        String deletedToken = "test-token";
        when(tokenStorage.deleteToken("test-token")).thenReturn(deletedToken);

        // When
        String result = tokenManager.deleteToken(context);

        // Then
        assertEquals(deletedToken, result);
        verify(tokenStorage).deleteToken("test-token");
    }
}
