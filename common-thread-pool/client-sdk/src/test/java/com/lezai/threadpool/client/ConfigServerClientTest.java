package com.lezai.threadpool.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigServerClientTest {

    @Test
    @DisplayName("pullConfigs uses plural configs path")
    void pullConfigsUsesPluralPath() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"code\":0,\"message\":\"success\",\"data\":{\"configVersion\":1,\"configs\":[]}}"));
            server.start();
            String baseUrl = server.url("/").toString().replaceAll("/$", "");
            ConfigServerClient client = new ConfigServerClient(baseUrl, "app1", "key", 1000);

            client.pullConfigs(1L);

            RecordedRequest request = server.takeRequest();
            assertThat(request.getPath()).isEqualTo("/open/api/thread-pool/configs/app1/pull?version=1");
            client.release();
        }
    }
}
