package com.lezai.threadpool.client.router;

import com.lezai.threadpool.utils.LogEvents;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DefaultHealthChecker implements HealthChecker {

    private final List<ServerNode> nodes;
    private final HealthStateObserver observer;
    private final long normalIntervalMs;
    private final long fastIntervalMs;
    private final ScheduledExecutorService executor;
    private volatile boolean running;

    public DefaultHealthChecker(List<ServerNode> nodes, HealthStateObserver observer,
                                long normalIntervalMs, long fastIntervalMs) {
        this.nodes = nodes;
        this.observer = observer;
        this.normalIntervalMs = normalIntervalMs;
        this.fastIntervalMs = fastIntervalMs;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "admin-server-health-check-thread");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        scheduleNext(0);
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdown();
    }

    private void scheduleNext(long delayMs) {
        if (running) {
            executor.schedule(this::probeAndReschedule, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        }
    }

    private void probeAndReschedule() {
        try {
            boolean allDown = true;
            for (ServerNode node : nodes) {
                NodeHealthStatus newStatus = probeNode(node);
                if (newStatus != NodeHealthStatus.DOWN) {
                    allDown = false;
                }
                if (newStatus != node.getHealthStatus()) {
                    LogEvents.nodeHealthChanged(node.getBaseUrl(), node.getHealthStatus().name(), newStatus.name());
                    observer.onStatusChanged(node, newStatus);
                }
            }
            scheduleNext(allDown ? fastIntervalMs : normalIntervalMs);
        } catch (Exception e) {
            log.error("Health check probe failed", e);
            scheduleNext(normalIntervalMs);
        }
    }

    private NodeHealthStatus probeNode(ServerNode node) {
        try {
            HealthResponse response = node.checkHealth();
            if (response == null || response.getStatus() == null) {
                return NodeHealthStatus.DOWN;
            }
            return HealthStatusMapper.fromResponse(response.getStatus());
        } catch (Exception e) {
            log.warn("Health probe failed for node {}", node.getBaseUrl(), e);
            return NodeHealthStatus.DOWN;
        }
    }
}
