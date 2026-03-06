package com.lezai.samples.service;

import com.lezai.ratelimit.annotation.RateLimit;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterTestService {
    @Getter
    AtomicInteger success = new AtomicInteger(0);

    @RateLimit(key = "#key", strategy = "redisTokenBucket")
    public Integer test(String key) {
        return success.addAndGet(1);
    }

}