package com.lezai.threadpool;

import com.lezai.threadpool.client.RemoteConfigSourceDetector;
import com.lezai.threadpool.manager.ThreadPoolManager;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
        // 启动 pull 不带 version——总是拉最新全量配置,本地 updatePools guard 去重
        assertFalse(req.getPath().contains("version="),
                "startup pull should NOT include version param — always fetch latest");
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

    @Test
    @DisplayName("subscribe response carries only {appId, version}, not the full config — detector pulls full config separately")
    void subscribeReceivesLightweightNotification_thenPullsFullConfig() throws Exception {
        // Fixed response queues race against the long-polling thread's immediate retry-on-304
        // behavior (no backoff on success) — it can consume a later queue slot before the
        // notification is even sent. Use a request-driven Dispatcher instead: serve the
        // notification exactly once on the first subscribe call, then a full config on the
        // first pull that follows it, decoupled from any assumed request ordering.
        String fullConfigBody = "{\"code\":0,\"message\":\"success\",\"data\":{"
                + "\"appId\":\"test-app\",\"configVersion\":7,\"configs\":["
                + "{\"poolName\":\"notify-pool\",\"corePoolSize\":1,\"maximumPoolSize\":2,"
                + "\"keepAliveTime\":60,\"timeUnit\":\"SECONDS\",\"queueType\":\"BLOCKING_QUEUE\","
                + "\"queueCapacity\":50,\"rejectPolicyType\":\"ABORT\"}]}}";

        AtomicReference<RecordedRequest> followupPullRequest = new AtomicReference<>();
        CountDownLatch followupPullSeen = new CountDownLatch(1);
        AtomicReference<Boolean> notificationSent = new AtomicReference<>(false);

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.startsWith("/open/api/thread-pool/configs/test-app/subscribe")) {
                    if (notificationSent.compareAndSet(false, true)) {
                        return new MockResponse().setResponseCode(200)
                                .setBody("{\"code\":0,\"message\":\"success\",\"data\":{\"appId\":\"test-app\",\"version\":7}}");
                    }
                    return new MockResponse().setResponseCode(304);
                }
                if (path.startsWith("/open/api/thread-pool/config/test-app/pull")) {
                    if (notificationSent.get() && !path.contains("version=")
                            && followupPullRequest.compareAndSet(null, request)) {
                        followupPullSeen.countDown();
                        return new MockResponse().setResponseCode(200).setBody(fullConfigBody);
                    }
                    // startup pull (before notification) — nothing yet
                    return new MockResponse().setResponseCode(200)
                            .setBody("{\"code\":0,\"message\":\"success\",\"data\":null}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        RemoteConfigSourceDetector d = detector(0);
        d.start();

        assertTrue(followupPullSeen.await(5, TimeUnit.SECONDS),
                "detector should pull the full config after receiving the notification");
        assertFalse(followupPullRequest.get().getPath().contains("version="),
                "notification-triggered pull should NOT include version — always fetch latest to avoid dirty cache");

        Thread.sleep(200);
        assertNotNull(manager.getPool("notify-pool"),
                "notify-pool from the follow-up pull should now be registered");

        d.stop();
    }

    @Test
    @DisplayName("subscribe response of HTTP 304 is treated as no-change, not an error")
    void subscribe304_treatedAsNoChange() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"code\":0,\"message\":\"success\",\"data\":null}"));
        server.enqueue(new MockResponse().setResponseCode(304));
        // second long-poll iteration so we can safely stop() without a pending take
        server.enqueue(new MockResponse().setResponseCode(304));

        RemoteConfigSourceDetector d = detector(0);
        d.start();

        server.takeRequest(); // startup pull
        RecordedRequest subscribeReq = server.takeRequest(); // subscribe -> 304
        assertTrue(subscribeReq.getPath().startsWith("/open/api/thread-pool/configs/test-app/subscribe"));

        d.stop();
        // No exception should have propagated out of the subscription thread for a 304 response.
    }

    @Test
    @DisplayName("periodic short-polling pull includes the current version for 304 optimization")
    void periodicPull_includesVersionParam() throws Exception {
        // The long-polling subscribe loop retries immediately on 304 (no backoff on success),
        // so it can starve a fixed response queue before the every-50ms periodic-pull scheduler
        // gets a turn. Use a request-driven Dispatcher instead: respond based on path, and
        // capture the first periodic pull request (the one carrying ?version=) via a latch —
        // fully decoupled from response-consumption ordering between the two background threads.
        AtomicReference<RecordedRequest> periodicPullRequest = new AtomicReference<>();
        CountDownLatch periodicPullSeen = new CountDownLatch(1);

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.startsWith("/open/api/thread-pool/config/test-app/pull")) {
                    if (path.contains("version=") && periodicPullRequest.compareAndSet(null, request)) {
                        periodicPullSeen.countDown();
                    }
                    return path.contains("version=")
                            ? new MockResponse().setResponseCode(304)
                            : new MockResponse().setResponseCode(200)
                                    .setBody("{\"code\":0,\"message\":\"success\",\"data\":null}");
                }
                // subscribe (long-polling)
                return new MockResponse().setResponseCode(304);
            }
        });

        RemoteConfigSourceDetector d = new RemoteConfigSourceDetector(
                server.url("/").toString().replaceAll("/$", ""), "test-app", "test-key",
                30000L, 50L, 1000L, 30000L, manager);
        d.start();

        assertTrue(periodicPullSeen.await(5, TimeUnit.SECONDS), "should observe a periodic short-polling pull request");
        assertTrue(periodicPullRequest.get().getPath().contains("version=0"),
                "periodic short-polling pull should include version for server-side 304 optimization, was: "
                        + periodicPullRequest.get().getPath());

        d.stop();
    }
}