package com.lezai.threadpool.client.router;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingStrategyTest {

    private ServerNode node(String url) {
        return new ServerNode(url, 1);
    }

    @Test
    void roundRobin_rotatesThroughNodes() {
        RoundRobinStrategy s = new RoundRobinStrategy();
        List<ServerNode> nodes = List.of(node("http://n1:8080"), node("http://n2:8080"));
        assertThat(s.select(nodes).getBaseUrl()).isEqualTo("http://n1:8080");
        assertThat(s.select(nodes).getBaseUrl()).isEqualTo("http://n2:8080");
        assertThat(s.select(nodes).getBaseUrl()).isEqualTo("http://n1:8080");
    }

    @Test
    void weightedRoundRobin_repeatsByWeight() {
        WeightedRoundRobinStrategy s = new WeightedRoundRobinStrategy();
        List<ServerNode> nodes = List.of(node("http://n1:8080"), new ServerNode("http://n2:8080", 2));
        assertThat(s.select(nodes).getBaseUrl()).isEqualTo("http://n1:8080");
        assertThat(s.select(nodes).getBaseUrl()).isEqualTo("http://n2:8080");
        assertThat(s.select(nodes).getBaseUrl()).isEqualTo("http://n2:8080");
    }

    @Test
    void random_alwaysReturnsFromCandidates() {
        RandomStrategy s = new RandomStrategy();
        List<ServerNode> nodes = List.of(node("http://n1:8080"), node("http://n2:8080"));
        for (int i = 0; i < 50; i++) {
            assertThat(s.select(nodes)).isIn(nodes);
        }
    }

    @Test
    void failover_sticksToPrimaryUntilDownThenNeverFlapsBack() {
        // FailoverStrategy is stateful: it remembers the active primary by baseUrl.
        // As long as the active primary stays in candidates, it won't switch.
        // If the active primary disappears, it promotes the first available —
        // and never flaps back to the old primary just because it recovered.
        FailoverStrategy s = new FailoverStrategy();
        ServerNode n1 = node("http://n1:8080");
        ServerNode n2 = node("http://n2:8080");
        assertThat(s.select(List.of(n1, n2))).isSameAs(n1);   // primary is n1
        assertThat(s.select(List.of(n2))).isSameAs(n2);      // n1 down → promote n2
        assertThat(s.select(List.of(n1, n2))).isSameAs(n2);  // n1 recovered → still n2 (no flap-back)
    }
}
