package com.lezai.samples.cache.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/** Micrometer 指标实现。全部低基数全局 meter。 */
public class MicrometerCacheMetrics implements CacheMetrics {

    private final MeterRegistry registry;
    private final Counter hit;
    private final Counter miss;
    private final Counter staleServed;
    private final Timer load;
    private final Counter singleFlightTimeout;
    private final Counter syncPublished;
    private final Counter syncReceived;
    private final Counter syncError;

    public MicrometerCacheMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.hit = registry.counter("cache.hit");
        this.miss = registry.counter("cache.miss");
        this.staleServed = registry.counter("cache.stale.served");
        this.load = registry.timer("cache.load");
        this.singleFlightTimeout = registry.counter("cache.singleflight.timeout");
        this.syncPublished = registry.counter("cache.sync.published");
        this.syncReceived = registry.counter("cache.sync.received");
        this.syncError = registry.counter("cache.sync.error");
    }

    @Override public void hit() { hit.increment(); }
    @Override public void miss() { miss.increment(); }
    @Override public void staleServed() { staleServed.increment(); }
    @Override public void recordLoad(long nanos) { load.record(nanos, TimeUnit.NANOSECONDS); }
    @Override public void guardRejected(String reason) {
        registry.counter("cache.guard.rejected", "reason", reason).increment();
    }
    @Override public void singleFlightTimeout() { singleFlightTimeout.increment(); }
    @Override public void syncPublished() { syncPublished.increment(); }
    @Override public void syncReceived() { syncReceived.increment(); }
    @Override public void syncError() { syncError.increment(); }
}
