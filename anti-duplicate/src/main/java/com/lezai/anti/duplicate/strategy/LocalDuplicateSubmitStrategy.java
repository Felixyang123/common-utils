package com.lezai.anti.duplicate.strategy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalDuplicateSubmitStrategy implements DuplicateSubmitStrategy {
    private final ConcurrentMap<String, Long> submitTimestampCache = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, int expireSeconds) {
        AtomicBoolean locked = new AtomicBoolean(false);
        submitTimestampCache.compute(key, (k, ts) -> {
            long cur = System.currentTimeMillis();
            if (ts == null) {
                locked.set(true);
                return cur;
            }
            if ((cur - ts) / 1000 >= expireSeconds) {
                locked.set(true);
                return cur;
            }
            return ts;
        });
        return locked.get();
    }
}
