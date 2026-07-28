package com.lezai.samples.cache.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheDegradationSupportTest {

    @AfterEach
    void reset() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
    }

    @Test
    void defaults_areDisabledGuardAndSharedSingleFlight() {
        assertThat(CacheDegradationSupport.guard().isEnabled()).isFalse();
        assertThat(CacheDegradationSupport.singleFlight()).isNotNull();
        assertThat(CacheDegradationSupport.singleFlightWaitMs()).isEqualTo(5000);
    }

    @Test
    void init_replacesGuardAndWait() {
        DegradationGuard guard = DegradationGuard.of(100, 10, 50);
        CacheDegradationSupport.init(guard, 3000);
        assertThat(CacheDegradationSupport.guard()).isSameAs(guard);
        assertThat(CacheDegradationSupport.singleFlightWaitMs()).isEqualTo(3000);
    }
}