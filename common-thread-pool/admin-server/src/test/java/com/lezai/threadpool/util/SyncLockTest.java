package com.lezai.threadpool.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SyncLockTest {

    @Test
    @DisplayName("LocalStripedLock tryLock returns false when same stripe is already held")
    void localTryLockReturnsFalseWhenHeld() throws Exception {
        LocalStripedLock lock = new LocalStripedLock();
        assertThat(lock.tryLock("stats", "aggregation", 0, 1, TimeUnit.SECONDS)).isTrue();
        assertThat(lock.tryLock("stats", "aggregation", 0, 1, TimeUnit.SECONDS)).isFalse();
        lock.unlock("stats", "aggregation");
    }
}
