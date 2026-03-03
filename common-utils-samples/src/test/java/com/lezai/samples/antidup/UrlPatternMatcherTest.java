// src/test/java/com/example/antidup/matcher/UrlPatternMatcherTest.java
package com.lezai.samples.antidup;

import com.lezai.anti.duplicate.utils.UrlPatternUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlPatternMatcherTest {

    @Test
    void shouldMatchAntStylePattern() {
        // Ant 风格
        assertTrue(UrlPatternUtils.match("/api/**", "/api/v1/order"));
        assertTrue(UrlPatternUtils.match("/user/{id}", "/user/123"));
        
        // 正则风格
        assertTrue(UrlPatternUtils.match("/pay/\\d+", "/pay/12345"));
    }

    @Test
    void shouldIgnoreQueryParameters() {
        boolean matched = UrlPatternUtils.match("/api/order", "/api/order?id=1");
        assertTrue(matched); // 忽略 ?id=1
    }
}