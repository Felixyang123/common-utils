package com.lezai.threadpool;

import com.lezai.threadpool.client.RemoteConfigSourceDetector;
import com.lezai.threadpool.manager.ThreadPoolManager;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RemoteConfigSourceDetector wire contract")
class RemoteConfigSourceDetectorTest {

    private MockWebServer server;
    private ThreadPoolManager manager;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        manager = new ThreadPoolManager();
    }

    @AfterEach
    void tearDown() throws Exception {
        manager.shutdownNow();
        server.shutdown();
    }

    private RemoteConfigSourceDetector detector(long pullInterval) {
        String baseUrl = server.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1); // strip trailing slash
        return new RemoteConfigSourceDetector(
                baseUrl, "test-app", "test-key",
                30000L, pullInterval, 1000L, 30000L, manager);
    }

    @Test
    @DisplayName("pullConfigs hits the singular /config/{appId}/pull endpoint with version param")
    void pullHitsCorrectEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"code\":0,\"message\":\"success\",\"data\":null}"));
        // extra response for the subscription thread
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"code\":0,\"message\":\"success\",\"data\":null}"));

        RemoteConfigSourceDetector d = detector(0);
        d.start();

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().startsWith("/open/api/thread-pool/config/test-app/pull"),
                "pull must hit singular /config/.../pull, was: " + req.getPath());
        assertTrue(req.getPath().contains("version="), "pull should send version param");
        assertEquals("test-key", req.getHeader("X-API-Key"));

        d.stop();
    }

    @Test
    @DisplayName("pull parses ThreadPoolAppConfig-shaped server response into pools")
    void pullParsesServerResponse() throws Exception {
        String body = "{\"code\":0,\"message\":\"success\",\"data\":{"
                + "\"appId\":\"test-app\",\"configVersion\":5,\"configs\":["
                + "{\"poolName\":\"order-pool\",\"corePoolSize\":2,\"maximumPoolSize\":4,"
                + "\"keepAliveTime\":60,\"timeUnit\":\"SECONDS\",\"queueType\":\"BLOCKING_QUEUE\","
                + "\"queueCapacity\":100,\"rejectPolicyType\":\"ABORT\"}]}}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(body));
        // extra response for the subscription thread
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"code\":0,\"message\":\"success\",\"data\":null}"));

        RemoteConfigSourceDetector d = detector(0);
        d.start();
        server.takeRequest();

        Thread.sleep(200);
        assertNotNull(manager.getPool("order-pool"),
                "pull should create order-pool from server config (DTO contract)");

        d.stop();
    }
}