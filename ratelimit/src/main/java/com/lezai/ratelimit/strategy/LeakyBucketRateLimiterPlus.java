package com.lezai.ratelimit.strategy;

import com.lezai.ratelimit.enumeration.RateLimiterStrategyEnum;
import com.lezai.ratelimit.exception.RateLimitExceededException;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class LeakyBucketRateLimiterPlus implements RateLimiter {
    private static final ConcurrentMap<String, Bucket> BUCKETS = new ConcurrentHashMap<>();
    private final int capacity; // 桶容量
    private final int rate; // 漏水速率 (req/s)

    public LeakyBucketRateLimiterPlus(int capacity, int rate) {
        this.capacity = capacity;
        this.rate = rate;
        RateLimiterFactory.register(this);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        return tryAcquire(key, capacity, rate, permits);
    }

    @Override
    public boolean tryAcquire(String key, int cap, int rate, int permits) throws RateLimitExceededException {
        int c = cap <= 0 ? this.capacity : cap;
        int r = rate <= 0 ? this.rate : rate;
        AtomicBoolean acquired = new AtomicBoolean(false);
        BUCKETS.compute(key, (k, bucket) -> {
            if (bucket == null) {
                bucket = new Bucket(c, r);
            }

            long now = System.currentTimeMillis();
            long last = bucket.getLastLeakTime();

            // 计算已漏水
            long leaked = (now - last) * r / 1000;

            // 更新水位
            long currentWater = bucket.getWater();
            long newWater = Math.max(0, currentWater - leaked);

            // 尝试加水
            newWater += permits;
            if (newWater <= c) {
                acquired.set(true);
                // 更新水位和时间
                bucket.setWater(newWater);
                bucket.setLastLeakTime(now);
            }

            return bucket;
        });
        return acquired.get();
    }

    @Override
    public String name() {
        return RateLimiterStrategyEnum.LEAKY_BUCKET_PLUS.getName();
    }

    @Data
    @RequiredArgsConstructor
    private static class Bucket {
        private long water = 0;
        private long lastLeakTime = System.currentTimeMillis();
        private final int capacity; // 桶容量
        private final int rate; // 漏水速率 (req/s)
    }
}