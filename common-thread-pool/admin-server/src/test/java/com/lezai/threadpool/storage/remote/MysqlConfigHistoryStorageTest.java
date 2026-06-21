package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.enums.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlConfigHistoryStorageTest {

    private MysqlConfigHistoryStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MysqlConfigHistoryStorage();
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
    @DisplayName("recordChange with null appId does nothing")
    void recordChange_nullAppId() {
        storage.recordChange(null, "pool", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        assertThat(storage.getAllHistory(null)).isEmpty();
    }

    @Test
    @DisplayName("recordChange with blank poolName does nothing")
    void recordChange_blankPoolName() {
        storage.recordChange("app1", "", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        assertThat(storage.getAllHistory("app1")).isEmpty();
    }

    @Test
    @DisplayName("recordChange with null poolName does nothing")
    void recordChange_nullPoolName() {
        storage.recordChange("app1", null, ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        assertThat(storage.getAllHistory("app1")).isEmpty();
    }

    @Test
    @DisplayName("getAllHistory with blank appId returns empty")
    void getAllHistory_blankAppId() {
        assertThat(storage.getAllHistory("")).isEmpty();
    }

    @Test
    @DisplayName("getAllHistory with null appId returns empty")
    void getAllHistory_nullAppId() {
        assertThat(storage.getAllHistory(null)).isEmpty();
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
    @DisplayName("getHistory with null appId returns empty")
    void getHistory_nullAppId() {
        assertThat(storage.getHistory(null, "pool")).isEmpty();
    }

    @Test
    @DisplayName("getHistory with blank poolName returns empty")
    void getHistory_blankPoolName() {
        assertThat(storage.getHistory("app1", "")).isEmpty();
    }

    @Test
    @DisplayName("getHistory with null poolName returns empty")
    void getHistory_nullPoolName() {
        assertThat(storage.getHistory("app1", (String) null)).isEmpty();
    }

    @Test
    @DisplayName("getHistory for non-existent pool returns empty")
    void getHistory_nonExistentPool() {
        storage.recordChange("app1", "pool-a", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        assertThat(storage.getHistory("app1", "nonexistent-pool")).isEmpty();
    }

    @Test
    @DisplayName("getHistory returns sorted entries (ascending)")
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
    @DisplayName("getHistory with limit returns most recent entries (descending)")
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
        assertThat(storage.getHistory("app1", "test-pool", -1)).isEmpty();
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
    @DisplayName("clearHistory with null appId does nothing")
    void clearHistory_nullAppId() {
        storage.clearHistory((String) null);
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
    @DisplayName("clearHistory with null poolName does nothing")
    void clearHistory_nullPoolName() {
        storage.clearHistory("app1", (String) null);
    }

    @Test
    @DisplayName("clearHistory with blank appId and poolName does nothing")
    void clearHistory_blankAppIdAndPoolName() {
        storage.clearHistory("", "pool");
    }

    @Test
    @DisplayName("version increments correctly across multiple pools")
    void versionIncrement() {
        storage.recordChange("app1", "pool-a", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().poolName("pool-a").build());
        storage.recordChange("app1", "pool-b", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().poolName("pool-b").build());

        assertThat(storage.getHistory("app1", "pool-a").get(0).getVersion()).isEqualTo(1);
        assertThat(storage.getHistory("app1", "pool-b").get(0).getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("different appIds have independent version counters")
    void independentVersionCounters() {
        storage.recordChange("app1", "pool", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());
        storage.recordChange("app2", "pool", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());

        assertThat(storage.getHistory("app1", "pool").get(0).getVersion()).isEqualTo(1);
        assertThat(storage.getHistory("app2", "pool").get(0).getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAllHistory returns unmodifiable map")
    void getAllHistory_unmodifiable() {
        storage.recordChange("app1", "pool", ChangeType.CREATE, null,
                TestDataFactory.defaultThreadPoolConfig().build());

        Map<String, List<ChangeLogEntry<ThreadPoolConfig>>> history = storage.getAllHistory("app1");
        assertThat(history).containsKey("pool");

        // Clearing storage should not affect the returned map (it's a snapshot)
        storage.clearHistory("app1");
        assertThat(history).containsKey("pool");
    }

    @Test
    @DisplayName("recordChange with CREATE type stores null oldValue")
    void recordChange_create() {
        ThreadPoolConfig newConfig = TestDataFactory.defaultThreadPoolConfig().build();
        storage.recordChange("app1", "pool", ChangeType.CREATE, null, newConfig);

        List<ChangeLogEntry<ThreadPoolConfig>> history = storage.getHistory("app1", "pool");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getChangeType()).isEqualTo(ChangeType.CREATE);
        assertThat(history.get(0).getOldValue()).isNull();
        assertThat(history.get(0).getNewValue()).isNotNull();
    }

    @Test
    @DisplayName("recordChange with DELETE type stores null newValue")
    void recordChange_delete() {
        ThreadPoolConfig oldConfig = TestDataFactory.defaultThreadPoolConfig().build();
        storage.recordChange("app1", "pool", ChangeType.DELETE, oldConfig, null);

        List<ChangeLogEntry<ThreadPoolConfig>> history = storage.getHistory("app1", "pool");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getChangeType()).isEqualTo(ChangeType.DELETE);
        assertThat(history.get(0).getOldValue()).isNotNull();
        assertThat(history.get(0).getNewValue()).isNull();
    }
}
