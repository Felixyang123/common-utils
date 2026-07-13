package com.lezai.threadpool.controller;

import com.alibaba.fastjson2.JSON;
import com.lezai.threadpool.pojo.bean.PageResult;
import com.lezai.threadpool.exception.ResourceAlreadyExistsException;
import com.lezai.threadpool.exception.ResourceNotFoundException;
import com.lezai.threadpool.exception.GlobalExceptionHandler;
import com.lezai.threadpool.pojo.request.CreateApiKeyRequest;
import com.lezai.threadpool.pojo.request.UpdateApiKeyRequest;
import com.lezai.threadpool.pojo.response.ApiKeyInfoResponse;
import com.lezai.threadpool.pojo.response.CreateApiKeyResponse;
import com.lezai.threadpool.pojo.response.RegenerateApiKeyResponse;
import com.lezai.threadpool.service.ApiKeyAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    @Mock
    private ApiKeyAdminService apiKeyAdminService;

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
        CreateApiKeyResponse serviceResponse = new CreateApiKeyResponse();
        serviceResponse.setAppId("my-app");
        serviceResponse.setApiKey("plain-key-123");
        serviceResponse.setAppName("My App");
        serviceResponse.setEnabled(true);
        serviceResponse.setCreateTime(LocalDateTime.now());

        when(apiKeyAdminService.createApiKey(any(CreateApiKeyRequest.class))).thenReturn(serviceResponse);

        var request = new CreateApiKeyRequest();
        request.setAppId("my-app");
        request.setAppName("My App");

        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appId").value("my-app"))
                .andExpect(jsonPath("$.data.apiKey").value("plain-key-123"));
    }

    @Test
    @DisplayName("POST /api/api-keys returns 409 when appId already exists")
    void createApiKey_alreadyExists() throws Exception {
        when(apiKeyAdminService.createApiKey(any(CreateApiKeyRequest.class)))
                .thenThrow(new ResourceAlreadyExistsException("API key already exists for appId: my-app"));

        var request = new CreateApiKeyRequest();
        request.setAppId("my-app");
        request.setAppName("My App");

        mockMvc.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/api-keys/{appId} returns API key info")
    void getApiKey_success() throws Exception {
        ApiKeyInfoResponse info = new ApiKeyInfoResponse();
        info.setAppId("my-app");
        info.setAppName("My App");
        info.setEnabled(true);
        info.setCreateTime(LocalDateTime.now());

        when(apiKeyAdminService.getApiKey("my-app")).thenReturn(info);

        mockMvc.perform(get("/api/api-keys/my-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appId").value("my-app"));
    }

    @Test
    @DisplayName("GET /api/api-keys/{appId} returns 404 when not found")
    void getApiKey_notFound() throws Exception {
        when(apiKeyAdminService.getApiKey("unknown"))
                .thenThrow(new ResourceNotFoundException("API key not found for appId: unknown"));

        mockMvc.perform(get("/api/api-keys/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/api-keys/{appId} deletes API key")
    void deleteApiKey_success() throws Exception {
        mockMvc.perform(delete("/api/api-keys/my-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(apiKeyAdminService).deleteApiKey("my-app");
    }

    @Test
    @DisplayName("DELETE /api/api-keys/{appId} returns 404 when not found")
    void deleteApiKey_notFound() throws Exception {
        doThrow(new ResourceNotFoundException("API key not found for appId: unknown"))
                .when(apiKeyAdminService).deleteApiKey("unknown");

        mockMvc.perform(delete("/api/api-keys/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/api-keys lists all API keys with pagination")
    void pageApiKeys() throws Exception {
        ApiKeyInfoResponse key1 = new ApiKeyInfoResponse();
        key1.setAppId("app1");
        key1.setAppName("App 1");
        ApiKeyInfoResponse key2 = new ApiKeyInfoResponse();
        key2.setAppId("app2");
        key2.setAppName("App 2");

        when(apiKeyAdminService.pageApiKeys(1, 20)).thenReturn(PageResult.of(2, List.of(key1, key2)));

        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list.length()").value(2));
    }

    @Test
    @DisplayName("PUT /api/api-keys/{appId} updates API key")
    void updateApiKey_success() throws Exception {
        var request = new UpdateApiKeyRequest();
        request.setAppName("Updated App");
        request.setEnabled(true);

        mockMvc.perform(put("/api/api-keys/my-app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(apiKeyAdminService).updateApiKey(eq("my-app"), any(UpdateApiKeyRequest.class));
    }

    @Test
    @DisplayName("PUT /api/api-keys/{appId} returns 404 when not found")
    void updateApiKey_notFound() throws Exception {
        doThrow(new ResourceNotFoundException("API key not found for appId: unknown"))
                .when(apiKeyAdminService).updateApiKey(eq("unknown"), any(UpdateApiKeyRequest.class));

        var request = new UpdateApiKeyRequest();
        request.setEnabled(true);

        mockMvc.perform(put("/api/api-keys/unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/api-keys/{appId}/regenerate regenerates API key")
    void regenerateApiKey_success() throws Exception {
        RegenerateApiKeyResponse serviceResponse = new RegenerateApiKeyResponse();
        serviceResponse.setAppId("my-app");
        serviceResponse.setApiKey("new-plain-key");

        when(apiKeyAdminService.regenerateApiKey("my-app")).thenReturn(serviceResponse);

        mockMvc.perform(post("/api/api-keys/my-app/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.apiKey").value("new-plain-key"));
    }

    @Test
    @DisplayName("POST /api/api-keys/{appId}/regenerate returns 404 when not found")
    void regenerateApiKey_notFound() throws Exception {
        when(apiKeyAdminService.regenerateApiKey("unknown"))
                .thenThrow(new ResourceNotFoundException("API key not found for appId: unknown"));

        mockMvc.perform(post("/api/api-keys/unknown/regenerate"))
                .andExpect(status().isNotFound());
    }
}




