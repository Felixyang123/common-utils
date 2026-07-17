package com.lezai.samples.cache;

import com.lezai.samples.cache.core.Cache;
import com.lezai.samples.cache.impl.DistributeCache;
import com.lezai.samples.cache.impl.HashMapCache;
import com.lezai.samples.cache.sync.CacheMessagePubSub;
import com.lezai.samples.cache.sync.CacheSyncMessageImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DistributeCacheTest {

    @Test
    void set_shouldPublishSyncMessage() {
        @SuppressWarnings("unchecked")
        Cache<Object> delegate = new HashMapCache<>(100);
        CacheMessagePubSub pubSub = mock(CacheMessagePubSub.class);

        DistributeCache<Object> cache = new DistributeCache<>(delegate, "test", pubSub, "127.0.0.1:8080");
        cache.set("key1", "value1");

        verify(pubSub).publish(any(CacheSyncMessageImpl.class));
        assertThat(cache.get("key1")).isEqualTo("value1");
    }

    @Test
    void setWithTtl_shouldPublishSyncMessage() {
        @SuppressWarnings("unchecked")
        Cache<Object> delegate = new HashMapCache<>(100);
        CacheMessagePubSub pubSub = mock(CacheMessagePubSub.class);

        DistributeCache<Object> cache = new DistributeCache<>(delegate, "test", pubSub, "127.0.0.1:8080");
        cache.set("key1", "value1", 5000L);

        verify(pubSub).publish(any(CacheSyncMessageImpl.class));
        assertThat(cache.get("key1")).isEqualTo("value1");
    }

    @Test
    void remove_shouldPublishSyncMessage() {
        @SuppressWarnings("unchecked")
        Cache<Object> delegate = new HashMapCache<>(100);
        CacheMessagePubSub pubSub = mock(CacheMessagePubSub.class);

        DistributeCache<Object> cache = new DistributeCache<>(delegate, "test", pubSub, "127.0.0.1:8080");
        cache.set("key1", "value1");
        cache.remove("key1");

        verify(pubSub, atLeast(2)).publish(any(CacheSyncMessageImpl.class));
        assertThat(cache.get("key1")).isNull();
    }

    @Test
    void get_shouldDelegateToInnerCache() {
        @SuppressWarnings("unchecked")
        Cache<Object> delegate = new HashMapCache<>(100);
        CacheMessagePubSub pubSub = mock(CacheMessagePubSub.class);

        DistributeCache<Object> cache = new DistributeCache<>(delegate, "test", pubSub, "127.0.0.1:8080");
        delegate.set("key1", "value1");

        assertThat(cache.get("key1")).isEqualTo("value1");
    }
}
