package com.lezai.threadpool.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RRateLimiter rateLimiter;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(redissonClient);
    }

    @Test
    @DisplayName("preHandle rate-limits by IP when X-App-Id header is absent, passes when allowed")
    void preHandle_noAppId_rateLimitedByIp_passes() throws Exception {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.trySetRate(any(RateType.class), anyLong(), anyLong(), any(RateIntervalUnit.class))).thenReturn(true);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("preHandle returns 429 by IP when X-App-Id header is absent and IP rate limit exceeded")
    void preHandle_noAppId_rateLimitedByIp_returns429() throws Exception {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.trySetRate(any(RateType.class), anyLong(), anyLong(), any(RateIntervalUnit.class))).thenReturn(true);
        when(rateLimiter.tryAcquire(1)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("preHandle passes when rate limit allows")
    void preHandle_rateAllowed_passes() throws Exception {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.trySetRate(any(RateType.class), anyLong(), anyLong(), any(RateIntervalUnit.class))).thenReturn(true);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-App-Id", "app1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("preHandle returns 429 when rate limit exceeded")
    void preHandle_rateExceeded_returns429() throws Exception {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.trySetRate(any(RateType.class), anyLong(), anyLong(), any(RateIntervalUnit.class))).thenReturn(true);
        when(rateLimiter.tryAcquire(1)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-App-Id", "app1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("429");
    }
}
