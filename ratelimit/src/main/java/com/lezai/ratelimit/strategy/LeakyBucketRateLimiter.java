package com.lezai.ratelimit.strategy;

import com.lezai.ratelimit.enumeration.RateLimiterStrategyEnum;
import com.lezai.ratelimit.exception.RateLimitExceededException;

import java.util.concurrent.atomic.AtomicLong;

@Deprecated
public class LeakyBucketRateLimiter implements RateLimiter {
    private final AtomicLong water = new AtomicLong(0);
    private final AtomicLong lastLeakTime = new AtomicLong(System.currentTimeMillis());
    private final int capacity; // 桶容量
    private final int rate; // 漏水速率 (req/s)

    public LeakyBucketRateLimiter(int capacity, int rate) {
        this.capacity = capacity;
        this.rate = rate;
        RateLimiterFactory.register(this);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        return tryAcquire(key, this.capacity, this.rate, permits);
    }

    @Override
    public boolean tryAcquire(String key, int cap, int rate, int permits) throws RateLimitExceededException {
        cap = cap <= 0 ? this.capacity : cap;
        rate = rate <= 0 ? this.rate : rate;
        long now = System.currentTimeMillis();
        long last = lastLeakTime.get();

        // 计算已漏水
        long leaked = (now - last) * rate / 1000;

        // 更新水位
        long currentWater = water.get();
        long newWater = Math.max(0, currentWater - leaked);

        // CAS 更新水位和时间
        while (!water.compareAndSet(currentWater, newWater)) {
            currentWater = water.get();
            newWater = Math.max(0, currentWater - leaked);
        }
        lastLeakTime.set(now);

        // 尝试加水
        if (water.addAndGet(permits) <= cap) {
            return true;
        }
        water.addAndGet(-permits);
        return false;
    }

    @Override
    public String name() {
        return RateLimiterStrategyEnum.LEAKY_BUCKET.getName();
    }
}