package com.lezai.threadpool.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    @DisplayName("exception stores code and message")
    void exception() {
        BusinessException ex = new BusinessException(400, "bad request");
        assertThat(ex.getCode()).isEqualTo(400);
        assertThat(ex.getMessage()).isEqualTo("bad request");
    }

    @Test
    @DisplayName("exception with cause")
    void exception_withCause() {
        Throwable cause = new RuntimeException("root cause");
        BusinessException ex = new BusinessException(500, "error", cause);
        assertThat(ex.getCode()).isEqualTo(500);
        assertThat(ex.getMessage()).isEqualTo("error");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("ValidationException has code 400")
    void validationException() {
        ValidationException ex = new ValidationException("invalid input");
        assertThat(ex.getCode()).isEqualTo(400);
        assertThat(ex.getMessage()).isEqualTo("invalid input");
    }

    @Test
    @DisplayName("ConfigNotFoundException has code 404")
    void configNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("not found");
        assertThat(ex.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("ConfigAlreadyExistsException has code 409")
    void configAlreadyExistsException() {
        ResourceAlreadyExistsException ex = new ResourceAlreadyExistsException("already exists");
        assertThat(ex.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("ConfigNotModifiedException has code 304")
    void configNotModifiedException() {
        ResourceNotModifiedException ex = new ResourceNotModifiedException("not modified");
        assertThat(ex.getCode()).isEqualTo(304);
    }
}
