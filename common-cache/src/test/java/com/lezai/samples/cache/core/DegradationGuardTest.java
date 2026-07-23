package com.lezai.samples.cache.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DegradationGuardTest {

    @Test
    void disabled_alwaysPermits() {
        DegradationGuard guard = DegradationGuard.disabled();
        assertThat(guard.isEnabled()).isFalse();
        try (DegradationGuard.Permit p = guard.acquire("k")) {
            assertThatCode(() -> { /* no-op */ }).doesNotThrowAnyException();
        }
    }

    @Test
    void acquire_rejects_whenTokenExhausted() {
        // 速率极低 + 舱壁充足 → 第二次因令牌不足被拒
        DegradationGuard guard = DegradationGuard.of(1.0, 100, 10);
        try (DegradationGuard.Permit p = guard.acquire("k")) {
            // 第一次成功（Guava 允许首次）
        }
        assertThatThrownBy(() -> guard.acquire("k"))
                .isInstanceOf(CacheDegradedException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    void acquire_rejects_whenBulkheadFull() {
        // 速率极高（不阻塞令牌）+ 舱壁=1 → 持有不放时第二次因舱壁满被拒
        DegradationGuard guard = DegradationGuard.of(1_000_000, 1, 50);
        DegradationGuard.Permit held = guard.acquire("k");
        try {
            assertThatThrownBy(() -> guard.acquire("k"))
                    .isInstanceOf(CacheDegradedException.class)
                    .hasMessageContaining("bulkhead");
        } finally {
            held.close();
        }
    }

    @Test
    void close_releasesBulkheadPermit() {
        DegradationGuard guard = DegradationGuard.of(1_000_000, 1, 50);
        DegradationGuard.Permit p1 = guard.acquire("k");
        p1.close();
        // 释放后能再次获取
        try (DegradationGuard.Permit p2 = guard.acquire("k")) {
            assertThat(p2).isNotNull();
        }
    }
}