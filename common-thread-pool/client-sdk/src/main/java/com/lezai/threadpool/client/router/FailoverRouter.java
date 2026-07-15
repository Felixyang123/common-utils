package com.lezai.threadpool.client.router;

import com.lezai.threadpool.bean.AddConfigAppResult;
import com.lezai.threadpool.bean.ConfigChangeNotification;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.lezai.threadpool.bean.ThreadPoolConfigResp;
import com.lezai.threadpool.bean.ThreadPoolStatsReport;
import com.lezai.threadpool.client.ConfigOperations;
import com.lezai.threadpool.client.ConfigServerClient;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Slf4j
public class FailoverRouter implements ConfigOperations {

    private final List<ServerNode> nodes;
    private final RoutingAlgorithm routingAlgorithm;
    private final AtomicInteger roundRobin = new AtomicInteger();
    private volatile int failoverIndex = 0;
    private final Map<String, ConfigServerClient> clients = new HashMap<>();
    private final long healthCheckIntervalMs;
    private final long healthCheckFastIntervalMs;
    private final ScheduledExecutorService healthExecutor;
    private volatile boolean healthRunning;

    public FailoverRouter(List<ServerNode> nodes, RoutingAlgorithm routingAlgorithm,
                          Function<ServerNode, ConfigServerClient> clientFactory,
                          long healthCheckIntervalMs, long healthCheckFastIntervalMs) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be empty");
        }
        this.nodes = List.copyOf(nodes);
        this.routingAlgorithm = routingAlgorithm;
        this.healthCheckIntervalMs = healthCheckIntervalMs;
        this.healthCheckFastIntervalMs = healthCheckFastIntervalMs;
        for (ServerNode node : nodes) {
            clients.put(node.getBaseUrl(), clientFactory.apply(node));
        }
        this.healthExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "admin-server-health-check-thread");
            t.setDaemon(true);
            return t;
        });
    }

    // ─── ConfigOperations ───

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
        healthRunning = false;
        healthExecutor.shutdown();
        clients.values().forEach(ConfigServerClient::release);
    }

    // ─── Routing & failover ───

    public ServerNode selectNode(long nowMs) {
        List<ServerNode> candidates = candidates(nowMs);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No available admin-server node");
        }
        return switch (routingAlgorithm) {
            case WEIGHTED_ROUND_ROBIN -> weightedRoundRobin(candidates);
            case RANDOM -> candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            case FAILOVER -> failover(candidates);
            case ROUND_ROBIN -> candidates.get(Math.floorMod(roundRobin.getAndIncrement(), candidates.size()));
        };
    }

    private List<ServerNode> candidates(long nowMs) {
        return nodes.stream()
                .filter(node -> node.getHealthStatus() != NodeHealthStatus.DOWN)
                .filter(node -> node.getCircuitBreaker().allowRequest(nowMs))
                .toList();
    }

    private ServerNode weightedRoundRobin(List<ServerNode> candidates) {
        List<ServerNode> weighted = new ArrayList<>();
        for (ServerNode node : candidates) {
            for (int i = 0; i < node.getWeight(); i++) {
                weighted.add(node);
            }
        }
        return weighted.get(Math.floorMod(roundRobin.getAndIncrement(), weighted.size()));
    }

    private ServerNode failover(List<ServerNode> candidates) {
        ServerNode current = nodes.get(failoverIndex);
        if (candidates.contains(current)) return current;
        ServerNode next = candidates.get(0);
        failoverIndex = nodes.indexOf(next);
        return next;
    }

    public boolean isDegraded() {
        return nodes.stream().anyMatch(node -> node.getHealthStatus() == NodeHealthStatus.DEGRADED);
    }

    // ─── Failover execution ───

    private <T> T execute(IoCall<T> call) throws IOException {
        IOException last = null;
        int attempts = 0;
        while (attempts < nodes.size()) {
            ServerNode node = selectNode(System.currentTimeMillis());
            ConfigServerClient client = clients.get(node.getBaseUrl());
            try {
                T result = call.apply(client);
                node.getCircuitBreaker().recordSuccess();
                return result;
            } catch (ConfigServerClient.HttpStatusException e) {
                if (e.getStatusCode() >= 500) {
                    node.getCircuitBreaker().recordFailure(System.currentTimeMillis());
                    last = e;
                    attempts++;
                    continue;
                }
                throw e;
            } catch (IOException e) {
                node.getCircuitBreaker().recordFailure(System.currentTimeMillis());
                last = e;
                attempts++;
            }
        }
        throw last != null ? last : new IOException("No available admin-server node");
    }

    @FunctionalInterface
    private interface IoCall<T> {
        T apply(ConfigServerClient client) throws IOException;
    }

    // ─── Health check ───

    public void startHealthCheck() {
        if (healthRunning) return;
        healthRunning = true;
        scheduleHealthCheck(0);
    }

    private void scheduleHealthCheck(long delayMs) {
        if (healthRunning) {
            healthExecutor.schedule(this::refreshHealthAndReschedule, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        }
    }

    private void refreshHealthAndReschedule() {
        try {
            refreshHealthOnce();
        } finally {
            boolean allDown = nodes.stream().allMatch(node -> node.getHealthStatus() == NodeHealthStatus.DOWN);
            scheduleHealthCheck(allDown ? healthCheckFastIntervalMs : healthCheckIntervalMs);
        }
    }

    public void refreshHealthOnce() {
        for (ServerNode node : nodes) {
            ConfigServerClient client = clients.get(node.getBaseUrl());
            try {
                HealthResponse response = client.health();
                node.markHealth(toNodeHealth(response != null ? response.getStatus() : null));
            } catch (Exception e) {
                node.markHealth(NodeHealthStatus.DOWN);
            }
        }
    }

    private NodeHealthStatus toNodeHealth(String status) {
        if ("UP".equalsIgnoreCase(status)) return NodeHealthStatus.UP;
        if ("DEGRADED".equalsIgnoreCase(status)) return NodeHealthStatus.DEGRADED;
        if ("DOWN".equalsIgnoreCase(status)) return NodeHealthStatus.DOWN;
        return NodeHealthStatus.UNKNOWN;
    }
}