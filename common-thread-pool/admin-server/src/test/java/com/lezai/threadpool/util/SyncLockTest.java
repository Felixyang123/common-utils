package com.lezai.threadpool.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SyncLockTest {

    @Test
    @DisplayName("LocalStripedLock 同线程重入 tryLock 返回 true（与 ReentrantLock 原生重入语义对齐）")
    void localTryLock_reentrant_returnsTrue() throws Exception {
        LocalStripedLock lock = new LocalStripedLock();
        assertThat(lock.tryLock("stats", "aggregation", 0, 1, TimeUnit.SECONDS)).isTrue();
        // 同线程重入：不再前置拒绝，走 ReentrantLock 原生重入，返回 true
        assertThat(lock.tryLock("stats", "aggregation", 0, 1, TimeUnit.SECONDS)).isTrue();
        lock.unlock("stats", "aggregation");
    }

    @Test
    @DisplayName("LocalStripedLock 未持锁 unlock 不抛异常（与 RedissonSyncLock 行为对齐）")
    void localUnlock_notHeld_noException() {
        LocalStripedLock lock = new LocalStripedLock();
        // 当前线程未持有该锁，unlock 应静默返回，不抛 IllegalMonitorStateException
        lock.unlock("stats", "aggregation");
    }
}
