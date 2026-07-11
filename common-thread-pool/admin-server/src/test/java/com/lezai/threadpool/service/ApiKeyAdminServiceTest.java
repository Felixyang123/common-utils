package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.controller.dto.request.CreateApiKeyRequest;
import com.lezai.threadpool.controller.dto.request.UpdateApiKeyRequest;
import com.lezai.threadpool.controller.dto.response.ApiKeyInfoResponse;
import com.lezai.threadpool.controller.dto.response.CreateApiKeyResponse;
import com.lezai.threadpool.controller.dto.response.RegenerateApiKeyResponse;
import com.lezai.threadpool.exception.ResoureAlreadyExistsException;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.storage.ApiKeyStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAdminServiceTest {

    @Mock
    private ApiKeyStorage apiKeyStorage;

    private ApiKeyAdminService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyAdminService(apiKeyStorage);
    }

    // ==================== createApiKey ====================

    @Test
    @DisplayName("createApiKey creates API key successfully")
    void createApiKey_success() {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setAppId("my-app");
        request.setAppName("My App");
        request.setDescription("Test app");
        request.setExpireTime(LocalDateTime.now().plusDays(30));

        when(apiKeyStorage.putIfAbsent(any(ApiKey.class))).thenReturn(true);

        CreateApiKeyResponse response = service.createApiKey(request);

        assertThat(response).isNotNull();
        assertThat(response.getAppId()).isEqualTo("my-app");
        assertThat(response.getAppName()).isEqualTo("My App");
        assertThat(response.getApiKey()).isNotEmpty();
        assertThat(response.getDescription()).isEqualTo("Test app");
        assertThat(response.getExpireTime()).isNotNull();
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getCreateTime()).isNotNull();

        verify(apiKeyStorage).putIfAbsent(any(ApiKey.class));
    }

    @Test
    @DisplayName("createApiKey throws ConfigAlreadyExistsException when appId exists")
    void createApiKey_alreadyExists() {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setAppId("my-app");
        request.setAppName("My App");

        when(apiKeyStorage.putIfAbsent(any(ApiKey.class))).thenReturn(false);

        assertThatThrownBy(() -> service.createApiKey(request))
                .isInstanceOf(ResoureAlreadyExistsException.class)
                .hasMessageContaining("my-app");
    }

    @Test
    @DisplayName("createApiKey with null expireTime works")
    void createApiKey_nullExpireTime() {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setAppId("my-app");
        request.setAppName("My App");

        when(apiKeyStorage.putIfAbsent(any(ApiKey.class))).thenReturn(true);

        CreateApiKeyResponse response = service.createApiKey(request);

        assertThat(response).isNotNull();
        assertThat(response.getExpireTime()).isNull();
    }

    // ==================== deleteApiKey ====================

    @Test
    @DisplayName("deleteApiKey deletes successfully")
    void deleteApiKey_success() {
        when(apiKeyStorage.exists("my-app")).thenReturn(true);

        service.deleteApiKey("my-app");

        verify(apiKeyStorage).deleteApiKey("my-app");
    }

    @Test
    @DisplayName("deleteApiKey throws ConfigNotFoundException when not found")
    void deleteApiKey_notFound() {
        when(apiKeyStorage.exists("unknown")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteApiKey("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");

        verify(apiKeyStorage, never()).deleteApiKey(anyString());
    }

    // ==================== getApiKey ====================

    @Test
    @DisplayName("getApiKey returns API key info when found")
    void getApiKey_success() {
        LocalDateTime now = LocalDateTime.now();
        ApiKey key = ApiKey.builder()
                .appId("my-app")
                .appName("My App")
                .enabled(true)
                .createTime(now)
                .expireTime(now.plusDays(30))
                .updateTime(now)
                .description("Test")
                .build();

        when(apiKeyStorage.getApiKey("my-app")).thenReturn(Optional.of(key));

        ApiKeyInfoResponse response = service.getApiKey("my-app");

        assertThat(response).isNotNull();
        assertThat(response.getAppId()).isEqualTo("my-app");
        assertThat(response.getAppName()).isEqualTo("My App");
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getDescription()).isEqualTo("Test");
        assertThat(response.getCreateTime()).isEqualTo(now);
        assertThat(response.getExpireTime()).isEqualTo(now.plusDays(30));
        assertThat(response.getUpdateTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("getApiKey throws ConfigNotFoundException when not found")
    void getApiKey_notFound() {
        when(apiKeyStorage.getApiKey("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApiKey("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    @DisplayName("getApiKey maps expired and valid correctly")
    void getApiKey_expiredAndValid() {
        LocalDateTime past = LocalDateTime.now().minusDays(10);
        ApiKey expiredKey = ApiKey.builder()
                .appId("expired-app")
                .appName("Expired")
                .enabled(true)
                .expireTime(past)
                .createTime(past)
                .build();

        when(apiKeyStorage.getApiKey("expired-app")).thenReturn(Optional.of(expiredKey));

        ApiKeyInfoResponse response = service.getApiKey("expired-app");

        assertThat(response.isExpired()).isTrue();
        assertThat(response.isValid()).isFalse();
    }

    // ==================== listAllApiKeys ====================

    @Test
    @DisplayName("listAllApiKeys returns empty list when no keys")
    void allApiKeys_empty() {
        when(apiKeyStorage.allApiKeys()).thenReturn(Collections.emptyList());

        List<ApiKeyInfoResponse> result = service.allApiKeys();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listAllApiKeys returns multiple keys")
    void allApiKeys_multiple() {
        ApiKey key1 = ApiKey.builder().appId("app1").appName("App 1").enabled(true).build();
        ApiKey key2 = ApiKey.builder().appId("app2").appName("App 2").enabled(true).build();

        when(apiKeyStorage.allApiKeys()).thenReturn(List.of(key1, key2));

        List<ApiKeyInfoResponse> result = service.allApiKeys();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAppId()).isEqualTo("app1");
        assertThat(result.get(1).getAppId()).isEqualTo("app2");
    }

    // ==================== updateApiKey ====================

    @Test
    @DisplayName("updateApiKey updates successfully")
    void updateApiKey_success() {
        UpdateApiKeyRequest request = new UpdateApiKeyRequest();
        request.setAppName("Updated App");
        request.setEnabled(true);
        request.setDescription("Updated description");

        ApiKey existing = ApiKey.builder()
                .appId("my-app")
                .apiKeyHash("existing-hash")
                .appName("Old App")
                .enabled(true)
                .createTime(LocalDateTime.now().minusDays(1))
                .build();
        when(apiKeyStorage.getApiKey("my-app")).thenReturn(Optional.of(existing));

        service.updateApiKey("my-app", request);

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyStorage).updateApiKey(captor.capture());
        ApiKey saved = captor.getValue();
        assertThat(saved.getAppId()).isEqualTo("my-app");
        assertThat(saved.getApiKeyHash()).isEqualTo("existing-hash");
        assertThat(saved.getCreateTime()).isEqualTo(existing.getCreateTime());
        assertThat(saved.getAppName()).isEqualTo("Updated App");
        assertThat(saved.getDescription()).isEqualTo("Updated description");
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("updateApiKey throws ConfigNotFoundException when not found")
    void updateApiKey_notFound() {
        UpdateApiKeyRequest request = new UpdateApiKeyRequest();
        request.setAppName("Updated App");
        request.setEnabled(true);

        when(apiKeyStorage.getApiKey("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateApiKey("unknown", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");

        verify(apiKeyStorage, never()).updateApiKey(any(ApiKey.class));
    }

    // ==================== regenerateApiKey ====================

    @Test
    @DisplayName("regenerateApiKey regenerates successfully")
    void regenerateApiKey_success() {
        when(apiKeyStorage.exists("my-app")).thenReturn(true);
        when(apiKeyStorage.regenerateApiKey("my-app")).thenReturn("new-plain-key");

        RegenerateApiKeyResponse response = service.regenerateApiKey("my-app");

        assertThat(response).isNotNull();
        assertThat(response.getAppId()).isEqualTo("my-app");
        assertThat(response.getApiKey()).isEqualTo("new-plain-key");
    }

    @Test
    @DisplayName("regenerateApiKey throws ConfigNotFoundException when not found")
    void regenerateApiKey_notFound() {
        when(apiKeyStorage.exists("unknown")).thenReturn(false);

        assertThatThrownBy(() -> service.regenerateApiKey("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");

        verify(apiKeyStorage, never()).regenerateApiKey(anyString());
    }

    // ==================== boundary cases ====================

    @Test
    @DisplayName("deleteApiKey with blank appId throws ConfigNotFoundException")
    void deleteApiKey_blankAppId() {
        when(apiKeyStorage.exists("")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteApiKey(""))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getApiKey with blank appId throws ConfigNotFoundException")
    void getApiKey_blankAppId() {
        when(apiKeyStorage.getApiKey("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApiKey(""))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
