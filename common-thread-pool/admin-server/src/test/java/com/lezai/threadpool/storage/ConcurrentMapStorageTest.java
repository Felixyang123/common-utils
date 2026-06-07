package com.lezai.threadpool.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentMapStorageTest {

    private ConcurrentMapStorage<String> storage;

    @BeforeEach
    void setUp() {
        storage = new ConcurrentMapStorage<>(new ConcurrentHashMap<>());
    }

    @Test
    @DisplayName("put and get work correctly")
    void putAndGet() {
        storage.putToCache("key1", "value1");
        assertThat(storage.getFromCache("key1")).hasValue("value1");
    }

    @Test
    @DisplayName("get returns empty for missing key")
    void get_missingKey_empty() {
        assertThat(storage.getFromCache("missing")).isEmpty();
    }

    @Test
    @DisplayName("remove deletes the entry")
    void remove() {
        storage.putToCache("key1", "value1");
        storage.removeFromCache("key1");
        assertThat(storage.getFromCache("key1")).isEmpty();
    }

    @Test
    @DisplayName("existsInCache returns correct status")
    void exists() {
        assertThat(storage.existsInCache("key1")).isFalse();
        storage.putToCache("key1", "value1");
        assertThat(storage.existsInCache("key1")).isTrue();
    }

    @Test
    @DisplayName("getCacheSize returns correct count")
    void cacheSize() {
        assertThat(storage.getCacheSize()).isZero();
        storage.putToCache("key1", "value1");
        storage.putToCache("key2", "value2");
        assertThat(storage.getCacheSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("compute applies remapping function atomically")
    void compute() {
        storage.putToCache("key1", "old");
        String result = storage.compute("key1", (k, v) -> v + "-new");
        assertThat(result).isEqualTo("old-new");
        assertThat(storage.getFromCache("key1")).hasValue("old-new");
    }

    @Test
    @DisplayName("compute can create new entry when key is missing")
    void compute_newKey() {
        String result = storage.compute("new-key", (k, v) -> "created");
        assertThat(result).isEqualTo("created");
        assertThat(storage.getFromCache("new-key")).hasValue("created");
    }

    @Test
    @DisplayName("compute can remove entry by returning null")
    void compute_removeByNull() {
        storage.putToCache("key1", "value1");
        storage.compute("key1", (k, v) -> null);
        assertThat(storage.getFromCache("key1")).isEmpty();
    }
}
