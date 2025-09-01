package com.lezai.token;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UUIDTokenGeneratorTest {

    private final UUIDTokenGenerator tokenGenerator = new UUIDTokenGenerator();

    @Test
    void generateToken_ReturnsValidUUID() {
        // Given
        DefaultTokenContext context = new DefaultTokenContext();

        // When
        String token = tokenGenerator.generateToken(context);

        // Then
        assertNotNull(token);
        assertDoesNotThrow(() -> UUID.fromString(token));
    }

    @Test
    void generateToken_ReturnsDifferentTokens() {
        // Given
        DefaultTokenContext context1 = new DefaultTokenContext();
        DefaultTokenContext context2 = new DefaultTokenContext();

        // When
        String token1 = tokenGenerator.generateToken(context1);
        String token2 = tokenGenerator.generateToken(context2);

        // Then
        assertNotEquals(token1, token2);
    }
}
