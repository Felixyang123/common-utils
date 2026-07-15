package com.lezai.threadpool.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BizTypeTest {

    @Test
    @DisplayName("enum has all expected values")
    void values() {
        assertThat(BizType.values()).containsExactly(
                BizType.APIKEY,
                BizType.THREADPOOL_CONFIG,
                BizType.THREAD_POOL_STATS
        );
    }
}
