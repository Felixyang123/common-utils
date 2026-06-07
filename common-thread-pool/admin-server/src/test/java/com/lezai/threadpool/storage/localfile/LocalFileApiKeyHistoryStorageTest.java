package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.enums.ChangeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileApiKeyHistoryStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileApiKeyHistoryStorage storage;

    @BeforeEach
    void setUp() {
        String historyDir = tempDir.resolve("apikey-history").toString();
        storage = new LocalFileApiKeyHistoryStorage(historyDir, 100);
    }

    @AfterEach
    void tearDown() throws IOException {
        cleanDir(tempDir.resolve("apikey-history"));
    }

    private void cleanDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    @DisplayName("recordChange and getHistory work correctly")
    void recordAndGet() {
        ApiKey oldKey = TestDataFactory.defaultApiKey().build();
        ApiKey newKey = TestDataFactory.defaultApiKey().appName("Updated").build();

        storage.recordChange("app1", ChangeType.UPDATE, oldKey, newKey);

        List<ChangeLogEntry<ApiKey>> history = storage.getHistory("app1");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getChangeType()).isEqualTo(ChangeType.UPDATE);
        assertThat(history.get(0).getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordChange with blank appId does nothing")
    void recordChange_blankAppId() {
        storage.recordChange("", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().build());
        assertThat(storage.getHistory("")).isEmpty();
    }

    @Test
    @DisplayName("getHistory with blank appId returns empty")
    void getHistory_blankAppId() {
        assertThat(storage.getHistory("")).isEmpty();
    }

    @Test
    @DisplayName("getHistory for non-existent app returns empty")
    void getHistory_nonExistent() {
        assertThat(storage.getHistory("unknown")).isEmpty();
    }

    @Test
    @DisplayName("getHistory returns entries sorted by version")
    void getHistory_sorted() {
        storage.recordChange("app1", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().build());
        storage.recordChange("app1", ChangeType.UPDATE, TestDataFactory.defaultApiKey().build(), TestDataFactory.defaultApiKey().appName("v2").build());

        List<ChangeLogEntry<ApiKey>> history = storage.getHistory("app1");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getVersion()).isEqualTo(1);
        assertThat(history.get(1).getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("getHistory with limit returns most recent entries")
    void getHistory_withLimit() {
        for (int i = 0; i < 5; i++) {
            storage.recordChange("app1", ChangeType.UPDATE,
                    TestDataFactory.defaultApiKey().build(),
                    TestDataFactory.defaultApiKey().appName("v" + i).build());
        }

        List<ChangeLogEntry<ApiKey>> history = storage.getHistory("app1", 2);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getVersion()).isEqualTo(5);
        assertThat(history.get(1).getVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("getHistory with negative or zero limit returns empty")
    void getHistory_zeroLimit() {
        storage.recordChange("app1", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().build());

        assertThat(storage.getHistory("app1", 0)).isEmpty();
        assertThat(storage.getHistory("app1", -1)).isEmpty();
    }

    @Test
    @DisplayName("history is trimmed when exceeding maxHistorySize")
    void maxHistorySize_trimmed() {
        String historyDir = tempDir.resolve("apikey-history-trimmed").toString();
        LocalFileApiKeyHistoryStorage trimmedStorage = new LocalFileApiKeyHistoryStorage(historyDir, 3);

        for (int i = 0; i < 10; i++) {
            trimmedStorage.recordChange("app1", ChangeType.UPDATE,
                    TestDataFactory.defaultApiKey().build(),
                    TestDataFactory.defaultApiKey().appName("v" + i).build());
        }

        assertThat(trimmedStorage.getHistory("app1")).hasSize(3);
    }

    @Test
    @DisplayName("clearHistory removes all history")
    void clearHistory() {
        storage.recordChange("app1", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().build());
        assertThat(storage.getHistory("app1")).hasSize(1);

        storage.clearHistory("app1");
        assertThat(storage.getHistory("app1")).isEmpty();
    }

    @Test
    @DisplayName("clearHistory with blank appId does nothing")
    void clearHistory_blankAppId() {
        storage.clearHistory("");
    }

    @Test
    @DisplayName("recordChange and getHistory maintain version increment across calls")
    void versionIncrement() {
        storage.recordChange("app1", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().build());
        storage.recordChange("app1", ChangeType.UPDATE,
                TestDataFactory.defaultApiKey().build(), TestDataFactory.defaultApiKey().appName("v2").build());
        storage.recordChange("app1", ChangeType.DELETE, TestDataFactory.defaultApiKey().build(), null);

        List<ChangeLogEntry<ApiKey>> history = storage.getHistory("app1");
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getVersion()).isEqualTo(1);
        assertThat(history.get(1).getVersion()).isEqualTo(2);
        assertThat(history.get(2).getVersion()).isEqualTo(3);
    }
}
