package com.lezai.threadpool.client.router;

import com.lezai.threadpool.client.ConfigServerClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultHealthCheckerTest {

    private ServerNode upNode(MockWebServer server) {
        String baseUrl = server.url("/").toString().replaceAll("/$", "");
        ConfigServerClient client = new ConfigServerClient(baseUrl, "app", "key", 1000);
        return new ServerNode(baseUrl, baseUrl, 1, client, new CircuitBreaker(3, 30000));
    }

    @Test
    void pushesStatusChangeWhenHealthChanges() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"status\":\"UP\",\"db\":\"UP\",\"redis\":\"N/A\",\"timestamp\":\"now\"}"));
            server.start();
            ServerNode node = upNode(server);

            CopyOnWriteArrayList<NodeHealthStatus> observed = new CopyOnWriteArrayList<>();
            DefaultHealthChecker checker = new DefaultHealthChecker(
                    List.of(node), (n, status) -> observed.add(status), 100L, 50L);

            checker.start();
            Thread.sleep(300);
            checker.stop();

            assertThat(observed).contains(NodeHealthStatus.UP);
        }
    }

    @Test
    void marksDownOnFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"status\":\"DOWN\",\"db\":\"DOWN\",\"redis\":\"N/A\",\"timestamp\":\"now\"}"));
            server.start();
            ServerNode node = upNode(server);

            CopyOnWriteArrayList<NodeHealthStatus> observed = new CopyOnWriteArrayList<>();
            DefaultHealthChecker checker = new DefaultHealthChecker(
                    List.of(node), (n, status) -> observed.add(status), 100L, 50L);

            checker.start();
            Thread.sleep(400);
            checker.stop();

            assertThat(observed).contains(NodeHealthStatus.DOWN);
        }
    }
}
