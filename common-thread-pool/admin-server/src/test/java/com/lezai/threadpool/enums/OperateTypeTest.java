package com.lezai.threadpool.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperateTypeTest {

    @Test
    @DisplayName("enum has all expected values")
    void values() {
        assertThat(OperateType.values()).containsExactly(
                OperateType.CREATE,
                OperateType.UPDATE,
                OperateType.DELETE,
                OperateType.REGENERATE,
                OperateType.UPSERT
        );
    }
}
