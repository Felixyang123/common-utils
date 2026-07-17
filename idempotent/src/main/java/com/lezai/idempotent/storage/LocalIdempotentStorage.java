package com.lezai.idempotent.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lezai.idempotent.core.IdempotentRecord;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 本地内存存储实现
 */
@Slf4j
public class LocalIdempotentStorage implements IdempotentStorage {

    private final Cache<String, IdempotentRecord> cache;

    public LocalIdempotentStorage() {
        this(10000, 24 * 60 * 60);
    }

    public LocalIdempotentStorage(int maxSize, long expireSeconds) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public IdempotentRecord get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        return cache.getIfPresent(key);
    }

    @Override
    public void save(IdempotentRecord record, long expireSeconds) {
        if (record == null) {
            throw new IllegalArgumentException("IdempotentRecord must not be null");
        }
        // Caffeine only supports global expiration; expireSeconds parameter is ignored
        // All records use the expireAfterWrite policy set in the constructor
        cache.put(record.getKey(), record);
        log.debug("Record saved for key: {}", record.getKey());
    }

    @Override
    public void remove(String key) {
        cache.invalidate(key);
        log.debug("Record removed for key: {}", key);
    }

    @Override
    public boolean exists(String key) {
        return cache.getIfPresent(key) != null;
    }
}
