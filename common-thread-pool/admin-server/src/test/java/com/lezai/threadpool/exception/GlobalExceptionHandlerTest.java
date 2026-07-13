package com.lezai.threadpool.exception;

import com.lezai.threadpool.bean.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleConfigNotFound returns code 404")
    void handleConfigNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("not found");
        ResponseEntity<ApiResponse<Void>> response = handler.handleConfigNotFound(ex);
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("not found");
    }

    @Test
    @DisplayName("handleConfigAlreadyExists returns code 409")
    void handleConfigAlreadyExists() {
        ResourceAlreadyExistsException ex = new ResourceAlreadyExistsException("already exists");
        ResponseEntity<ApiResponse<Void>> response = handler.handleConfigAlreadyExists(ex);
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).isEqualTo("already exists");
    }

    @Test
    @DisplayName("handleValidation returns code 400")
    void handleValidation() {
        ValidationException ex = new ValidationException("invalid input");
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("invalid input");
    }

    @Test
    @DisplayName("handleBusinessException uses exception code and message")
    void handleBusinessException() {
        BusinessException ex = new BusinessException(503, "service unavailable");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);
        assertThat(response.getBody().getCode()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo("service unavailable");
    }

    @Test
    @DisplayName("handleException returns generic 500 message")
    void handleException() {
        Exception ex = new RuntimeException("unexpected null pointer");
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);
        assertThat(response.getBody().getCode()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("Internal server error");
    }
}


