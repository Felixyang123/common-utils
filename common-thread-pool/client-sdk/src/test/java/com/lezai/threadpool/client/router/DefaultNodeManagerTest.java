package com.lezai.threadpool.client.router;

import com.lezai.threadpool.client.ConfigServerClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultNodeManagerTest {

    private ServerNode node(String url) {
        ConfigServerClient client = new ConfigServerClient(url, "app", "key", 1000);
        CircuitBreaker breaker = new CircuitBreaker(1, 30000);
        return new ServerNode(url, url, 1, client, breaker);
    }

    @Test
    void getCandidates_excludesDownAndOpen() {
        ServerNode n1 = node("http://n1:8080");
        ServerNode n2 = node("http://n2:8080");
        ServerNode n3 = node("http://n3:8080");
        n1.markHealth(NodeHealthStatus.UP);
        n2.markHealth(NodeHealthStatus.UP);
        n3.markHealth(NodeHealthStatus.UP);
        n3.getCircuitBreaker().recordFailure(0L);  // n3 breaker OPEN

        DefaultNodeManager manager = new DefaultNodeManager(List.of(n1, n2, n3));
        List<ServerNode> candidates = manager.getCandidates();
        assertThat(candidates).containsExactly(n1, n2);
    }

    @Test
    void onStatusChanged_removesDownNode() {
        ServerNode n1 = node("http://n1:8080");
        ServerNode n2 = node("http://n2:8080");
        n1.markHealth(NodeHealthStatus.UP);
        n2.markHealth(NodeHealthStatus.UP);

        DefaultNodeManager manager = new DefaultNodeManager(List.of(n1, n2));
        assertThat(manager.getCandidates()).hasSize(2);

        manager.onStatusChanged(n1, NodeHealthStatus.DOWN);
        assertThat(manager.getCandidates()).containsExactly(n2);
    }

    @Test
    void onStatusChanged_addsRecoveredNode() {
        ServerNode n1 = node("http://n1:8080");
        ServerNode n2 = node("http://n2:8080");
        n1.markHealth(NodeHealthStatus.DOWN);
        n2.markHealth(NodeHealthStatus.UP);

        DefaultNodeManager manager = new DefaultNodeManager(List.of(n1, n2));
        assertThat(manager.getCandidates()).containsExactly(n2);

        // Simulate health check detecting recovery: markHealth + callback
        manager.onStatusChanged(n1, NodeHealthStatus.UP);
        // rebuild iterates original node order [n1, n2], both now available
        assertThat(manager.getCandidates()).containsExactly(n1, n2);
    }

    @Test
    void breakerTripping_removesNodeViaRealObserverChain() {
        // Use the REAL flow: trip the breaker via recordFailure → breaker fires
        // its observer → ServerNode forwards to NodeManager callback → candidates rebuild.
        ServerNode n1 = node("http://n1:8080");
        ServerNode n2 = node("http://n2:8080");
        n1.markHealth(NodeHealthStatus.UP);
        n2.markHealth(NodeHealthStatus.UP);

        DefaultNodeManager manager = new DefaultNodeManager(List.of(n1, n2));
        assertThat(manager.getCandidates()).hasSize(2);

        // Wire each node's breaker callback to the manager (done in composition root)
        n1.setBreakerObserver(manager::onBreakerStateChanged);
        n2.setBreakerObserver(manager::onBreakerStateChanged);

        // Trip n1's breaker for real → observer fires → manager rebuilds
        n1.getCircuitBreaker().recordFailure(0L);
        assertThat(manager.getCandidates()).containsExactly(n2);
    }

    @Test
    void breakerRecovery_addsNodeBackViaRealObserverChain() {
        ServerNode n1 = node("http://n1:8080");
        ServerNode n2 = node("http://n2:8080");
        n1.markHealth(NodeHealthStatus.UP);
        n2.markHealth(NodeHealthStatus.UP);

        DefaultNodeManager manager = new DefaultNodeManager(List.of(n1, n2));
        n1.setBreakerObserver(manager::onBreakerStateChanged);
        n2.setBreakerObserver(manager::onBreakerStateChanged);

        // Trip n1 → removed
        n1.getCircuitBreaker().recordFailure(0L);
        assertThat(manager.getCandidates()).containsExactly(n2);

        // Force HALF_OPEN (simulating cooldown expiry) → added back
        n1.getCircuitBreaker().transitionToHalfOpenForTest();
        // transitionToHalfOpenForTest fires the breaker's observer → manager callback
        assertThat(manager.getCandidates()).containsExactly(n1, n2);
    }

    @Test
    void isDegraded_trueWhenAnyDegraded() {
        ServerNode n1 = node("http://n1:8080");
        ServerNode n2 = node("http://n2:8080");
        n1.markHealth(NodeHealthStatus.UP);
        n2.markHealth(NodeHealthStatus.DEGRADED);

        DefaultNodeManager manager = new DefaultNodeManager(List.of(n1, n2));
        assertThat(manager.isDegraded()).isTrue();
    }

    @Test
    void isDegraded_falseWhenAllUp() {
        ServerNode n1 = node("http://n1:8080");
        n1.markHealth(NodeHealthStatus.UP);

        DefaultNodeManager manager = new DefaultNodeManager(List.of(n1));
        assertThat(manager.isDegraded()).isFalse();
    }

    @Test
    void breakerCallbackPropagatesFromNode() {
        ServerNode n1 = node("http://n1:8080");
        n1.markHealth(NodeHealthStatus.UP);
        DefaultNodeManager manager = new DefaultNodeManager(List.of(n1));

        AtomicReference<CircuitBreakerState> observed = new AtomicReference<>();
        n1.setBreakerObserver((node, old, newVal) -> observed.set(newVal));

        n1.getCircuitBreaker().recordFailure(0L);  // OPEN
        assertThat(observed.get()).isEqualTo(CircuitBreakerState.OPEN);
    }
}
