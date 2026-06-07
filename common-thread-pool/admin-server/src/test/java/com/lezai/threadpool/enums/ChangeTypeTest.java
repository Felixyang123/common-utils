package com.lezai.threadpool.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeTypeTest {

    @Test
    @DisplayName("enum has all expected values")
    void values() {
        assertThat(ChangeType.values()).containsExactly(
                ChangeType.CREATE,
                ChangeType.UPDATE,
                ChangeType.DELETE,
                ChangeType.REGENERATE
        );
    }

    @Test
    @DisplayName("CREATE is at ordinal 0")
    void create() {
        assertThat(ChangeType.CREATE.ordinal()).isZero();
        assertThat(ChangeType.CREATE.name()).isEqualTo("CREATE");
    }

    @Test
    @DisplayName("UPDATE has ordinal 1")
    void update() {
        assertThat(ChangeType.UPDATE.ordinal()).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE has ordinal 2")
    void delete() {
        assertThat(ChangeType.DELETE.ordinal()).isEqualTo(2);
    }

    @Test
    @DisplayName("REGENERATE has ordinal 3")
    void regenerate() {
        assertThat(ChangeType.REGENERATE.ordinal()).isEqualTo(3);
    }
}
