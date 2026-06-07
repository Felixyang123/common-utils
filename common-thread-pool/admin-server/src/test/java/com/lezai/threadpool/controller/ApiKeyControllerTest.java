package com.lezai.threadpool.controller;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.bean.ApiKey;
import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.ChangeLogEntry;
import com.lezai.threadpool.enums.ChangeType;
import com.lezai.threadpool.exception.ConfigAlreadyExistsException;
import com.lezai.threadpool.exception.ConfigNotFoundException;
import com.lezai.threadpool.exception.GlobalExceptionHandler;
import com.lezai.threadpool.pojo.cmd.CreateApiKeyCmd;
import com.lezai.threadpool.pojo.cmd.UpdateApiKeyCmd;
import com.lezai.threadpool.storage.ApiKeyHistoryStorage;
import com.lezai.threadpool.storage.ApiKeyStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    @Mock
    private ApiKeyStorage apiKeyStorage;

    @Mock
    private ApiKeyHistoryStorage historyStorage;

    @InjectMocks
    private ApiKeyController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/api-keys creates API key successfully")
    void createApiKey_success() throws Exception {
        when(apiKeyStorage.exists("my-app")).thenReturn(false);

        var request = new CreateApiKeyCmd();
        request.setAppId("my-app");
        request.setAppName("My App");

        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appId").value("my-app"))
                .andExpect(jsonPath("$.data.apiKey").isNotEmpty());

        verify(apiKeyStorage).saveApiKey(any(ApiKey.class));
    }

    @Test
    @DisplayName("POST /api/api-keys returns 409 when appId already exists")
    void createApiKey_alreadyExists() throws Exception {
        when(apiKeyStorage.exists("my-app")).thenReturn(true);

        var request = new CreateApiKeyCmd();
        request.setAppId("my-app");
        request.setAppName("My App");

        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("GET /api/api-keys/{appId} returns API key info")
    void getApiKey_success() throws Exception {
        ApiKey key = ApiKey.builder()
                .appId("my-app")
                .appName("My App")
                .enabled(true)
                .expireTime(LocalDateTime.now().plusDays(30))
                .createTime(LocalDateTime.now())
                .build();

        when(apiKeyStorage.getApiKey("my-app")).thenReturn(Optional.of(key));

        mockMvc.perform(get("/api/api-keys/my-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appId").value("my-app"));
    }

    @Test
    @DisplayName("GET /api/api-keys/{appId} returns 404 when not found")
    void getApiKey_notFound() throws Exception {
        when(apiKeyStorage.getApiKey("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/api-keys/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("DELETE /api/api-keys/{appId} deletes API key")
    void deleteApiKey_success() throws Exception {
        when(apiKeyStorage.exists("my-app")).thenReturn(true);

        mockMvc.perform(delete("/api/api-keys/my-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(apiKeyStorage).deleteApiKey("my-app");
    }

    @Test
    @DisplayName("DELETE /api/api-keys/{appId} returns 404 when not found")
    void deleteApiKey_notFound() throws Exception {
        when(apiKeyStorage.exists("unknown")).thenReturn(false);

        mockMvc.perform(delete("/api/api-keys/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("GET /api/api-keys lists all API keys")
    void listAllApiKeys() throws Exception {
        ApiKey key1 = ApiKey.builder().appId("app1").appName("App 1").enabled(true).build();
        ApiKey key2 = ApiKey.builder().appId("app2").appName("App 2").enabled(true).build();
        when(apiKeyStorage.listAllApiKeys()).thenReturn(List.of(key1, key2));

        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("PUT /api/api-keys/{appId} updates API key")
    void updateApiKey_success() throws Exception {
        when(apiKeyStorage.exists("my-app")).thenReturn(true);

        var request = new UpdateApiKeyCmd();
        request.setAppName("Updated App");
        request.setEnabled(true);

        mockMvc.perform(put("/api/api-keys/my-app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(apiKeyStorage).saveApiKey(any(ApiKey.class));
    }

    @Test
    @DisplayName("PUT /api/api-keys/{appId} returns 404 when not found")
    void updateApiKey_notFound() throws Exception {
        when(apiKeyStorage.exists("unknown")).thenReturn(false);

        var request = new UpdateApiKeyCmd();
        request.setEnabled(true);

        mockMvc.perform(put("/api/api-keys/unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("POST /api/api-keys/{appId}/regenerate regenerates API key")
    void regenerateApiKey_success() throws Exception {
        when(apiKeyStorage.exists("my-app")).thenReturn(true);
        when(apiKeyStorage.regenerateApiKey("my-app")).thenReturn("new-plain-key");

        mockMvc.perform(post("/api/api-keys/my-app/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.apiKey").value("new-plain-key"));
    }

    @Test
    @DisplayName("POST /api/api-keys/{appId}/regenerate returns 404 when not found")
    void regenerateApiKey_notFound() throws Exception {
        when(apiKeyStorage.exists("unknown")).thenReturn(false);

        mockMvc.perform(post("/api/api-keys/unknown/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("GET /api/api-keys/{appId}/history returns history")
    void getApiKeyHistory() throws Exception {
        when(apiKeyStorage.exists("my-app")).thenReturn(true);

        ChangeLogEntry<ApiKey> entry = ChangeLogEntry.of(1, ChangeType.CREATE, null,
                ApiKey.builder().appId("my-app").build());
        when(historyStorage.getHistory("my-app")).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/api-keys/my-app/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/api-keys/{appId}/history with limit parameter")
    void getApiKeyHistory_withLimit() throws Exception {
        when(apiKeyStorage.exists("my-app")).thenReturn(true);

        ChangeLogEntry<ApiKey> entry = ChangeLogEntry.of(1, ChangeType.CREATE, null,
                ApiKey.builder().appId("my-app").build());
        when(historyStorage.getHistory("my-app", 5)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/api-keys/my-app/history")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }
}
