package com.lezai.samples.cache.core;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerCacheMetricsTest {

    @Test
    void recordsAllMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerCacheMetrics m = new MicrometerCacheMetrics(registry);

        m.hit(); m.hit();
        m.miss();
        m.staleServed();
        m.recordLoad(1_000_000);
        m.guardRejected("token");
        m.guardRejected("bulkhead");
        m.singleFlightTimeout();
        m.syncPublished();
        m.syncReceived();
        m.syncError();

        assertThat(registry.get("cache.hit").counter().count()).isEqualTo(2);
        assertThat(registry.get("cache.miss").counter().count()).isEqualTo(1);
        assertThat(registry.get("cache.stale.served").counter().count()).isEqualTo(1);
        assertThat(registry.get("cache.load").timer().count()).isEqualTo(1);
        assertThat(registry.get("cache.guard.rejected").tag("reason", "token").counter().count()).isEqualTo(1);
        assertThat(registry.get("cache.guard.rejected").tag("reason", "bulkhead").counter().count()).isEqualTo(1);
        assertThat(registry.get("cache.singleflight.timeout").counter().count()).isEqualTo(1);
        assertThat(registry.get("cache.sync.published").counter().count()).isEqualTo(1);
        assertThat(registry.get("cache.sync.received").counter().count()).isEqualTo(1);
        assertThat(registry.get("cache.sync.error").counter().count()).isEqualTo(1);
    }
}
