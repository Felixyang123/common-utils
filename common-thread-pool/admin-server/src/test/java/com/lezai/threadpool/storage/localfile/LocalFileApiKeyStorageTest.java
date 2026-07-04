package com.lezai.threadpool.storage.localfile;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.utils.ApiKeyUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileApiKeyStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileApiKeyStorage storage;

    @BeforeEach
    void setUp() {
        String storageDir = tempDir.resolve("api-keys").toString();
        storage = new LocalFileApiKeyStorage(storageDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        cleanDir(tempDir.resolve("api-keys"));
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
    @DisplayName("saveApiKey and getApiKey work correctly")
    void saveAndGet() {
        ApiKey key = TestDataFactory.defaultApiKey().build();
        storage.saveApiKey(key);

        Optional<ApiKey> result = storage.getApiKey("test-app");
        assertThat(result).isPresent();
        assertThat(result.get().getAppId()).isEqualTo("test-app");
        assertThat(result.get().getAppName()).isEqualTo("Test Application");
    }

    @Test
    @DisplayName("saveApiKey throws when appId is blank")
    void saveApiKey_blankAppId_throws() {
        ApiKey key = ApiKey.builder().appId("").apiKeyHash("hash").build();
        assertThatThrownBy(() -> storage.saveApiKey(key))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("saveApiKey throws when apiKeyHash is null")
    void saveApiKey_nullHash_throws() {
        ApiKey key = ApiKey.builder().appId("app").apiKeyHash(null).build();
        assertThatThrownBy(() -> storage.saveApiKey(key))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("getApiKey returns empty for non-existent app")
    void getApiKey_nonExistent_empty() {
        assertThat(storage.getApiKey("unknown")).isEmpty();
    }

    @Test
    @DisplayName("validateApiKey returns true for valid key")
    void validateApiKey_valid() {
        String plainKey = "my-secret-key-123";
        String hash = ApiKeyUtils.hashApiKey(plainKey);
        ApiKey key = TestDataFactory.defaultApiKey().apiKeyHash(hash).build();
        storage.saveApiKey(key);

        assertThat(storage.validateApiKey("test-app", plainKey)).isTrue();
    }

    @Test
    @DisplayName("validateApiKey returns false for wrong key")
    void validateApiKey_wrongKey() {
        String hash = ApiKeyUtils.hashApiKey("correct-key");
        ApiKey key = TestDataFactory.defaultApiKey().apiKeyHash(hash).build();
        storage.saveApiKey(key);

        assertThat(storage.validateApiKey("test-app", "wrong-key")).isFalse();
    }

    @Test
    @DisplayName("validateApiKey returns false for disabled key")
    void validateApiKey_disabled() {
        ApiKey key = TestDataFactory.disabledApiKey().build();
        storage.saveApiKey(key);

        assertThat(storage.validateApiKey("disabled-app", "any-key")).isFalse();
    }

    @Test
    @DisplayName("validateApiKey returns false for expired key")
    void validateApiKey_expired() {
        ApiKey key = TestDataFactory.expiredApiKey().build();
        storage.saveApiKey(key);

        assertThat(storage.validateApiKey("expired-app", "any-key")).isFalse();
    }

    @Test
    @DisplayName("validateApiKey returns false for blank appId")
    void validateApiKey_blankAppId() {
        assertThat(storage.validateApiKey("", "key")).isFalse();
    }

    @Test
    @DisplayName("deleteApiKey removes the key")
    void deleteApiKey() {
        ApiKey key = TestDataFactory.defaultApiKey().build();
        storage.saveApiKey(key);
        assertThat(storage.exists("test-app")).isTrue();

        storage.deleteApiKey("test-app");
        assertThat(storage.exists("test-app")).isFalse();
        assertThat(storage.getApiKey("test-app")).isEmpty();
    }

    @Test
    @DisplayName("listAllApiKeys returns all saved keys")
    void listAllApiKeys() {
        storage.saveApiKey(TestDataFactory.defaultApiKey().appId("app1").build());
        storage.saveApiKey(TestDataFactory.defaultApiKey().appId("app2").build());

        List<ApiKey> all = storage.listAllApiKeys();
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("exists returns correct status")
    void exists() {
        assertThat(storage.exists("test-app")).isFalse();
        storage.saveApiKey(TestDataFactory.defaultApiKey().build());
        assertThat(storage.exists("test-app")).isTrue();
    }

    @Test
    @DisplayName("regenerateApiKey generates new key and returns plaintext")
    void regenerateApiKey() {
        String originalHash = ApiKeyUtils.hashApiKey("original-key");
        ApiKey key = TestDataFactory.defaultApiKey().apiKeyHash(originalHash).build();
        storage.saveApiKey(key);

        String newPlainKey = storage.regenerateApiKey("test-app");
        assertThat(newPlainKey).hasSize(32);

        Optional<ApiKey> updated = storage.getApiKey("test-app");
        assertThat(updated).isPresent();
        assertThat(updated.get().getApiKeyHash()).isNotEqualTo(originalHash);
        assertThat(ApiKeyUtils.validateApiKey(newPlainKey, updated.get().getApiKeyHash())).isTrue();
    }

    @Test
    @DisplayName("regenerateApiKey throws for non-existent app")
    void regenerateApiKey_nonExistent_throws() {
        assertThatThrownBy(() -> storage.regenerateApiKey("unknown"))
                .isInstanceOf(ConfigNotFoundException.class);
    }

    @Test
    @DisplayName("saveApiKey updates existing key")
    void saveApiKey_update() {
        ApiKey key = TestDataFactory.defaultApiKey().build();
        storage.saveApiKey(key);

        ApiKey updated = TestDataFactory.defaultApiKey().appName("Updated Name").build();
        storage.saveApiKey(updated);

        Optional<ApiKey> result = storage.getApiKey("test-app");
        assertThat(result).isPresent();
        assertThat(result.get().getAppName()).isEqualTo("Updated Name");
    }
}
