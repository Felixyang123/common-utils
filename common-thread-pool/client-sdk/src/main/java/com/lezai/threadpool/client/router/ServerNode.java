package com.lezai.threadpool.client.router;

import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.client.ConfigOperations;
import com.lezai.threadpool.client.ConfigServerClient;
import lombok.Getter;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;

@Getter
public class ServerNode implements ConfigOperations {

    private final String name;
    private final String baseUrl;
    private final int weight;
    private final ConfigServerClient client;
    private final CircuitBreaker circuitBreaker;
    private volatile NodeHealthStatus healthStatus = NodeHealthStatus.UNKNOWN;
    private BiConsumer<CircuitBreakerState, CircuitBreakerState> breakerCallback;

    public ServerNode(String name, String baseUrl, int weight,
                      ConfigServerClient client, CircuitBreaker circuitBreaker) {
        this.name = name;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.weight = Math.max(1, weight);
        this.client = client;
        this.circuitBreaker = circuitBreaker;
        wireBreakerCallback();
    }

    /**
     * Backward-compatible constructor for existing parser tests and simple use cases.
     * Creates a self-contained node with its own ConfigServerClient and default breaker.
     */
    public ServerNode(String baseUrl, int weight) {
        this(baseUrl, baseUrl, weight,
                new ConfigServerClient(baseUrl, "default-app", "default-key", 35000),
                new CircuitBreaker(3, 30000));
    }

    public void setBreakerCallback(BiConsumer<CircuitBreakerState, CircuitBreakerState> callback) {
        this.breakerCallback = callback;
    }

    private void wireBreakerCallback() {
        circuitBreaker.addObserver((oldState, newState) -> {
            if (breakerCallback != null) {
                breakerCallback.accept(oldState, newState);
            }
        });
    }

    @Override
    public ConfigChangeNotification subscribe(long version, long timeoutMs) throws IOException {
        return execute(client -> client.subscribe(version, timeoutMs));
    }

    @Override
    public ThreadPoolConfigResp pullConfigs(Long version) throws IOException {
        return execute(client -> client.pullConfigs(version));
    }

    @Override
    public AddConfigAppResult registerConfig(ThreadPoolConfig config) throws IOException {
        return execute(client -> client.registerConfig(config));
    }

    @Override
    public AddConfigAppResult registerConfigs(List<ThreadPoolConfig> configs) throws IOException {
        return execute(client -> client.registerConfigs(configs));
    }

    @Override
    public void reportStats(ThreadPoolStatsReport report) throws IOException {
        execute(client -> {
            client.reportStats(report);
            return null;
        });
    }

    @Override
    public void release() {
        client.release();
    }

    public void markHealth(NodeHealthStatus healthStatus) {
        this.healthStatus = healthStatus;
    }

    /**
     * Health check bypasses the circuit breaker — the recovery path must never be
     * blocked by the breaker itself.
     */
    public HealthResponse checkHealth() {
        try {
            return client.health();
        } catch (IOException e) {
            return null;
        }
    }

    public boolean available(long nowMs) {
        return healthStatus != NodeHealthStatus.DOWN && circuitBreaker.allowRequest(nowMs);
    }

    private <T> T execute(IoCall<T> call) throws IOException {
        long now = System.currentTimeMillis();
        if (!circuitBreaker.allowRequest(now)) {
            throw new NodeUnavailableException("Circuit breaker is OPEN for " + baseUrl);
        }
        try {
            T result = call.apply(client);
            circuitBreaker.recordSuccess();
            return result;
        } catch (IOException e) {
            circuitBreaker.recordFailure(now);
            throw e;
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) return null;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    @FunctionalInterface
    private interface IoCall<T> {
        T apply(ConfigServerClient client) throws IOException;
    }
}
