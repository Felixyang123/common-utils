package com.lezai.samples.cache.config;

import com.lezai.samples.cache.core.CacheDegradationSupport;
import com.lezai.samples.cache.core.DegradationGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DegradationConfigTest {

    @AfterEach
    void reset() {
        CacheDegradationSupport.init(DegradationGuard.disabled(), 5000);
    }

    @Test
    void enabledBuildsGuardAndInitializesSupport() {
        CacheAutoConfiguration config = new CacheAutoConfiguration();
        DegradationProperties props = new DegradationProperties();
        props.setEnabled(true);
        props.setPermitsPerSecond(50);
        props.setBulkheadPermits(10);
        props.setBulkheadWaitMs(100);
        props.setSingleFlightWaitMs(3000);

        DegradationGuard guard = config.degradationGuard(props);

        assertThat(guard.isEnabled()).isTrue();
        assertThat(CacheDegradationSupport.guard()).isSameAs(guard);
        assertThat(CacheDegradationSupport.singleFlightWaitMs()).isEqualTo(3000);
    }

    @Test
    void disabledBuildsDisabledGuard() {
        CacheAutoConfiguration config = new CacheAutoConfiguration();
        DegradationProperties props = new DegradationProperties();
        props.setEnabled(false);

        DegradationGuard guard = config.degradationGuard(props);

        assertThat(guard.isEnabled()).isFalse();
        assertThat(CacheDegradationSupport.guard()).isSameAs(guard);
    }
}