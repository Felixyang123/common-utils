package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.enums.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlApiKeyHistoryStorageTest {

    private MysqlApiKeyHistoryStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MysqlApiKeyHistoryStorage();
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
    @DisplayName("recordChange with blank appId logs warning and does nothing")
    void recordChange_blankAppId() {
        storage.recordChange("", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().build());
        assertThat(storage.getHistory("")).isEmpty();
    }

    @Test
    @DisplayName("recordChange with null appId logs warning and does nothing")
    void recordChange_nullAppId() {
        storage.recordChange(null, ChangeType.CREATE, null, TestDataFactory.defaultApiKey().build());
        assertThat(storage.getHistory(null)).isEmpty();
    }

    @Test
    @DisplayName("getHistory with blank appId returns empty")
    void getHistory_blankAppId() {
        assertThat(storage.getHistory("")).isEmpty();
    }

    @Test
    @DisplayName("getHistory with null appId returns empty")
    void getHistory_nullAppId() {
        assertThat(storage.getHistory(null)).isEmpty();
    }

    @Test
    @DisplayName("getHistory for non-existent app returns empty")
    void getHistory_nonExistent() {
        assertThat(storage.getHistory("unknown")).isEmpty();
    }

    @Test
    @DisplayName("getHistory returns entries sorted by version (ascending)")
    void getHistory_sorted() {
        storage.recordChange("app1", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().build());
        storage.recordChange("app1", ChangeType.UPDATE,
                TestDataFactory.defaultApiKey().build(),
                TestDataFactory.defaultApiKey().appName("v2").build());

        List<ChangeLogEntry<ApiKey>> history = storage.getHistory("app1");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getVersion()).isEqualTo(1);
        assertThat(history.get(1).getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("getHistory with limit returns most recent entries (descending)")
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
    @DisplayName("getHistory with limit when history is empty returns empty")
    void getHistory_withLimit_empty() {
        assertThat(storage.getHistory("app1", 5)).isEmpty();
    }

    @Test
    @DisplayName("getHistory with negative or zero limit returns empty")
    void getHistory_zeroLimit() {
        storage.recordChange("app1", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().build());

        assertThat(storage.getHistory("app1", 0)).isEmpty();
        assertThat(storage.getHistory("app1", -1)).isEmpty();
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
    @DisplayName("clearHistory with null appId does nothing")
    void clearHistory_nullAppId() {
        storage.clearHistory(null);
    }

    @Test
    @DisplayName("recordChange and getHistory maintain version increment across calls")
    void versionIncrement() {
        storage.recordChange("app1", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().build());
        storage.recordChange("app1", ChangeType.UPDATE,
                TestDataFactory.defaultApiKey().build(),
                TestDataFactory.defaultApiKey().appName("v2").build());
        storage.recordChange("app1", ChangeType.DELETE, TestDataFactory.defaultApiKey().build(), null);

        List<ChangeLogEntry<ApiKey>> history = storage.getHistory("app1");
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getVersion()).isEqualTo(1);
        assertThat(history.get(1).getVersion()).isEqualTo(2);
        assertThat(history.get(2).getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("different appIds have independent version counters")
    void independentVersionCounters() {
        storage.recordChange("app1", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().appId("app1").build());
        storage.recordChange("app2", ChangeType.CREATE, null, TestDataFactory.defaultApiKey().appId("app2").build());

        assertThat(storage.getHistory("app1").get(0).getVersion()).isEqualTo(1);
        assertThat(storage.getHistory("app2").get(0).getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordChange with CREATE type stores null oldValue")
    void recordChange_create() {
        ApiKey newKey = TestDataFactory.defaultApiKey().build();
        storage.recordChange("app1", ChangeType.CREATE, null, newKey);

        List<ChangeLogEntry<ApiKey>> history = storage.getHistory("app1");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getChangeType()).isEqualTo(ChangeType.CREATE);
        assertThat(history.get(0).getOldValue()).isNull();
        assertThat(history.get(0).getNewValue()).isNotNull();
    }

    @Test
    @DisplayName("recordChange with DELETE type stores null newValue")
    void recordChange_delete() {
        ApiKey oldKey = TestDataFactory.defaultApiKey().build();
        storage.recordChange("app1", ChangeType.DELETE, oldKey, null);

        List<ChangeLogEntry<ApiKey>> history = storage.getHistory("app1");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getChangeType()).isEqualTo(ChangeType.DELETE);
        assertThat(history.get(0).getOldValue()).isNotNull();
        assertThat(history.get(0).getNewValue()).isNull();
    }

    @Test
    @DisplayName("ConcurrentHashMap handles concurrent access safely")
    void concurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int changesPerThread = 100;
        AtomicInteger counter = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < changesPerThread; j++) {
                        storage.recordChange("concurrent-app", ChangeType.UPDATE,
                                TestDataFactory.defaultApiKey().build(),
                                TestDataFactory.defaultApiKey().appName("v" + counter.incrementAndGet()).build());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        List<ChangeLogEntry<ApiKey>> history = storage.getHistory("concurrent-app");
        // Each thread makes `changesPerThread` changes, so total should be threadCount * changesPerThread
        assertThat(history).hasSize(threadCount * changesPerThread);
        // Versions should be sequential from 1 to N
        assertThat(history.get(0).getVersion()).isEqualTo(1);
        assertThat(history.get(history.size() - 1).getVersion()).isEqualTo(threadCount * changesPerThread);
    }
}
