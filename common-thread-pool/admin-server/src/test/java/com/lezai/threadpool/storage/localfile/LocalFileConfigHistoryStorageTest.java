package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.bean.ThreadPoolConfig;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileConfigHistoryStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileConfigHistoryStorage storage;

    @BeforeEach
    void setUp() {
        String historyDir = tempDir.resolve("config-history").toString();
        storage = new LocalFileConfigHistoryStorage(historyDir, 100);
    }

    @AfterEach
    void tearDown() throws IOException {
        cleanDir(tempDir.resolve("config-history/history"));
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
    @DisplayName("recordChange and getAllHistory work correctly")
    void recordAndGetAll() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        storage.recordChange("app1", "test-pool", ChangeType.CREATE, null, config);

        Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> allHistory = storage.getAllHistory("app1");
        assertThat(allHistory).containsKey("test-pool");
        assertThat(allHistory.get("test-pool")).hasSize(1);
        assertThat(allHistory.get("test-pool").get(0).getChangeType()).isEqualTo(ChangeType.CREATE);
    }

    @Test
    @DisplayName("recordChange with blank appId does nothing")
    void recordChange_blankAppId() {
        storage.recordChange("", "pool", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        assertThat(storage.getAllHistory("")).isEmpty();
    }

    @Test
    @DisplayName("recordChange with blank poolName does nothing")
    void recordChange_blankPoolName() {
        storage.recordChange("app1", "", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        assertThat(storage.getAllHistory("app1")).isEmpty();
    }

    @Test
    @DisplayName("getAllHistory with blank appId returns empty")
    void getAllHistory_blankAppId() {
        assertThat(storage.getAllHistory("")).isEmpty();
    }

    @Test
    @DisplayName("getAllHistory for non-existent app returns empty")
    void getAllHistory_nonExistent() {
        assertThat(storage.getAllHistory("unknown")).isEmpty();
    }

    @Test
    @DisplayName("getHistory returns entries for a specific pool")
    void getHistory() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        storage.recordChange("app1", "test-pool", ChangeType.CREATE, null, config);

        List<ChangeLogEntry<ThreadPoolConfig>> history = storage.getHistory("app1", "test-pool");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("getHistory with blank appId returns empty")
    void getHistory_blankAppId() {
        assertThat(storage.getHistory("", "pool")).isEmpty();
    }

    @Test
    @DisplayName("getHistory with blank poolName returns empty")
    void getHistory_blankPoolName() {
        assertThat(storage.getHistory("app1", "")).isEmpty();
    }

    @Test
    @DisplayName("getHistory for non-existent pool returns empty")
    void getHistory_nonExistentPool() {
        storage.recordChange("app1", "pool-a", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        assertThat(storage.getHistory("app1", "nonexistent-pool")).isEmpty();
    }

    @Test
    @DisplayName("getHistory returns sorted entries")
    void getHistory_sorted() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        storage.recordChange("app1", "test-pool", ChangeType.CREATE, null, config);
        storage.recordChange("app1", "test-pool", ChangeType.UPDATE, config,
                TestDataFactory.defaultThreadPoolConfig().corePoolSize(8).build());

        List<ChangeLogEntry<ThreadPoolConfig>> history = storage.getHistory("app1", "test-pool");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getVersion()).isEqualTo(1);
        assertThat(history.get(1).getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("getHistory with limit returns most recent entries")
    void getHistory_withLimit() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        for (int i = 0; i < 5; i++) {
            storage.recordChange("app1", "test-pool", ChangeType.UPDATE, config,
                    TestDataFactory.defaultThreadPoolConfig().corePoolSize(i + 1).build());
        }

        List<ChangeLogEntry<ThreadPoolConfig>> history = storage.getHistory("app1", "test-pool", 3);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getVersion()).isEqualTo(5);
        assertThat(history.get(1).getVersion()).isEqualTo(4);
        assertThat(history.get(2).getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("getHistory with limit when history is empty returns empty")
    void getHistory_withLimit_empty() {
        assertThat(storage.getHistory("app1", "pool", 5)).isEmpty();
    }

    @Test
    @DisplayName("getHistory with negative or zero limit returns empty")
    void getHistory_zeroLimit() {
        storage.recordChange("app1", "test-pool", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        assertThat(storage.getHistory("app1", "test-pool", 0)).isEmpty();
    }

    @Test
    @DisplayName("history is trimmed when exceeding maxHistorySize")
    void maxHistorySize_trimmed() {
        String historyDir = tempDir.resolve("config-history-trimmed").toString();
        LocalFileConfigHistoryStorage trimmedStorage = new LocalFileConfigHistoryStorage(historyDir, 3);

        for (int i = 0; i < 10; i++) {
            trimmedStorage.recordChange("app1", "test-pool", ChangeType.UPDATE,
                    TestDataFactory.defaultThreadPoolConfig().build(),
                    TestDataFactory.defaultThreadPoolConfig().corePoolSize(i + 1).build());
        }

        List<ChangeLogEntry<ThreadPoolConfig>> history = trimmedStorage.getHistory("app1", "test-pool");
        assertThat(history).hasSize(3);
    }

    @Test
    @DisplayName("clearHistory removes all history for app")
    void clearHistory() {
        storage.recordChange("app1", "test-pool", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        storage.recordChange("app1", "pool-b", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().poolName("pool-b").build());

        storage.clearHistory("app1");
        assertThat(storage.getAllHistory("app1")).isEmpty();
    }

    @Test
    @DisplayName("clearHistory with blank appId does nothing")
    void clearHistory_blankAppId() {
        storage.clearHistory("");
    }

    @Test
    @DisplayName("clearHistory removes specific pool history")
    void clearHistory_pool() {
        storage.recordChange("app1", "pool-a", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().poolName("pool-a").build());
        storage.recordChange("app1", "pool-b", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().poolName("pool-b").build());

        storage.clearHistory("app1", "pool-a");

        assertThat(storage.getHistory("app1", "pool-a")).isEmpty();
        assertThat(storage.getHistory("app1", "pool-b")).hasSize(1);
    }

    @Test
    @DisplayName("clearHistory with blank poolName does nothing")
    void clearHistory_blankPoolName() {
        storage.clearHistory("app1", "");
    }

    @Test
    @DisplayName("clearHistory with blank appId and poolName does nothing")
    void clearHistory_blankAppIdAndPoolName() {
        storage.clearHistory("", "pool");
    }

    @Test
    @DisplayName("version increments correctly across multiple changes")
    void versionIncrement() {
        storage.recordChange("app1", "pool-a", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().poolName("pool-a").build());
        storage.recordChange("app1", "pool-b", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().poolName("pool-b").build());

        assertThat(storage.getHistory("app1", "pool-a").get(0).getVersion()).isEqualTo(1);
        assertThat(storage.getHistory("app1", "pool-b").get(0).getVersion()).isEqualTo(2);
    }
}
