package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.converter.ApiKeyConverter;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.ValidationException;
import com.lezai.threadpool.pojo.cmd.ApiKeyUpsertCmd;
import com.lezai.threadpool.pojo.dto.ApiKeyDto;
import com.lezai.threadpool.service.ApiKeyPersistenceService;
import com.lezai.threadpool.utils.ApiKeyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisMysqlApiKeyStorageTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private ApiKeyPersistenceService apiKeyService;

    @Mock
    private ApiKeyConverter apiKeyConverter;

    @Captor
    private ArgumentCaptor<ApiKeyUpsertCmd> upsertCmdCaptor;

    private final ConcurrentHashMap<String, ApiKey> realMap = new ConcurrentHashMap<>();

    private RedisMysqlApiKeyStorage storage;

    @BeforeEach
    void setUp() {
        RMap rMap = createRMapProxy(realMap);
        when(redissonClient.getMap(anyString())).thenReturn(rMap);
        storage = new RedisMysqlApiKeyStorage(redissonClient, apiKeyService, apiKeyConverter) {
            @Override
            public void loadAllFromDb() {
                // no-op: avoid concurrent mock access during constructor
            }
        };
    }

    @Test
    @DisplayName("saveApiKey saves to service and updates cache")
    void saveApiKey() {
        ApiKey key = TestDataFactory.defaultApiKey().build();
        when(apiKeyService.upsert(any(ApiKeyUpsertCmd.class))).thenReturn(true);
        when(apiKeyConverter.convertUpsertCmd(key)).thenReturn(new ApiKeyUpsertCmd());

        storage.saveApiKey(key);

        verify(apiKeyService).upsert(any(ApiKeyUpsertCmd.class));
    }

    @Test
    @DisplayName("saveApiKey throws when appId is null")
    void saveApiKey_nullAppId_throws() {
        ApiKey key = ApiKey.builder().apiKeyHash("hash").build();
        assertThatThrownBy(() -> storage.saveApiKey(key))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("saveApiKey throws when apiKeyHash is null")
    void saveApiKey_nullHash_throws() {
        ApiKey key = ApiKey.builder().appId("app1").build();
        assertThatThrownBy(() -> storage.saveApiKey(key))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("getApiKey loads from cache then falls back to DB")
    void getApiKey_loadFromDb() {
        ApiKeyDto dto = new ApiKeyDto();
        dto.setAppId("app1");
        ApiKey expectedKey = TestDataFactory.defaultApiKey().build();

        when(apiKeyService.findByAppId("app1")).thenReturn(Optional.of(dto));
        when(apiKeyConverter.convertApiKey(dto)).thenReturn(expectedKey);

        Optional<ApiKey> result = storage.getApiKey("app1");

        assertThat(result).isPresent();
        assertThat(result.get().getAppId()).isEqualTo("test-app");
    }

    @Test
    @DisplayName("getApiKey returns empty when not found in cache or DB")
    void getApiKey_notFound() {
        when(apiKeyService.findByAppId("unknown")).thenReturn(Optional.empty());

        Optional<ApiKey> result = storage.getApiKey("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("validateApiKey returns true for valid key")
    void validateApiKey_valid() {
        String plainKey = "my-secret-key";
        String hash = ApiKeyUtils.hashApiKey(plainKey);
        ApiKeyDto dto = new ApiKeyDto();
        dto.setAppId("app1");
        ApiKey storedKey = TestDataFactory.defaultApiKey().apiKeyHash(hash).build();

        when(apiKeyService.findByAppId("app1")).thenReturn(Optional.of(dto));
        when(apiKeyConverter.convertApiKey(dto)).thenReturn(storedKey);

        assertThat(storage.validateApiKey("app1", plainKey)).isTrue();
    }

    @Test
    @DisplayName("validateApiKey returns false for wrong key")
    void validateApiKey_wrongKey() {
        String hash = ApiKeyUtils.hashApiKey("correct-key");
        ApiKeyDto dto = new ApiKeyDto();
        dto.setAppId("app1");
        ApiKey storedKey = TestDataFactory.defaultApiKey().apiKeyHash(hash).build();

        when(apiKeyService.findByAppId("app1")).thenReturn(Optional.of(dto));
        when(apiKeyConverter.convertApiKey(dto)).thenReturn(storedKey);

        assertThat(storage.validateApiKey("app1", "wrong-key")).isFalse();
    }

    @Test
    @DisplayName("validateApiKey returns false for disabled key")
    void validateApiKey_disabled() {
        ApiKeyDto dto = new ApiKeyDto();
        dto.setAppId("disabled-app");
        ApiKey disabledKey = TestDataFactory.disabledApiKey().build();

        when(apiKeyService.findByAppId("disabled-app")).thenReturn(Optional.of(dto));
        when(apiKeyConverter.convertApiKey(dto)).thenReturn(disabledKey);

        assertThat(storage.validateApiKey("disabled-app", "any-key")).isFalse();
    }

    @Test
    @DisplayName("validateApiKey returns false for expired key")
    void validateApiKey_expired() {
        ApiKeyDto dto = new ApiKeyDto();
        dto.setAppId("expired-app");
        ApiKey expiredKey = TestDataFactory.expiredApiKey().build();

        when(apiKeyService.findByAppId("expired-app")).thenReturn(Optional.of(dto));
        when(apiKeyConverter.convertApiKey(dto)).thenReturn(expiredKey);

        assertThat(storage.validateApiKey("expired-app", "any-key")).isFalse();
    }

    @Test
    @DisplayName("validateApiKey returns false when appId is null")
    void validateApiKey_nullAppId() {
        assertThat(storage.validateApiKey(null, "key")).isFalse();
    }

    @Test
    @DisplayName("validateApiKey returns false when apiKey is null")
    void validateApiKey_nullApiKey() {
        assertThat(storage.validateApiKey("app1", null)).isFalse();
    }

    @Test
    @DisplayName("deleteApiKey removes from cache and DB")
    void deleteApiKey() {
        when(apiKeyService.deleteByAppId("app1")).thenReturn(true);

        storage.deleteApiKey("app1");

        verify(apiKeyService).deleteByAppId("app1");
    }

    @Test
    @DisplayName("exists returns true when key is in cache")
    void exists_true() {
        ApiKeyDto dto = new ApiKeyDto();
        dto.setAppId("app1");
        ApiKey key = TestDataFactory.defaultApiKey().build();

        when(apiKeyService.findByAppId("app1")).thenReturn(Optional.of(dto));
        when(apiKeyConverter.convertApiKey(dto)).thenReturn(key);

        assertThat(storage.exists("app1")).isTrue();
    }

    @Test
    @DisplayName("exists returns false when key not found")
    void exists_false() {
        when(apiKeyService.findByAppId("unknown")).thenReturn(Optional.empty());

        assertThat(storage.exists("unknown")).isFalse();
    }

    @Test
    @DisplayName("regenerateApiKey generates new key and updates service")
    void regenerateApiKey() {
        ApiKeyDto dto = new ApiKeyDto();
        dto.setId(1L);
        dto.setAppId("app1");

        when(apiKeyService.findByAppId("app1")).thenReturn(Optional.of(dto));
        when(apiKeyConverter.convertApiKey(dto)).thenReturn(TestDataFactory.defaultApiKey().build());
        when(apiKeyService.update(any(ApiKeyUpsertCmd.class))).thenReturn(true);

        String newKey = storage.regenerateApiKey("app1");

        assertThat(newKey).hasSize(32);
        verify(apiKeyService).update(any(ApiKeyUpsertCmd.class));
    }

    @Test
    @DisplayName("regenerateApiKey throws when appId not found")
    void regenerateApiKey_notFound() {
        when(apiKeyService.findByAppId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storage.regenerateApiKey("unknown"))
                .isInstanceOf(ConfigNotFoundException.class);
    }

    @Test
    @DisplayName("listAllApiKeys returns all cached keys")
    void listAllApiKeys() {
        ApiKeyDto dto1 = new ApiKeyDto();
        dto1.setAppId("app1");
        ApiKeyDto dto2 = new ApiKeyDto();
        dto2.setAppId("app2");

        ApiKey key1 = TestDataFactory.defaultApiKey().appId("app1").build();
        ApiKey key2 = TestDataFactory.defaultApiKey().appId("app2").build();

        when(apiKeyService.findByAppId("app1")).thenReturn(Optional.of(dto1));
        when(apiKeyService.findByAppId("app2")).thenReturn(Optional.of(dto2));
        when(apiKeyConverter.convertApiKey(dto1)).thenReturn(key1);
        when(apiKeyConverter.convertApiKey(dto2)).thenReturn(key2);

        storage.getApiKey("app1");
        storage.getApiKey("app2");

        List<ApiKey> all = storage.listAllApiKeys();
        assertThat(all).hasSize(2);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> RMap createRMapProxy(ConcurrentMap<String, T> backingMap) {
        return (RMap) Proxy.newProxyInstance(
                RMap.class.getClassLoader(),
                new Class<?>[]{RMap.class},
                (proxy, method, args) -> {
                    try {
                        Method targetMethod = ConcurrentHashMap.class.getMethod(
                                method.getName(), method.getParameterTypes());
                        try {
                            return targetMethod.invoke(backingMap, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    } catch (NoSuchMethodException e) {
                        return null;
                    }
                }
        );
    }
}
