package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ThreadPoolAppConfig;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.storage.ConfigStorage;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileConfigStorageTest {

    @TempDir
    Path tempDir;

    private ConfigStorage storage;

    @BeforeEach
    void setUp() {
        String configDir = tempDir.resolve("configs").toString();
        storage = new LocalFileConfigStorage(configDir, new ConfigChangeListenerManager());
    }

    @AfterEach
    void tearDown() throws IOException {
        cleanDir(tempDir.resolve("configs"));
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
    @DisplayName("saveConfig and getConfig work correctly")
    void saveAndGetConfig() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        storage.saveConfig("app1", config);

        Optional<ThreadPoolConfig> retrieved = storage.getConfig("app1", "test-pool");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getPoolName()).isEqualTo("test-pool");
        assertThat(retrieved.get().getCorePoolSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("saveConfigs saves multiple configs for an app")
    void saveConfigs_multiplePools() {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");
        storage.saveConfigs("app1", configs);

        List<ThreadPoolConfig> retrieved = storage.getConfigs("app1");
        assertThat(retrieved).hasSize(2);
    }

    @Test
    @DisplayName("getConfig returns empty for non-existent app")
    void getConfig_nonExistent_null() {
        assertThat(storage.getConfig("unknown-app", "pool")).isEmpty();
    }

    @Test
    @DisplayName("getConfigs returns empty list for non-existent app")
    void getConfigs_nonExistent_empty() {
        assertThat(storage.getConfigs("unknown-app")).isEmpty();
    }

    @Test
    @DisplayName("saveConfig updates existing config")
    void saveConfig_updateExisting() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().corePoolSize(4).build();
        storage.saveConfig("app1", config);

        ThreadPoolConfig updated = TestDataFactory.defaultThreadPoolConfig().corePoolSize(8).build();
        storage.saveConfig("app1", updated);

        Optional<ThreadPoolConfig> retrieved = storage.getConfig("app1", "test-pool");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getCorePoolSize()).isEqualTo(8);
    }

    @Test
    @DisplayName("deleteConfig removes a specific pool config")
    void deleteConfig() {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");
        storage.saveConfigs("app1", configs);

        storage.deleteConfig("app1", "pool-a");

        assertThat(storage.getConfig("app1", "pool-a")).isEmpty();
        assertThat(storage.getConfig("app1", "pool-b")).isPresent();
    }

    @Test
    @DisplayName("deleteConfigs removes all configs for an app")
    void deleteConfigs() {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");
        storage.saveConfigs("app1", configs);

        storage.deleteConfigs("app1");

        assertThat(storage.getConfigs("app1")).isEmpty();
    }

    @Test
    @DisplayName("getConfigVersion returns incrementing version after save")
    void getConfigVersion() {
        assertThat(storage.getConfigVersion("app1")).isZero();

        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        storage.saveConfig("app1", config);
        assertThat(storage.getConfigVersion("app1")).isEqualTo(1);

        storage.saveConfig("app1", TestDataFactory.customThreadPoolConfig("pool-2").build());
        assertThat(storage.getConfigVersion("app1")).isEqualTo(2);
    }

    @Test
    @DisplayName("getAppConfig returns complete app config")
    void getAppConfig() {
        List<ThreadPoolConfig> configs = TestDataFactory.buildConfigList("pool-a", "pool-b");
        storage.saveConfigs("app1", configs);

        Optional<ThreadPoolAppConfig> appConfig = storage.getAppConfig("app1");
        assertThat(appConfig).isPresent();
        assertThat(appConfig.get().getAppId()).isEqualTo("app1");
        assertThat(appConfig.get().getConfigs()).hasSize(2);
    }

    @Test
    @DisplayName("getAppConfig returns empty for non-existent app")
    void getAppConfig_nonExistent_null() {
        assertThat(storage.getAppConfig("unknown-app")).isEmpty();
    }

    @Test
    @DisplayName("addConfig adds new config if not exists")
    void addConfig_new() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().build();
        ThreadPoolConfig result = storage.addConfig("app1", config);

        assertThat(result).isNotNull();
        assertThat(result.getPoolName()).isEqualTo("test-pool");
        assertThat(storage.getConfigVersion("app1")).isEqualTo(1);
    }

    @Test
    @DisplayName("addConfig returns existing config if already exists")
    void addConfig_alreadyExists() {
        ThreadPoolConfig config = TestDataFactory.defaultThreadPoolConfig().corePoolSize(4).build();
        storage.addConfig("app1", config);

        ThreadPoolConfig duplicate = TestDataFactory.defaultThreadPoolConfig().corePoolSize(16).build();
        ThreadPoolConfig result = storage.addConfig("app1", duplicate);

        assertThat(result.getCorePoolSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("addConfigs adds only non-existing configs")
    void addConfigs() {
        ThreadPoolConfig existing = TestDataFactory.customThreadPoolConfig("pool-a").corePoolSize(4).build();
        storage.addConfig("app1", existing);

        List<ThreadPoolConfig> toAdd = List.of(
                TestDataFactory.customThreadPoolConfig("pool-a").corePoolSize(8).build(),
                TestDataFactory.customThreadPoolConfig("pool-b").corePoolSize(2).build()
        );

        List<ThreadPoolConfig> added = storage.addConfigs("app1", toAdd);
        assertThat(added).hasSize(1);
        assertThat(added.get(0).getPoolName()).isEqualTo("pool-b");

        assertThat(storage.getConfig("app1", "pool-a").get().getCorePoolSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("getAllConfigs returns all apps")
    void getAllConfigs() {
        storage.saveConfig("app1", TestDataFactory.customThreadPoolConfig("pool-1").build());
        storage.saveConfig("app2", TestDataFactory.customThreadPoolConfig("pool-2").build());

        Map<String, List<ThreadPoolConfig>> all = storage.getAllConfigs();
        assertThat(all).containsKeys("app1", "app2");
    }

    @Test
    @DisplayName("registerChangeListener receives notifications on config change")
    void registerChangeListener() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder notifiedAppId = new StringBuilder();

        storage.registerChangeListener("app1", (appId, version) -> {
            notifiedAppId.setLength(0);
            notifiedAppId.append(appId);
            latch.countDown();
        });

        storage.saveConfig("app1", TestDataFactory.defaultThreadPoolConfig().build());
        boolean received = latch.await(2, TimeUnit.SECONDS);

        assertThat(received).isTrue();
        assertThat(notifiedAppId.toString()).isEqualTo("app1");
    }
}
