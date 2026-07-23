package com.lezai.samples.cache.sync;

import com.lezai.samples.cache.core.CacheMetrics;
import com.lezai.samples.cache.core.CacheMetricsHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SyncMetricsTest {

    static class Rec implements CacheMetrics {
        final Map<String, AtomicInteger> c = new ConcurrentHashMap<>();
        private void inc(String k) { c.computeIfAbsent(k, x -> new AtomicInteger()).incrementAndGet(); }
        int get(String k) { return c.getOrDefault(k, new AtomicInteger()).get(); }
        @Override public void hit() {} @Override public void miss() {} @Override public void staleServed() {}
        @Override public void recordLoad(long n) {} @Override public void guardRejected(String r) {}
        @Override public void singleFlightTimeout() {}
        @Override public void syncPublished() { inc("pub"); }
        @Override public void syncReceived() { inc("recv"); }
        @Override public void syncError() { inc("err"); }
    }

    @AfterEach
    void reset() { CacheMetricsHolder.init(null); }

    @Test
    @SuppressWarnings("unchecked")
    void publishSuccess_recordsPublished() {
        Rec rec = new Rec();
        CacheMetricsHolder.init(rec);
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        RedisCacheMessagePub pub = new RedisCacheMessagePub(template, "ch");
        pub.publish(new CacheSyncMessageImpl("C", "k", 1000L));
        assertThat(rec.get("pub")).isEqualTo(1);
        assertThat(rec.get("err")).isEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishFailure_recordsError() {
        Rec rec = new Rec();
        CacheMetricsHolder.init(rec);
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        doThrow(new RuntimeException("redis down")).when(template).convertAndSend(anyString(), any());
        RedisCacheMessagePub pub = new RedisCacheMessagePub(template, "ch");
        pub.publish(new CacheSyncMessageImpl("C", "k", 1000L));
        assertThat(rec.get("err")).isEqualTo(1);
    }
}
