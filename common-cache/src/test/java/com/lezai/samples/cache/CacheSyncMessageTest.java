package com.lezai.samples.cache;

import com.lezai.samples.cache.sync.CacheSyncMessageImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheSyncMessageTest {

    @Test
    void uniqueId_shouldBeSameWithinSameJvm() {
        CacheSyncMessageImpl msg1 = new CacheSyncMessageImpl("CAT", "key1", 1000L);
        CacheSyncMessageImpl msg2 = new CacheSyncMessageImpl("CAT", "key2", 1000L);
        // 同一 JVM 内所有消息共享相同的 uniqueId
        assertThat(msg1.uniqueId()).isEqualTo(msg2.uniqueId());
    }

    @Test
    void sourceId_shouldMatchUniqueId_forLocallyCreatedMessages() {
        CacheSyncMessageImpl msg = new CacheSyncMessageImpl("CAT", "key1", 1000L);
        assertThat(msg.getSourceId()).isEqualTo(msg.uniqueId());
    }

    @Test
    void uniqueId_shouldNotBeBlank() {
        CacheSyncMessageImpl msg = new CacheSyncMessageImpl("CAT", "key1", 1000L);
        assertThat(msg.uniqueId()).isNotBlank();
    }
}
