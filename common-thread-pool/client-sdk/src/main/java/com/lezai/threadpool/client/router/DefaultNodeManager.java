package com.lezai.threadpool.client.router;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DefaultNodeManager implements NodeManager {

    private final List<ServerNode> nodes;
    private volatile List<ServerNode> cachedCandidates;

    public DefaultNodeManager(List<ServerNode> nodes) {
        this.nodes = new ArrayList<>(nodes);
        this.cachedCandidates = nodes.stream().filter(DefaultNodeManager::isAvailable).toList();
    }

    private static boolean isAvailable(ServerNode n) {
        return n.getHealthStatus() != NodeHealthStatus.DOWN
                && n.getCircuitBreaker().getState() != CircuitBreakerState.OPEN;
    }

    @Override
    public List<ServerNode> getAllNodes() {
        return nodes;
    }

    @Override
    public List<ServerNode> getCandidates() {
        return cachedCandidates;
    }

    @Override
    public void onStatusChanged(ServerNode node, NodeHealthStatus newStatus) {
        log.info("Node {} health changed to {}", node.getBaseUrl(), newStatus);
        node.markHealth(newStatus);
        rebuildCandidates();
    }

    @Override
    public void onBreakerStateChanged(ServerNode node, CircuitBreakerState oldState, CircuitBreakerState newState) {
        log.info("Node {} breaker changed from {} to {}", node.getBaseUrl(), oldState, newState);
        rebuildCandidates();
    }

    private void rebuildCandidates() {
        cachedCandidates = nodes.stream().filter(DefaultNodeManager::isAvailable).toList();
    }

    @Override
    public boolean isDegraded() {
        return nodes.stream().anyMatch(n -> n.getHealthStatus() == NodeHealthStatus.DEGRADED);
    }

    @Override
    public int nodeCount() {
        return nodes.size();
    }

    @Override
    public void release() {
        nodes.forEach(ServerNode::release);
    }
}
