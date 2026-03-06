package com.lezai.samples.limit;

import com.lezai.ratelimit.exception.RateLimitExceededException;
import com.lezai.samples.service.RateLimiterTestService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RateLimiterServiceTest {
    @Autowired
    private RateLimiterTestService testService;

    @Test
    @DisplayName("测试限流API")
    void rateLimiterTest() {
        String key = "rateLimiterTest";
        for (int i = 0; i < 20; i++) {
            try {
                testService.test(key);
            } catch (Exception e) {
            }
        }
        Assertions.assertEquals(10, testService.getSuccess().get());
    }

    @Test
    @DisplayName("测试限流API超限被拦截")
    void overLimitTest() {
        String key = "overLimitTest";
        for (int i = 0; i < 10; i++) {
            testService.test(key);
        }
        Assertions.assertThrows(RateLimitExceededException.class, () -> testService.test(key));
    }
}
