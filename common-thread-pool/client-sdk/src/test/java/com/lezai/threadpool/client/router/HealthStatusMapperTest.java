package com.lezai.threadpool.client.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthStatusMapperTest {

    @Test
    void mapsUp() {
        assertThat(HealthStatusMapper.fromResponse("UP")).isEqualTo(NodeHealthStatus.UP);
    }

    @Test
    void mapsUpCaseInsensitive() {
        assertThat(HealthStatusMapper.fromResponse("up")).isEqualTo(NodeHealthStatus.UP);
    }

    @Test
    void mapsDegraded() {
        assertThat(HealthStatusMapper.fromResponse("DEGRADED")).isEqualTo(NodeHealthStatus.DEGRADED);
    }

    @Test
    void mapsDown() {
        assertThat(HealthStatusMapper.fromResponse("DOWN")).isEqualTo(NodeHealthStatus.DOWN);
    }

    @Test
    void mapsNullToUnknown() {
        assertThat(HealthStatusMapper.fromResponse(null)).isEqualTo(NodeHealthStatus.UNKNOWN);
    }

    @Test
    void mapsUnknownValueToUnknown() {
        assertThat(HealthStatusMapper.fromResponse("bogus")).isEqualTo(NodeHealthStatus.UNKNOWN);
    }
}
