package com.lezai.threadpool.bean;

import com.lezai.threadpool.enums.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeLogEntryTest {

    @Test
    @DisplayName("of creates entry with all fields")
    void of_withOperator() {
        String oldVal = "old";
        String newVal = "new";

        ChangeLogEntry<String> entry = ChangeLogEntry.of(1, ChangeType.UPDATE, oldVal, newVal, "admin");

        assertThat(entry.getVersion()).isEqualTo(1);
        assertThat(entry.getChangeType()).isEqualTo(ChangeType.UPDATE);
        assertThat(entry.getOldValue()).isEqualTo("old");
        assertThat(entry.getNewValue()).isEqualTo("new");
        assertThat(entry.getOperator()).isEqualTo("admin");
        assertThat(entry.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("of without operator sets operator to null")
    void of_withoutOperator() {
        ChangeLogEntry<String> entry = ChangeLogEntry.of(2, ChangeType.CREATE, null, "new");

        assertThat(entry.getVersion()).isEqualTo(2);
        assertThat(entry.getChangeType()).isEqualTo(ChangeType.CREATE);
        assertThat(entry.getOldValue()).isNull();
        assertThat(entry.getNewValue()).isEqualTo("new");
        assertThat(entry.getOperator()).isNull();
    }

    @Test
    @DisplayName("builder works correctly")
    void builder() {
        LocalDateTime now = LocalDateTime.now();
        ChangeLogEntry<String> entry = ChangeLogEntry.<String>builder()
                .version(3)
                .changeType(ChangeType.DELETE)
                .oldValue("old")
                .timestamp(now)
                .build();

        assertThat(entry.getVersion()).isEqualTo(3);
        assertThat(entry.getChangeType()).isEqualTo(ChangeType.DELETE);
        assertThat(entry.getOldValue()).isEqualTo("old");
        assertThat(entry.getNewValue()).isNull();
        assertThat(entry.getTimestamp()).isEqualTo(now);
    }
}
