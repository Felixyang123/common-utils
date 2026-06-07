package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.storage.StatsStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStatsStorageTest {

    @TempDir
    Path tempDir;

    private StatsStorage storage;

    @BeforeEach
    void setUp() {
        String statsDir = tempDir.resolve("stats").toString();
        storage = new LocalFileStatsStorage(statsDir, 100);
    }

    @AfterEach
    void tearDown() throws IOException {
        cleanDir(tempDir.resolve("stats"));
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
    @DisplayName("saveStats and getPoolStatsHistory work correctly")
    void saveAndGetStats() {
        ThreadPoolStats stats = TestDataFactory.defaultStats().build();
        storage.saveStats("app1", List.of(stats));

        LocalDateTime now = LocalDateTime.now();
        List<ThreadPoolStats> history = storage.getPoolStatsHistory("app1", "test-pool",
                now.minusMinutes(5), now.plusMinutes(5));

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPoolName()).isEqualTo("test-pool");
    }

    @Test
    @DisplayName("saveStats with blank appId does nothing")
    void saveStats_blankAppId() {
        ThreadPoolStats stats = TestDataFactory.defaultStats().build();
        storage.saveStats("", List.of(stats));

        List<ThreadPoolStats> history = storage.getPoolStatsHistory("", "test-pool",
                null, null);
        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("saveStats with empty list does nothing")
    void saveStats_emptyList() {
        storage.saveStats("app1", Collections.emptyList());

        List<ThreadPoolStats> history = storage.getPoolStatsHistory("app1", "test-pool",
                null, null);
        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("saveStats accumulates history for multiple pools")
    void saveStats_multiplePools() {
        ThreadPoolStats stats1 = TestDataFactory.defaultStats().poolName("pool-a").build();
        ThreadPoolStats stats2 = TestDataFactory.defaultStats().poolName("pool-b").build();
        storage.saveStats("app1", List.of(stats1, stats2));

        LocalDateTime now = LocalDateTime.now();
        assertThat(storage.getPoolStatsHistory("app1", "pool-a", now.minusMinutes(5), now.plusMinutes(5))).hasSize(1);
        assertThat(storage.getPoolStatsHistory("app1", "pool-b", now.minusMinutes(5), now.plusMinutes(5))).hasSize(1);
    }

    @Test
    @DisplayName("getPoolStatsHistory with blank appId returns empty")
    void getPoolStatsHistory_blankAppId() {
        assertThat(storage.getPoolStatsHistory("", "pool", null, null)).isEmpty();
    }

    @Test
    @DisplayName("getPoolStatsHistory with blank poolName returns empty")
    void getPoolStatsHistory_blankPoolName() {
        assertThat(storage.getPoolStatsHistory("app1", "", null, null)).isEmpty();
    }

    @Test
    @DisplayName("getPoolStatsHistory filters by time range")
    void getPoolStatsHistory_timeFilter() {
        ThreadPoolStats stats = TestDataFactory.defaultStats().build();
        storage.saveStats("app1", List.of(stats));

        LocalDateTime now = LocalDateTime.now();
        List<ThreadPoolStats> history = storage.getPoolStatsHistory("app1", "test-pool",
                now.plusMinutes(1), now.plusMinutes(10));

        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("getPoolStatsHistory for non-existent app returns empty")
    void getPoolStatsHistory_nonExistent() {
        assertThat(storage.getPoolStatsHistory("unknown", "pool", null, null)).isEmpty();
    }

    @Test
    @DisplayName("history is trimmed when exceeding maxHistorySize")
    void maxHistorySize_trimmed() {
        String statsDir = tempDir.resolve("stats-trimmed").toString();
        StatsStorage trimmedStorage = new LocalFileStatsStorage(statsDir, 5);

        for (int i = 0; i < 10; i++) {
            ThreadPoolStats stats = TestDataFactory.defaultStats().build();
            trimmedStorage.saveStats("app1", List.of(stats));
        }

        List<ThreadPoolStats> history = trimmedStorage.getPoolStatsHistory("app1", "test-pool", null, null);
        assertThat(history).hasSize(5);
    }
}
