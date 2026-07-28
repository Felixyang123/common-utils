package com.lezai.samples.cache.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheDegradedExceptionTest {

    @Test
    void message_isExposed() {
        CacheDegradedException e = new CacheDegradedException("rate limit exceeded for key=k1");
        assertThat(e.getMessage()).contains("rate limit exceeded").contains("k1");
    }

    @Test
    void cause_isChained() {
        RuntimeException cause = new RuntimeException("boom");
        CacheDegradedException e = new CacheDegradedException("wrapped", cause);
        assertThat(e.getCause()).isSameAs(cause);
    }
}