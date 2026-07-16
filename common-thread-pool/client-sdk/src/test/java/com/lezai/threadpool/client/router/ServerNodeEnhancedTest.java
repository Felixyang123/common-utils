package com.lezai.threadpool.client.router;

import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.client.ConfigServerClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerNodeEnhancedTest {

    @Test
    void delegatesCallWhenBreakerClosed() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"code\":0,\"message\":\"success\",\"data\":{\"configVersion\":1,\"configs\":[]}}"));
            server.start();
            String baseUrl = server.url("/").toString().replaceAll("/$", "");
            ConfigServerClient client = new ConfigServerClient(baseUrl, "app1", "key", 1000);
            CircuitBreaker breaker = new CircuitBreaker(3, 30000);
            ServerNode node = new ServerNode("test-node", baseUrl, 1, client, breaker);

            ThreadPoolConfigResp resp = node.pullConfigs(1L);
            assertThat(resp.getConfigVersion()).isEqualTo(1);
            node.release();
        }
    }

    @Test
    void blocksCallWhenBreakerOpen() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String baseUrl = server.url("/").toString().replaceAll("/$", "");
            ConfigServerClient client = new ConfigServerClient(baseUrl, "app1", "key", 1000);
            CircuitBreaker breaker = new CircuitBreaker(1, 30000);
            ServerNode node = new ServerNode("test-node", baseUrl, 1, client, breaker);
            breaker.recordFailure(0L);

            assertThatThrownBy(() -> node.pullConfigs(1L))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Circuit breaker is OPEN");
            node.release();
        }
    }

    @Test
    void recordsFailureOnIOException() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String baseUrl = server.url("/").toString().replaceAll("/$", "");
            ConfigServerClient client = new ConfigServerClient(baseUrl, "app1", "key", 1000);
            CircuitBreaker breaker = new CircuitBreaker(1, 30000);
            ServerNode node = new ServerNode("test-node", baseUrl, 1, client, breaker);

            assertThatThrownBy(() -> node.pullConfigs(1L)).isInstanceOf(IOException.class);
            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
            node.release();
        }
    }

    @Test
    void checkHealthBypassesBreaker() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"status\":\"UP\",\"db\":\"UP\",\"redis\":\"N/A\",\"timestamp\":\"now\"}"));
            server.start();
            String baseUrl = server.url("/").toString().replaceAll("/$", "");
            ConfigServerClient client = new ConfigServerClient(baseUrl, "app1", "key", 1000);
            CircuitBreaker breaker = new CircuitBreaker(1, 30000);
            ServerNode node = new ServerNode("test-node", baseUrl, 1, client, breaker);
            breaker.recordFailure(0L);

            // Even though breaker is OPEN, health check still works
            HealthResponse health = node.checkHealth();
            assertThat(health.getStatus()).isEqualTo("UP");
            node.release();
        }
    }

    @Test
    void breakerCallbackFiresOnStateChange() throws Exception {
        ConfigServerClient client = new ConfigServerClient("http://n1:8080", "app1", "key", 1000);
        CircuitBreaker breaker = new CircuitBreaker(1, 30000);
        ServerNode node = new ServerNode("test-node", "http://n1:8080", 1, client, breaker);

        AtomicReference<CircuitBreakerState> observed = new AtomicReference<>();
        node.setBreakerObserver((n, old, newVal) -> observed.set(newVal));

        breaker.recordFailure(0L);
        assertThat(observed.get()).isEqualTo(CircuitBreakerState.OPEN);
        node.release();
    }
}
