package com.lezai.samples.cache.core;

import com.lezai.samples.cache.impl.HashMapCache;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheGetTest {

    @Test
    void get_returnsValue_whenFresh() {
        HashMapCache<Object> cache = new HashMapCache<>(100);
        cache.innerSet("k", CacheWrapper.of("v", 60_000L));
        assertThat(cache.get("k")).isEqualTo("v");
    }

    @Test
    void get_returnsNull_whenExpired() {
        HashMapCache<Object> cache = new HashMapCache<>(100);
        // ttl = -1 → expireTime = now-1，构造即过期
        cache.innerSet("k", CacheWrapper.of("v", -1L));
        assertThat(cache.get("k")).isNull();
    }

    @Test
    void get_returnsNull_whenMissing() {
        HashMapCache<Object> cache = new HashMapCache<>(100);
        assertThat(cache.get("missing")).isNull();
    }
}