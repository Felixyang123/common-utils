package com.lezai.threadpool.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageTypeTest {

    @Test
    @DisplayName("enum has all expected values")
    void values() {
        assertThat(StorageType.values()).containsExactly(
                StorageType.API_KEY,
                StorageType.API_KEY_HISTORY,
                StorageType.THREAD_POOL_CONFIG,
                StorageType.THREAD_POOL_CONFIG_HISTORY,
                StorageType.THREAD_POOL_STATS
        );
    }

    @Test
    @DisplayName("descriptions are correct")
    void descriptions() {
        assertThat(StorageType.API_KEY.getDescription()).isEqualTo("API Key");
        assertThat(StorageType.API_KEY_HISTORY.getDescription()).isEqualTo("API Key History");
        assertThat(StorageType.THREAD_POOL_CONFIG.getDescription()).isEqualTo("Thread Pool Config");
        assertThat(StorageType.THREAD_POOL_CONFIG_HISTORY.getDescription()).isEqualTo("Thread Pool Config History");
        assertThat(StorageType.THREAD_POOL_STATS.getDescription()).isEqualTo("Thread Pool Stats");
    }
}
